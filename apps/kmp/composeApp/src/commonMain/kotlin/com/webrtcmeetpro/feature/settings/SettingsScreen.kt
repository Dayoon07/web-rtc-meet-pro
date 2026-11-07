package com.webrtcmeetpro.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.resources.settings_withdraw
import com.webrtcmeetpro.resources.password_change
import com.webrtcmeetpro.resources.password_changed
import com.webrtcmeetpro.resources.password_confirm
import com.webrtcmeetpro.resources.password_create
import com.webrtcmeetpro.resources.password_created
import com.webrtcmeetpro.resources.password_current
import com.webrtcmeetpro.resources.password_hint
import com.webrtcmeetpro.resources.password_mismatch
import com.webrtcmeetpro.resources.password_new
import com.webrtcmeetpro.resources.password_too_short
import com.webrtcmeetpro.ui.LocalSnackbar
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.getString
import com.webrtcmeetpro.resources.settings_withdraw_body
import com.webrtcmeetpro.resources.settings_withdraw_confirm
import com.webrtcmeetpro.resources.settings_withdraw_password
import com.webrtcmeetpro.resources.settings_withdraw_title
import com.webrtcmeetpro.resources.settings_withdraw_word
import com.webrtcmeetpro.ui.errorMessage
import kotlinx.coroutines.flow.update
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.AppLanguage
import com.webrtcmeetpro.core.data.AppSettings
import com.webrtcmeetpro.core.data.AuthRepository
import com.webrtcmeetpro.core.data.SettingsStore
import com.webrtcmeetpro.core.data.ThemeMode
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.designsystem.component.Badge
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.core.model.Me
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.lang_system
import com.webrtcmeetpro.resources.settings_language
import com.webrtcmeetpro.resources.settings_billing
import androidx.compose.ui.platform.LocalUriHandler
import com.webrtcmeetpro.core.network.ApiConfig
import org.koin.compose.koinInject
import com.webrtcmeetpro.resources.settings_logout
import com.webrtcmeetpro.resources.settings_plan
import com.webrtcmeetpro.resources.settings_theme
import com.webrtcmeetpro.resources.settings_title
import com.webrtcmeetpro.resources.theme_dark
import com.webrtcmeetpro.resources.theme_light
import com.webrtcmeetpro.resources.theme_system
import com.webrtcmeetpro.ui.ScreenList
import com.webrtcmeetpro.ui.ScreenTitle
import com.webrtcmeetpro.ui.resolveImageUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class SettingsViewModel(
    private val users: UserRepository,
    private val auth: AuthRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _me = MutableStateFlow<Me?>(null)
    val me: StateFlow<Me?> = _me.asStateFlow()
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init { loadMe() }

    fun loadMe() {
        viewModelScope.launch { runCatching { users.me() }.onSuccess { _me.value = it } }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(mode) }
    fun setLanguage(language: AppLanguage?) = viewModelScope.launch { settingsStore.setLanguage(language) }

    /** 토큰이 지워지면 App 이 알아서 로그인 화면으로 돌아간다 */
    fun logout() = viewModelScope.launch { auth.logout() }

    private val _withdraw = MutableStateFlow(WithdrawState())
    val withdraw: StateFlow<WithdrawState> = _withdraw.asStateFlow()

    /** 회원 탈퇴. 성공하면 토큰이 지워져서 로그인 화면으로 돌아간다 */
    fun withdraw(password: String?) {
        if (_withdraw.value.running) return
        _withdraw.value = WithdrawState(running = true)
        viewModelScope.launch {
            runCatching { auth.withdraw(password) }
                .onSuccess { _withdraw.value = WithdrawState() }
                .onFailure { e -> _withdraw.update { it.copy(running = false, errorCode = e.errorCode) } }
        }
    }

    fun clearWithdrawError() = _withdraw.update { it.copy(errorCode = null) }

    private val _password = MutableStateFlow(PasswordState())
    val password: StateFlow<PasswordState> = _password.asStateFlow()

    /** 비밀번호 바꾸기·만들기. 다른 기기는 로그아웃되고 이 기기는 새 토큰으로 계속 로그인 */
    fun changePassword(current: String?, new: String) {
        if (_password.value.running) return
        val creating = current == null
        _password.value = PasswordState(running = true)
        viewModelScope.launch {
            runCatching { auth.changePassword(current, new) }
                .onSuccess { me -> _password.value = PasswordState(done = true, created = creating); loadMe() }
                .onFailure { e -> _password.update { it.copy(running = false, errorCode = e.errorCode) } }
        }
    }

    fun resetPassword() { _password.value = PasswordState() }
}

data class WithdrawState(val running: Boolean = false, val errorCode: String? = null)

/** 비밀번호 바꾸기 창 상태. done 이면 창을 닫고 스낵바로 알린다 (만들었으면 created) */
data class PasswordState(val running: Boolean = false, val errorCode: String? = null, val done: Boolean = false, val created: Boolean = false)


/** 언어 이름은 번역하지 않고 각 언어로 표기 (웹과 동일) */
private val languageNames = linkedMapOf(
    AppLanguage.KO to "한국어",
    AppLanguage.EN to "English",
    AppLanguage.JA to "日本語",
    AppLanguage.ZH to "简体中文",
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val me by viewModel.me.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ScreenList(refreshing = false, onRefresh = viewModel::loadMe) {
        item { ScreenTitle(stringResource(Res.string.settings_title)) }

        me?.let { user -> item { ProfileCard(user) } }

        // 요금제 결제·자동결제·카드 관리는 웹에서 (앱 안 결제는 스토어 정책 검토 후)
        item {
            val uriHandler = LocalUriHandler.current
            val config = koinInject<ApiConfig>()
            SecondaryButton(
                stringResource(Res.string.settings_billing),
                onClick = { uriHandler.openUri(config.webUrl.trimEnd('/') + "/app/billing") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            SettingsSection(stringResource(Res.string.settings_theme)) {
                val modes = listOf(
                    ThemeMode.SYSTEM to Res.string.theme_system,
                    ThemeMode.LIGHT to Res.string.theme_light,
                    ThemeMode.DARK to Res.string.theme_dark,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { viewModel.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        }

        item {
            SettingsSection(stringResource(Res.string.settings_language)) {
                Column {
                    LanguageRow(stringResource(Res.string.lang_system), selected = settings.language == null) {
                        viewModel.setLanguage(null)
                    }
                    languageNames.forEach { (language, name) ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LanguageRow(name, selected = settings.language == language) { viewModel.setLanguage(language) }
                    }
                }
            }
        }

        item {
            SecondaryButton(
                stringResource(Res.string.settings_logout),
                onClick = { viewModel.logout() },
                icon = Icons.AutoMirrored.Outlined.Logout,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }

        me?.let { user ->
            item {
                var open by remember { mutableStateOf(false) }
                val passwordState by viewModel.password.collectAsStateWithLifecycle()
                val snackbar = LocalSnackbar.current
                LaunchedEffect(passwordState.done) {
                    if (passwordState.done) {
                        open = false
                        val created = passwordState.created
                        viewModel.resetPassword()
                        snackbar.showSnackbar(getString(if (created) Res.string.password_created else Res.string.password_changed))
                    }
                }
                SecondaryButton(
                    stringResource(if (user.hasPassword) Res.string.password_change else Res.string.password_create),
                    onClick = { open = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (open) {
                    PasswordDialog(
                        hasPassword = user.hasPassword,
                        state = passwordState,
                        onSubmit = viewModel::changePassword,
                        onDismiss = { open = false; viewModel.resetPassword() },
                    )
                }
            }
        }

        me?.let { user ->
            item {
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.settings_withdraw), color = MaterialTheme.colorScheme.error)
                }
                if (open) {
                    WithdrawDialog(
                        hasPassword = user.hasPassword,
                        viewModel = viewModel,
                        onDismiss = {
                            open = false
                            viewModel.clearWithdrawError()
                        },
                    )
                }
            }
        }
    }
}

/** 비밀번호 바꾸기(현재 비밀번호 확인) / 만들기(구글 전용 계정). 8자 이상, 확인 칸과 같아야 한다 */
@Composable
private fun PasswordDialog(hasPassword: Boolean, state: PasswordState, onSubmit: (String?, String) -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<org.jetbrains.compose.resources.StringResource?>(null) }
    val field = @Composable { value: String, label: org.jetbrains.compose.resources.StringResource, onChange: (String) -> Unit ->
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(72)); localError = null },
            label = { Text(stringResource(label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = { Text(stringResource(if (hasPassword) Res.string.password_change else Res.string.password_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.password_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasPassword) field(current, Res.string.password_current) { current = it }
                field(new, Res.string.password_new) { new = it }
                field(confirm, Res.string.password_confirm) { confirm = it }
                val message = localError?.let { stringResource(it) } ?: state.errorCode?.let { errorMessage(it) }
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    localError = when {
                        new.length < 8 -> Res.string.password_too_short
                        new != confirm -> Res.string.password_mismatch
                        else -> null
                    }
                    if (localError == null) onSubmit(if (hasPassword) current else null, new)
                },
                enabled = !state.running && new.isNotEmpty() && confirm.isNotEmpty() && (!hasPassword || current.isNotEmpty()),
            ) { Text(stringResource(if (hasPassword) Res.string.password_change else Res.string.password_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.running) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** 비밀번호가 있는 계정은 비밀번호로, 구글로만 가입한 계정은 확인 문구로 한 번 더 확인한다 (웹과 동일) */
@Composable
private fun WithdrawDialog(hasPassword: Boolean, viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val state by viewModel.withdraw.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val word = stringResource(Res.string.settings_withdraw_word)
    val ready = if (hasPassword) input.isNotEmpty() else input.trim() == word
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        icon = { androidx.compose.material3.Icon(Icons.Outlined.PersonRemove, contentDescription = null) },
        title = { Text(stringResource(Res.string.settings_withdraw_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.settings_withdraw_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(100); viewModel.clearWithdrawError() },
                    label = {
                        Text(
                            if (hasPassword) stringResource(Res.string.settings_withdraw_password)
                            else stringResource(Res.string.settings_withdraw_confirm, word),
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (hasPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    isError = state.errorCode != null,
                    supportingText = state.errorCode?.let { code -> { Text(errorMessage(code)) } },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.withdraw(if (hasPassword) input else null) }, enabled = ready && !state.running) {
                Text(stringResource(Res.string.settings_withdraw), color = if (ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.running) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

@Composable
private fun ProfileCard(me: Me) {
    LimeetCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(me.nickname, resolveImageUrl(me.profileImageUrl), size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(me.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(me.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Badge(stringResource(Res.string.settings_plan, me.plan.name))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    LimeetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}
