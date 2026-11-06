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
}

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
    }
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
