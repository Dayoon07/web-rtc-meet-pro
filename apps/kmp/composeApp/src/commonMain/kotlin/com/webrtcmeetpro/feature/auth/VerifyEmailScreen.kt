package com.webrtcmeetpro.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.AuthRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.component.PrimaryButton
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.core.model.Me
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.verify_body
import com.webrtcmeetpro.resources.verify_done
import com.webrtcmeetpro.resources.verify_logout
import com.webrtcmeetpro.resources.verify_not_yet
import com.webrtcmeetpro.resources.verify_resend
import com.webrtcmeetpro.resources.verify_resent
import com.webrtcmeetpro.resources.verify_title
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.errorMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class VerifyEmailUiState(
    val loadError: String? = null,
    val checking: Boolean = false,
    val resending: Boolean = false,
    /** 다시 보내기까지 남은 초 */
    val cooldown: Int = 0,
    /** 한 번 보여 줄 안내 (스낵바 대신 화면 아래 문구) */
    val notice: Notice? = null,
    val errorCode: String? = null,
)

enum class Notice { NOT_YET, RESENT }

/**
 * 이메일로 가입하고 아직 인증하지 않은 사용자. 메일의 링크(웹)를 누르고 앱으로 돌아오면 다시 확인한다.
 * 구글 가입자와 기존 회원은 처음부터 인증된 상태라 이 화면을 보지 않는다.
 */
class VerifyEmailViewModel(private val users: UserRepository, private val auth: AuthRepository) : ViewModel() {
    val me: StateFlow<Me?> = users.me
    private val _state = MutableStateFlow(VerifyEmailUiState())
    val state: StateFlow<VerifyEmailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            runCatching { users.refresh() }.onFailure { e -> _state.update { it.copy(loadError = e.errorCode) } }
        }
    }

    /** "인증했어요" 또는 앱으로 돌아왔을 때 */
    fun check(showNotYet: Boolean) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            runCatching { users.refresh() }
                .onSuccess { me -> _state.update { it.copy(checking = false, notice = if (showNotYet && !me.emailVerified) Notice.NOT_YET else it.notice) } }
                .onFailure { e -> _state.update { it.copy(checking = false, errorCode = e.errorCode) } }
        }
    }

    fun resend() {
        if (_state.value.resending || _state.value.cooldown > 0) return
        _state.update { it.copy(resending = true, errorCode = null) }
        viewModelScope.launch {
            runCatching { auth.resendVerification() }
                .onSuccess { _state.update { it.copy(resending = false, notice = Notice.RESENT) }; startCooldown() }
                .onFailure { e ->
                    _state.update { it.copy(resending = false, errorCode = e.errorCode) }
                    if (e.errorCode == "RESEND_TOO_SOON") startCooldown()
                }
        }
    }

    private fun startCooldown() {
        viewModelScope.launch {
            for (left in 60 downTo 0) {
                _state.update { it.copy(cooldown = left) }
                if (left > 0) delay(1000)
            }
        }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    fun logout() = viewModelScope.launch { auth.logout() }
}

/** 로그인 뒤 메인 화면 앞에 두는 문: 이메일 인증 전이면 안내 화면, 아니면 [content] */
@Composable
fun EmailVerifiedGate(content: @Composable () -> Unit) {
    val viewModel = koinViewModel<VerifyEmailViewModel>()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = me
    when {
        current == null && state.loadError != null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ErrorState(state.loadError!!, onRetry = viewModel::load) }
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        !current.emailVerified -> VerifyEmailScreen(current, state, viewModel)
        else -> content()
    }
}

@Composable
private fun VerifyEmailScreen(me: Me, state: VerifyEmailUiState, viewModel: VerifyEmailViewModel) {
    // 메일 앱·브라우저에서 링크를 누르고 돌아오면 바로 확인
    LifecycleResumeEffect(Unit) {
        viewModel.check(showNotYet = false)
        onPauseOrDispose { }
    }
    // 안내 문구는 4초 뒤에 지운다 (메인 화면 밖이라 스낵바가 없다)
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(4000)
            viewModel.noticeShown()
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(stringResource(Res.string.verify_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.verify_body, me.email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            PrimaryButton(
                text = stringResource(Res.string.verify_done),
                onClick = { viewModel.check(showNotYet = true) },
                loading = state.checking,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = if (state.cooldown > 0) "${stringResource(Res.string.verify_resend)} (${state.cooldown})" else stringResource(Res.string.verify_resend),
                onClick = viewModel::resend,
                enabled = state.cooldown == 0 && !state.resending,
                modifier = Modifier.fillMaxWidth(),
            )
            state.notice?.let {
                Text(
                    stringResource(if (it == Notice.NOT_YET) Res.string.verify_not_yet else Res.string.verify_resent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            state.errorCode?.takeIf { it != "RESEND_TOO_SOON" }?.let {
                Text(errorMessage(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { viewModel.logout() }) { Text(stringResource(Res.string.verify_logout)) }
        }
    }
}
