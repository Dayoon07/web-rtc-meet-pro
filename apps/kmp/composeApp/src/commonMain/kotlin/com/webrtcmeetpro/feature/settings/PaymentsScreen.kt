package com.webrtcmeetpro.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.BillingRepository
import com.webrtcmeetpro.core.designsystem.component.Badge
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.model.Payment
import com.webrtcmeetpro.core.model.RefundQuote
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.platform.formatDateTime
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.resources.payments_empty
import com.webrtcmeetpro.resources.payments_title
import com.webrtcmeetpro.resources.refund_agree
import com.webrtcmeetpro.resources.refund_amount
import com.webrtcmeetpro.resources.refund_amount_value
import com.webrtcmeetpro.resources.payment_status_CANCELED
import com.webrtcmeetpro.resources.payment_status_DONE
import com.webrtcmeetpro.resources.payment_status_FAILED
import com.webrtcmeetpro.resources.payment_status_READY
import com.webrtcmeetpro.resources.refund_cancels_plan
import com.webrtcmeetpro.resources.refund_done
import com.webrtcmeetpro.resources.refund_open
import com.webrtcmeetpro.resources.refund_paid
import com.webrtcmeetpro.resources.refund_reason
import com.webrtcmeetpro.resources.refund_refunded
import com.webrtcmeetpro.resources.refund_rule_full
import com.webrtcmeetpro.resources.refund_rule_prorated
import com.webrtcmeetpro.resources.refund_submit
import com.webrtcmeetpro.resources.refund_title
import com.webrtcmeetpro.ui.DetailScaffold
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.LocalSnackbar
import com.webrtcmeetpro.ui.ScreenList
import com.webrtcmeetpro.ui.errorMessage
import com.webrtcmeetpro.ui.loadingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class PaymentsUiState(
    /** null = 불러오는 중 */
    val payments: List<Payment>? = null,
    val loading: Boolean = false,
    /** 환불 창: 고른 결제와 서버가 계산한 환불 금액 (null = 계산 중) */
    val refunding: Payment? = null,
    val quote: RefundQuote? = null,
    val quoteError: String? = null,
    val submitting: Boolean = false,
    /** 환불을 마친 금액 (스낵바 한 번) */
    val refundedAmount: Long? = null,
    val errorCode: String? = null,
)

/** 내 결제 내역과 환불 (약관 제8조: 7일 안 전액, 그 뒤에는 쓴 날 빼고. 환불하면 그 플랜은 바로 끝난다) */
class PaymentsViewModel(private val billing: BillingRepository) : ViewModel() {
    private val _state = MutableStateFlow(PaymentsUiState())
    val state: StateFlow<PaymentsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { billing.payments() }
                .onSuccess { list -> _state.update { it.copy(payments = list, loading = false) } }
                .onFailure { e -> _state.update { it.copy(payments = it.payments ?: emptyList(), loading = false, errorCode = e.errorCode) } }
        }
    }

    fun openRefund(payment: Payment) {
        _state.update { it.copy(refunding = payment, quote = null, quoteError = null) }
        viewModelScope.launch {
            runCatching { billing.refundQuote(payment.orderId) }
                .onSuccess { q -> _state.update { it.copy(quote = q) } }
                .onFailure { e -> _state.update { it.copy(quoteError = e.errorCode) } }
        }
    }

    fun closeRefund() = _state.update { it.copy(refunding = null, quote = null, quoteError = null) }

    fun refund(reason: String) {
        val payment = _state.value.refunding ?: return
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            runCatching { billing.refund(payment.orderId, reason) }
                .onSuccess { done ->
                    _state.update { it.copy(submitting = false, refunding = null, quote = null, refundedAmount = done.refundAmount) }
                    refresh()
                }
                .onFailure { e -> _state.update { it.copy(submitting = false, errorCode = e.errorCode) } }
        }
    }

    fun refundedShown() = _state.update { it.copy(refundedAmount = null) }
    fun errorShown() = _state.update { it.copy(errorCode = null) }
}

/** 1234567 → "1,234,567원" 대신 숫자만 (원 표시는 문자열 리소스가 붙인다) */
internal fun formatAmount(n: Long): String {
    val digits = kotlin.math.abs(n).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$grouped" else grouped
}

@Composable
fun PaymentsScreen(onBack: () -> Unit, viewModel: PaymentsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.refundedAmount) {
        state.refundedAmount?.let {
            viewModel.refundedShown()
            snackbar.showSnackbar(getString(Res.string.refund_done, formatAmount(it)))
        }
    }
    val payments = state.payments
    DetailScaffold(title = stringResource(Res.string.payments_title), onBack = onBack) {
        ScreenList(refreshing = state.loading && payments != null, onRefresh = viewModel::refresh) {
            when {
                payments == null -> loadingItem()
                payments.isEmpty() -> item {
                    LimeetCard(Modifier.fillMaxWidth()) { EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, stringResource(Res.string.payments_empty)) }
                }
                else -> items(payments, key = { it.orderId }) { p -> PaymentRow(p, onRefund = { viewModel.openRefund(p) }) }
            }
        }
    }
    state.refunding?.let { RefundDialog(state, onRefund = viewModel::refund, onDismiss = viewModel::closeRefund) }
}

/** 결제 상태 이름 (READY·ABORTED 는 "대기" 로 묶는다) */
private fun paymentStatusLabel(status: String) = when (status) {
    "DONE" -> Res.string.payment_status_DONE
    "CANCELED" -> Res.string.payment_status_CANCELED
    "FAILED" -> Res.string.payment_status_FAILED
    else -> Res.string.payment_status_READY
}

@Composable
private fun PaymentRow(p: Payment, onRefund: () -> Unit) {
    LimeetCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.orderName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(p.teamName, formatDateTime(p.approvedAt ?: p.createdAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.refund_amount_value, formatAmount(p.amount)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Badge(stringResource(paymentStatusLabel(p.status)))
                }
                if (p.refundedAmount > 0) {
                    Text(stringResource(Res.string.refund_refunded, formatAmount(p.refundedAmount)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (p.refundable) OutlinedButton(onClick = onRefund) { Text(stringResource(Res.string.refund_open)) }
        }
    }
}

/** 서버가 계산한 환불 금액을 보여 주고, "플랜이 바로 끝나요" 에 동의하면 환불 */
@Composable
private fun RefundDialog(state: PaymentsUiState, onRefund: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    val q = state.quote
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.refund_title)) },
        text = {
            when {
                state.quoteError != null -> Text(errorMessage(state.quoteError))
                q == null -> CircularProgressIndicator()
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(q.orderName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.refund_paid, formatAmount(q.paidAmount)), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(Res.string.refund_amount, formatAmount(q.refundAmount)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (q.refundable) {
                        Text(
                            if (q.rule == "FULL_7_DAYS") stringResource(Res.string.refund_rule_full)
                            else stringResource(Res.string.refund_rule_prorated, q.usedDays, q.periodDays),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (q.cancelsPlan) {
                            Text(stringResource(Res.string.refund_cancels_plan, q.planName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it.take(200) },
                            label = { Text(stringResource(Res.string.refund_reason)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = agree, onCheckedChange = { agree = it })
                            Text(stringResource(Res.string.refund_agree), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(errorMessage(q.blockedBy ?: "REFUND_NOT_ALLOWED"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            if (q?.refundable == true) {
                TextButton(onClick = { onRefund(reason) }, enabled = agree && !state.submitting) {
                    Text(stringResource(Res.string.refund_submit, formatAmount(q.refundAmount)), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
