package com.webrtcmeetpro.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.model.Subscription
import com.webrtcmeetpro.core.network.ApiConfig
import com.webrtcmeetpro.core.network.LimeetApi
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.plan_expired_body
import com.webrtcmeetpro.resources.plan_expired_data_kept
import com.webrtcmeetpro.resources.plan_expired_later
import com.webrtcmeetpro.resources.plan_expired_now_free
import com.webrtcmeetpro.resources.plan_expired_now_other
import com.webrtcmeetpro.resources.plan_expired_renew
import com.webrtcmeetpro.resources.plan_expired_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** 앱으로 돌아올 때마다 구독을 확인해서, 확인하지 않은 만료가 있으면 창을 띄운다 */
class PlanExpiryViewModel(private val api: LimeetApi) : ViewModel() {
    private val _subscription = MutableStateFlow<Subscription?>(null)
    val subscription: StateFlow<Subscription?> = _subscription.asStateFlow()

    fun refresh() {
        viewModelScope.launch { runCatching { api.mySubscription() }.onSuccess { _subscription.value = it } }
    }

    fun acknowledge() {
        val notice = _subscription.value?.expiryNotice ?: return
        _subscription.value = _subscription.value?.copy(expiryNotice = null)
        viewModelScope.launch { runCatching { api.acknowledgeExpiry(notice.subscriptionId) } }
    }
}

/**
 * "플랜이 만료됐어요" 창. 결제·구독 관리는 웹 요금제 화면에서 한다 (앱 안 결제는 스토어 정책 검토 후).
 * 만료된 플랜의 기능(녹화·늘어난 인원 등)은 서버가 이미 막고 있다.
 */
@Composable
fun PlanExpiryDialog(viewModel: PlanExpiryViewModel = koinViewModel()) {
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val subscription by viewModel.subscription.collectAsStateWithLifecycle()
    val notice = subscription?.expiryNotice ?: return
    val config = koinInject<ApiConfig>()
    val uriHandler = LocalUriHandler.current
    val onFree = subscription?.plan?.monthlyPrice == 0L
    val date = notice.expiredAt?.take(10) ?: ""

    AlertDialog(
        onDismissRequest = viewModel::acknowledge,
        icon = { Icon(Icons.Outlined.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Res.string.plan_expired_title, notice.plan.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.plan_expired_body, notice.plan.name, date))
                Text(
                    if (onFree) stringResource(Res.string.plan_expired_now_free)
                    else stringResource(Res.string.plan_expired_now_other, subscription?.plan?.name ?: ""),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(Res.string.plan_expired_data_kept), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.acknowledge()
                uriHandler.openUri(config.webUrl.trimEnd('/') + "/app/billing")
            }) { Text(stringResource(Res.string.plan_expired_renew)) }
        },
        dismissButton = { TextButton(onClick = viewModel::acknowledge) { Text(stringResource(Res.string.plan_expired_later)) } },
    )
}
