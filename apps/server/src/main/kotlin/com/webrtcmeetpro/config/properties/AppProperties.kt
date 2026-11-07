package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.registration.ClientRegistration

@ConfigurationProperties("app.oauth2")
data class OAuth2Properties(
    val google: Google,
    val successRedirect: String,
    val failureRedirect: String,
) {
    data class Google(val clientId: String = "", val clientSecret: String = "")

    val googleEnabled: Boolean get() = google.clientId.isNotBlank() && google.clientSecret.isNotBlank()

    /** client-id 가 없으면 null → 구글 로그인 비활성 */
    fun googleRegistration(): ClientRegistration? =
        if (!googleEnabled) null
        else CommonOAuth2Provider.GOOGLE.getBuilder("google")
            .clientId(google.clientId)
            .clientSecret(google.clientSecret)
            .build()
}

/**
 * @param uploadDir 프로필 이미지 (정적 경로 /uploads 아래로 공개)
 * @param chatFileDir 채팅 첨부파일 (공개하지 않음. 서명된 주소로만 내려받기)
 * @param recordingDir 회의 녹화 (공개하지 않음. 서명된 주소로만 재생·내려받기)
 */
@ConfigurationProperties("app.storage")
data class StorageProperties(val uploadDir: String, val chatFileDir: String, val recordingDir: String = "./data/recordings")
