package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** host 를 비우면 로컬 개발용으로 보내지 않고 서버 로그에 내용과 링크를 남긴다 */
@ConfigurationProperties("app.mail")
data class MailProperties(
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    /** SMTP 계정이 보낼 수 있는 주소여야 한다 */
    val from: String = "MQ <no-reply@localhost>",
    val starttls: Boolean = true,
) {
    val enabled: Boolean get() = host.isNotBlank()
}

/** 메일 안의 링크를 만들 때 쓴다 */
@ConfigurationProperties("app.web")
data class WebProperties(val url: String = "http://localhost:5173") {
    fun link(path: String): String = url.trimEnd('/') + path
}

/** @param tablePrefix 테스트용 사본(zz_) 테이블을 볼 때만 쓴다 */
@ConfigurationProperties("app.admin")
data class AdminProperties(val tablePrefix: String = "")
