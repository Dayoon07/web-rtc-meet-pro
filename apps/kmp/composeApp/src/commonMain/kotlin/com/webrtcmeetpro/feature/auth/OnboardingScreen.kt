package com.webrtcmeetpro.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.AuthRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.component.MqCard
import com.webrtcmeetpro.core.designsystem.component.PrimaryButton
import com.webrtcmeetpro.core.model.Gender
import com.webrtcmeetpro.core.model.Me
import com.webrtcmeetpro.core.model.UpdateProfileRequest
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.feature.settings.ProfileForm
import com.webrtcmeetpro.platform.countryName
import com.webrtcmeetpro.platform.currentYear
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.onboarding_submit
import com.webrtcmeetpro.resources.onboarding_subtitle
import com.webrtcmeetpro.resources.onboarding_title
import com.webrtcmeetpro.resources.settings_logout
import com.webrtcmeetpro.resources.signup_birth_year
import com.webrtcmeetpro.resources.signup_country
import com.webrtcmeetpro.resources.signup_gender
import com.webrtcmeetpro.resources.signup_gender_FEMALE
import com.webrtcmeetpro.resources.signup_gender_MALE
import com.webrtcmeetpro.resources.signup_gender_OTHER
import com.webrtcmeetpro.resources.signup_marketing
import com.webrtcmeetpro.resources.signup_nickname
import com.webrtcmeetpro.resources.signup_phone
import com.webrtcmeetpro.resources.signup_privacy
import com.webrtcmeetpro.resources.signup_real_name
import com.webrtcmeetpro.resources.signup_terms
import com.webrtcmeetpro.ui.BrandLogo
import com.webrtcmeetpro.ui.errorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class OnboardingUiState(
    val form: ProfileForm = ProfileForm(),
    val terms: Boolean = false,
    val privacy: Boolean = false,
    val saving: Boolean = false,
    val errorCode: String? = null,
    val prefilled: Boolean = false,
) {
    val canSave: Boolean
        get() = form.nickname.isNotBlank() && form.realName.isNotBlank() && toE164(form.country, form.phone).isNotEmpty() &&
            form.birthYear != null && terms && privacy && !saving
}

/** 구글로 처음 가입한 사람은 실명·전화번호·약관 동의가 비어 있어서 서비스를 쓰기 전에 받는다 (웹 온보딩과 같다) */
class OnboardingViewModel(private val users: UserRepository, private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** 구글에서 받은 이름을 미리 채운다 */
    fun prefill(me: Me, languageTag: String) {
        if (_state.value.prefilled) return
        _state.update {
            it.copy(
                prefilled = true,
                form = ProfileForm(
                    nickname = me.nickname,
                    realName = me.realName.orEmpty(),
                    country = me.countryCode?.takeIf { c -> c in DIAL_CODES } ?: guessCountry(languageTag),
                    marketing = me.marketingAgreed,
                ),
            )
        }
    }

    fun edit(block: OnboardingUiState.() -> OnboardingUiState) = _state.update { it.block().copy(errorCode = null) }

    /** 성공하면 내 정보의 profileCompleted 가 true 가 되어 메인 화면으로 넘어간다 */
    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val f = s.form
            runCatching {
                users.updateProfile(
                    UpdateProfileRequest(
                        nickname = f.nickname.trim(),
                        realName = f.realName.trim(),
                        phoneNumber = toE164(f.country, f.phone),
                        countryCode = f.country,
                        birthYear = f.birthYear!!,
                        gender = f.gender,
                        marketingAgreed = f.marketing,
                        termsAgreed = s.terms,
                        privacyAgreed = s.privacy,
                    ),
                )
            }.onFailure { e -> _state.update { it.copy(saving = false, errorCode = e.errorCode) } }
        }
    }

    fun logout() = viewModelScope.launch { auth.logout() }
}

@Composable
fun OnboardingScreen(me: Me, languageTag: String, viewModel: OnboardingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(me.id) { viewModel.prefill(me, languageTag) }
    val form = state.form

    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BrandLogo()
            MqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.onboarding_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(Res.string.onboarding_subtitle, me.email),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FormField(form.nickname, Res.string.signup_nickname) { v -> viewModel.edit { copy(form = form.copy(nickname = v.take(50))) } }
                    FormField(form.realName, Res.string.signup_real_name) { v -> viewModel.edit { copy(form = form.copy(realName = v.take(50))) } }
                    FormPicker(
                        label = stringResource(Res.string.signup_country),
                        value = "${countryName(form.country)} (+${DIAL_CODES[form.country]})",
                        options = DIAL_CODES.keys.map { code -> code to "${countryName(code)} (+${DIAL_CODES[code]})" },
                        onSelect = { code -> viewModel.edit { copy(form = form.copy(country = code)) } },
                    )
                    FormField(form.phone, Res.string.signup_phone, KeyboardType.Phone) { v -> viewModel.edit { copy(form = form.copy(phone = v.take(20))) } }
                    val thisYear = currentYear()
                    FormPicker(
                        label = stringResource(Res.string.signup_birth_year),
                        value = form.birthYear?.toString().orEmpty(),
                        options = (thisYear - MIN_AGE - 1 downTo 1900).map { it.toString() to it.toString() },
                        onSelect = { y -> viewModel.edit { copy(form = form.copy(birthYear = y.toInt())) } },
                    )
                    Text(stringResource(Res.string.signup_gender), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Gender.MALE to Res.string.signup_gender_MALE, Gender.FEMALE to Res.string.signup_gender_FEMALE, Gender.OTHER to Res.string.signup_gender_OTHER)
                            .forEach { (g, label) ->
                                FilterChip(
                                    selected = form.gender == g,
                                    onClick = { viewModel.edit { copy(form = form.copy(gender = if (form.gender == g) null else g)) } },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                    }
                    Agree(state.terms, Res.string.signup_terms, doc = "/terms") { viewModel.edit { copy(terms = it) } }
                    Agree(state.privacy, Res.string.signup_privacy, doc = "/privacy") { viewModel.edit { copy(privacy = it) } }
                    Agree(form.marketing, Res.string.signup_marketing) { viewModel.edit { copy(form = form.copy(marketing = it)) } }

                    state.errorCode?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    PrimaryButton(
                        stringResource(Res.string.onboarding_submit),
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        loading = state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            TextButton(onClick = { viewModel.logout() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(Res.string.settings_logout))
            }
        }
    }
}
