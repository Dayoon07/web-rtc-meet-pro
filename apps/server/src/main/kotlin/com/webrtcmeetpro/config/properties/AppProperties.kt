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

@ConfigurationProperties("app.storage")
data class StorageProperties(val uploadDir: String)
