package com.webrtcmeetpro.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.designsystem.component.MqCard
import com.webrtcmeetpro.core.designsystem.component.PrimaryButton
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.core.model.Gender
import com.webrtcmeetpro.core.model.Me
import com.webrtcmeetpro.core.model.UpdateProfileRequest
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.feature.auth.DIAL_CODES
import com.webrtcmeetpro.feature.auth.FormField
import com.webrtcmeetpro.feature.auth.FormPicker
import com.webrtcmeetpro.feature.auth.MIN_AGE
import com.webrtcmeetpro.feature.auth.toE164
import com.webrtcmeetpro.platform.PickedFile
import com.webrtcmeetpro.platform.countryName
import com.webrtcmeetpro.platform.currentYear
import com.webrtcmeetpro.platform.rememberImagePicker
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.profile_edit
import com.webrtcmeetpro.resources.profile_info_desc
import com.webrtcmeetpro.resources.profile_info_title
import com.webrtcmeetpro.resources.profile_marketing
import com.webrtcmeetpro.resources.profile_photo_change
import com.webrtcmeetpro.resources.profile_photo_changed
import com.webrtcmeetpro.resources.profile_photo_hint
import com.webrtcmeetpro.resources.profile_photo_remove
import com.webrtcmeetpro.resources.profile_photo_title
import com.webrtcmeetpro.resources.profile_save
import com.webrtcmeetpro.resources.profile_saved
import com.webrtcmeetpro.resources.signup_birth_year
import com.webrtcmeetpro.resources.signup_country
import com.webrtcmeetpro.resources.signup_gender
import com.webrtcmeetpro.resources.signup_gender_FEMALE
import com.webrtcmeetpro.resources.signup_gender_MALE
import com.webrtcmeetpro.resources.signup_gender_OTHER
import com.webrtcmeetpro.resources.signup_nickname
import com.webrtcmeetpro.resources.signup_phone
import com.webrtcmeetpro.resources.signup_real_name
import com.webrtcmeetpro.ui.DetailScaffold
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.LocalSnackbar
import com.webrtcmeetpro.ui.ScreenList
import com.webrtcmeetpro.ui.loadingItem
import com.webrtcmeetpro.ui.resolveImageUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class ProfileForm(
    val nickname: String = "",
    val realName: String = "",
    val country: String = "KR",
    val phone: String = "",
    val birthYear: Int? = null,
    val gender: Gender? = null,
    val marketing: Boolean = false,
)

data class ProfileEditState(
    val me: Me? = null,
    val form: ProfileForm = ProfileForm(),
    val saving: Boolean = false,
    val uploading: Boolean = false,
    /** 저장·사진 변경이 끝나면 한 번 스낵바로 알린다 */
    val done: StringResource? = null,
    val errorCode: String? = null,
) {
    val canSave: Boolean
        get() = form.nickname.isNotBlank() && form.realName.isNotBlank() && toE164(form.country, form.phone).isNotEmpty() &&
            form.birthYear != null && !saving
}

class ProfileEditViewModel(private val users: UserRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileEditState())
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { users.refresh() }
                .onSuccess { me -> _state.update { it.copy(me = me, form = formOf(me)) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun edit(block: ProfileForm.() -> ProfileForm) = _state.update { it.copy(form = it.form.block()) }

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
                    ),
                )
            }
                .onSuccess { me -> _state.update { it.copy(me = me, saving = false, done = Res.string.profile_saved) } }
                .onFailure { e -> _state.update { it.copy(saving = false, errorCode = e.errorCode) } }
        }
    }

    fun uploadPhoto(file: PickedFile) {
        val bytes = file.bytes
        // 형식은 서버가 내용으로 다시 확인하지만, 너무 큰 파일은 보내기 전에 막는다
        if (bytes == null) {
            _state.update { it.copy(errorCode = "INVALID_IMAGE") }
            return
        }
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            runCatching { users.uploadProfileImage(bytes, file.name, file.mimeType ?: "application/octet-stream") }
                .onSuccess { me -> _state.update { it.copy(me = me, uploading = false, done = Res.string.profile_photo_changed) } }
                .onFailure { e -> _state.update { it.copy(uploading = false, errorCode = e.errorCode) } }
        }
    }

    fun removePhoto() {
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            runCatching { users.removeProfileImage() }
                .onSuccess { me -> _state.update { it.copy(me = me, uploading = false, done = Res.string.profile_photo_changed) } }
                .onFailure { e -> _state.update { it.copy(uploading = false, errorCode = e.errorCode) } }
        }
    }

    fun doneShown() = _state.update { it.copy(done = null) }
    fun errorShown() = _state.update { it.copy(errorCode = null) }

    private fun formOf(me: Me): ProfileForm {
        val country = me.countryCode?.takeIf { it in DIAL_CODES } ?: "KR"
        val dial = "+" + DIAL_CODES[country]
        return ProfileForm(
            nickname = me.nickname,
            realName = me.realName.orEmpty(),
            country = country,
            phone = me.phoneNumber?.removePrefix(dial).orEmpty(),
            birthYear = me.birthYear,
            gender = me.gender,
            marketing = me.marketingAgreed,
        )
    }
}

@Composable
fun ProfileEditScreen(onBack: () -> Unit, viewModel: ProfileEditViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.done) {
        state.done?.let {
            viewModel.doneShown()
            snackbar.showSnackbar(getString(it))
        }
    }
    val pickPhoto = rememberImagePicker(stringResource(Res.string.profile_photo_change), viewModel::uploadPhoto)
    val me = state.me
    val form = state.form

    DetailScaffold(title = stringResource(Res.string.profile_edit), onBack = onBack) {
        ScreenList(refreshing = false, onRefresh = {}) {
            if (me == null) {
                loadingItem()
                return@ScreenList
            }
            item {
                Section(stringResource(Res.string.profile_photo_title), stringResource(Res.string.profile_photo_hint)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Avatar(me.nickname, resolveImageUrl(me.profileImageUrl), size = 64.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SecondaryButton(stringResource(Res.string.profile_photo_change), onClick = pickPhoto, enabled = !state.uploading)
                            if (me.profileImageUrl != null) {
                                TextButton(onClick = viewModel::removePhoto, enabled = !state.uploading) {
                                    Text(stringResource(Res.string.profile_photo_remove), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Section(stringResource(Res.string.profile_info_title), stringResource(Res.string.profile_info_desc)) {
                    FormField(form.nickname, Res.string.signup_nickname) { v -> viewModel.edit { copy(nickname = v.take(50)) } }
                    FormField(form.realName, Res.string.signup_real_name) { v -> viewModel.edit { copy(realName = v.take(50)) } }
                    FormPicker(
                        label = stringResource(Res.string.signup_country),
                        value = "${countryName(form.country)} (+${DIAL_CODES[form.country]})",
                        options = DIAL_CODES.keys.map { code -> code to "${countryName(code)} (+${DIAL_CODES[code]})" },
                        onSelect = { code -> viewModel.edit { copy(country = code) } },
                    )
                    FormField(form.phone, Res.string.signup_phone, KeyboardType.Phone) { v -> viewModel.edit { copy(phone = v.take(20)) } }
                    val thisYear = currentYear()
                    FormPicker(
                        label = stringResource(Res.string.signup_birth_year),
                        value = form.birthYear?.toString().orEmpty(),
                        options = (thisYear - MIN_AGE - 1 downTo 1900).map { it.toString() to it.toString() },
                        onSelect = { y -> viewModel.edit { copy(birthYear = y.toInt()) } },
                    )
                    Text(stringResource(Res.string.signup_gender), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Gender.MALE to Res.string.signup_gender_MALE, Gender.FEMALE to Res.string.signup_gender_FEMALE, Gender.OTHER to Res.string.signup_gender_OTHER)
                            .forEach { (g, label) ->
                                FilterChip(
                                    selected = form.gender == g,
                                    onClick = { viewModel.edit { copy(gender = if (gender == g) null else g) } },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.edit { copy(marketing = !marketing) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = form.marketing, onCheckedChange = { c -> viewModel.edit { copy(marketing = c) } })
                        Text(stringResource(Res.string.profile_marketing), style = MaterialTheme.typography.bodyMedium)
                    }
                    PrimaryButton(
                        stringResource(Res.string.profile_save),
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        loading = state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, desc: String, content: @Composable () -> Unit) {
    MqCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
