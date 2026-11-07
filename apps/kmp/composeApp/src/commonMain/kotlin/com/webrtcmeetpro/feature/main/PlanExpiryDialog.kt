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
import com.webrtcmeetpro.core.model.ExpiryNotice
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

/** 앱으로 돌아올 때마다 내가 결제한 워크스페이스 플랜의 만료를 확인해서, 확인하지 않은 것이 있으면 창을 띄운다 */
class PlanExpiryViewModel(private val api: LimeetApi) : ViewModel() {
    private val _notices = MutableStateFlow<List<ExpiryNotice>>(emptyList())
    val notices: StateFlow<List<ExpiryNotice>> = _notices.asStateFlow()

    fun refresh() {
        viewModelScope.launch { runCatching { api.expiryNotices() }.onSuccess { _notices.value = it } }
    }

    fun acknowledge() {
        val notice = _notices.value.firstOrNull() ?: return
        _notices.value = _notices.value.drop(1)
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
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val notice = notices.firstOrNull() ?: return
    val config = koinInject<ApiConfig>()
    val uriHandler = LocalUriHandler.current
    val current = notice.currentPlan
    val onFree = current == null || current.monthlyPrice == 0L
    val date = notice.expiredAt?.take(10) ?: ""

    AlertDialog(
        onDismissRequest = viewModel::acknowledge,
        icon = { Icon(Icons.Outlined.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Res.string.plan_expired_title, notice.plan.name) + (notice.teamName?.let { " · $it" } ?: "")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.plan_expired_body, notice.plan.name, date))
                Text(
                    if (onFree) stringResource(Res.string.plan_expired_now_free)
                    else stringResource(Res.string.plan_expired_now_other, current?.name ?: ""),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(Res.string.plan_expired_data_kept), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.acknowledge()
                uriHandler.openUri(config.webUrl.trimEnd('/') + "/app/billing" + (notice.teamId?.let { "?team=$it" } ?: ""))
            }) { Text(stringResource(Res.string.plan_expired_renew)) }
        },
        dismissButton = { TextButton(onClick = viewModel::acknowledge) { Text(stringResource(Res.string.plan_expired_later)) } },
    )
}
