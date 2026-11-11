package com.webrtcmeetpro.feature.auth

import com.webrtcmeetpro.resources.signup_view_doc
import com.webrtcmeetpro.core.network.ApiConfig
import org.koin.compose.koinInject
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.AuthRepository
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.component.PrimaryButton
import com.webrtcmeetpro.core.model.Gender
import com.webrtcmeetpro.core.model.SignUpRequest
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.platform.countryName
import com.webrtcmeetpro.platform.currentYear
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.common_back
import com.webrtcmeetpro.resources.login_email
import com.webrtcmeetpro.resources.login_password
import com.webrtcmeetpro.resources.signup_birth_year
import com.webrtcmeetpro.resources.signup_country
import com.webrtcmeetpro.resources.signup_gender
import com.webrtcmeetpro.resources.signup_gender_FEMALE
import com.webrtcmeetpro.resources.signup_gender_MALE
import com.webrtcmeetpro.resources.signup_gender_OTHER
import com.webrtcmeetpro.resources.signup_have_account
import com.webrtcmeetpro.resources.signup_marketing
import com.webrtcmeetpro.resources.signup_nickname
import com.webrtcmeetpro.resources.signup_password_rule
import com.webrtcmeetpro.resources.signup_phone
import com.webrtcmeetpro.resources.signup_privacy
import com.webrtcmeetpro.resources.signup_real_name
import com.webrtcmeetpro.resources.signup_submit
import com.webrtcmeetpro.resources.signup_subtitle
import com.webrtcmeetpro.resources.signup_terms
import com.webrtcmeetpro.resources.signup_title
import com.webrtcmeetpro.ui.BrandLogo
import com.webrtcmeetpro.ui.errorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class SignupUiState(
    val email: String = "",
    val password: String = "",
    val nickname: String = "",
    val realName: String = "",
    val country: String = "KR",
    val phone: String = "",
    val birthYear: Int? = null,
    val gender: Gender? = null,
    val terms: Boolean = false,
    val privacy: Boolean = false,
    val marketing: Boolean = false,
    val submitting: Boolean = false,
    val errorCode: String? = null,
) {
    val canSubmit: Boolean
        get() = email.contains('@') && password.length >= 8 && nickname.isNotBlank() && realName.isNotBlank() &&
            toE164(country, phone).isNotEmpty() && birthYear != null && terms && privacy && !submitting
}

class SignupViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SignupUiState())
    val state: StateFlow<SignupUiState> = _state.asStateFlow()

    fun update(block: SignupUiState.() -> SignupUiState) = _state.update { it.block().copy(errorCode = null) }

    /** 화면이 처음 열릴 때 앱 언어로 기본 국가를 정한다 */
    fun initCountry(languageTag: String) {
        if (_state.value.phone.isEmpty()) _state.update { it.copy(country = guessCountry(languageTag)) }
    }

    /** 성공하면 토큰이 저장되고 App 이 메인 화면으로 바꾼다 */
    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(submitting = true, errorCode = null) }
        viewModelScope.launch {
            val request = SignUpRequest(
                email = s.email.trim(),
                password = s.password,
                nickname = s.nickname.trim(),
                realName = s.realName.trim(),
                phoneNumber = toE164(s.country, s.phone),
                countryCode = s.country,
                birthYear = s.birthYear!!,
                gender = s.gender,
                termsAgreed = s.terms,
                privacyAgreed = s.privacy,
                marketingAgreed = s.marketing,
            )
            runCatching { auth.signUp(request) }
                .onFailure { e -> _state.update { it.copy(submitting = false, errorCode = e.errorCode) } }
        }
    }
}

@Composable
fun SignupScreen(onBack: () -> Unit, languageTag: String, viewModel: SignupViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(languageTag) { viewModel.initCountry(languageTag) }

    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                }
                BrandLogo()
            }
            LimeetCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.signup_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(Res.string.signup_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Field(state.email, Res.string.login_email, KeyboardType.Email) { viewModel.update { copy(email = it.take(255)) } }
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { v -> viewModel.update { copy(password = v.take(72)) } },
                        label = { Text(stringResource(Res.string.login_password)) },
                        supportingText = { Text(stringResource(Res.string.signup_password_rule)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Field(state.nickname, Res.string.signup_nickname) { viewModel.update { copy(nickname = it.take(50)) } }
                    Field(state.realName, Res.string.signup_real_name) { viewModel.update { copy(realName = it.take(50)) } }

                    Picker(
                        label = stringResource(Res.string.signup_country),
                        value = "${countryName(state.country)} (+${DIAL_CODES[state.country]})",
                        options = DIAL_CODES.keys.map { code -> code to "${countryName(code)} (+${DIAL_CODES[code]})" },
                        onSelect = { code -> viewModel.update { copy(country = code) } },
                    )
                    Field(state.phone, Res.string.signup_phone, KeyboardType.Phone) { viewModel.update { copy(phone = it.take(20)) } }

                    val thisYear = currentYear()
                    Picker(
                        label = stringResource(Res.string.signup_birth_year),
                        value = state.birthYear?.toString().orEmpty(),
                        options = (thisYear - MIN_AGE - 1 downTo 1900).map { it.toString() to it.toString() },
                        onSelect = { y -> viewModel.update { copy(birthYear = y.toInt()) } },
                    )

                    Text(stringResource(Res.string.signup_gender), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Gender.MALE to Res.string.signup_gender_MALE, Gender.FEMALE to Res.string.signup_gender_FEMALE, Gender.OTHER to Res.string.signup_gender_OTHER)
                            .forEach { (g, label) ->
                                FilterChip(
                                    selected = state.gender == g,
                                    onClick = { viewModel.update { copy(gender = if (gender == g) null else g) } },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                    }

                    Agree(state.terms, Res.string.signup_terms, doc = "/terms") { viewModel.update { copy(terms = it) } }
                    Agree(state.privacy, Res.string.signup_privacy, doc = "/privacy") { viewModel.update { copy(privacy = it) } }
                    Agree(state.marketing, Res.string.signup_marketing) { viewModel.update { copy(marketing = it) } }

                    state.errorCode?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    PrimaryButton(
                        stringResource(Res.string.signup_submit),
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                        loading = state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                stringResource(Res.string.signup_have_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onBack).padding(8.dp),
            )
        }
    }
}

@Composable
private fun Field(value: String, label: StringResource, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 읽기 전용 입력칸 + 드롭다운 (국가, 출생년도) */
@Composable
private fun Picker(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        // readOnly 입력칸은 클릭을 먹으므로 위에 투명한 클릭 영역을 덮는다
        Box(
            Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(key) })
            }
        }
    }
}

@Composable
private fun Agree(checked: Boolean, label: StringResource, doc: String? = null, onChange: (Boolean) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val webUrl = koinInject<ApiConfig>().webUrl.trimEnd('/')
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        // 이용약관·개인정보처리방침 전문 (웹 페이지)
        if (doc != null) {
            TextButton(onClick = { runCatching { uriHandler.openUri(webUrl + doc) } }) { Text(stringResource(Res.string.signup_view_doc)) }
        }
    }
}
