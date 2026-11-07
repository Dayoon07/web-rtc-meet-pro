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
 * @param type local = 서버 디스크 (기본), s3 = AWS S3 (프로필 이미지·채팅 첨부·녹화)
 * @param uploadDir 프로필 이미지 (/uploads/profile/{이름} 으로 공개)
 * @param chatFileDir 채팅 첨부파일 (공개하지 않음. 서명된 주소로만 내려받기)
 * @param recordingDir 회의 녹화 (공개하지 않음. 서명된 주소로만 재생·내려받기). S3 를 써도 녹화 중인 조각은 여기에 모았다가 끝나면 올린다
 */
@ConfigurationProperties("app.storage")
data class StorageProperties(
    val uploadDir: String,
    val chatFileDir: String,
    val recordingDir: String = "./data/recordings",
    val type: String = "local",
    val s3: S3 = S3(),
) {
    /**
     * @param prefix 버킷 안의 폴더 (예: dev/, prod/). 개발과 운영이 같은 버킷을 써도 섞이지 않게
     * @param accessKey 비우면 AWS 기본 방법(환경변수 AWS_ACCESS_KEY_ID, EC2 역할 등)으로 찾는다
     */
    data class S3(
        val bucket: String = "",
        val region: String = "ap-northeast-2",
        val accessKey: String = "",
        val secretKey: String = "",
        val prefix: String = "",
    )
}
