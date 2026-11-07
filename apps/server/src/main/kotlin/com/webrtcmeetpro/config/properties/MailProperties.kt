package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * SMTP 메일 설정. host 를 비워 두면(로컬 개발) 메일을 실제로 보내지 않고 서버 로그에 내용과 링크를 남긴다.
 * 예: Gmail 은 smtp.gmail.com / 587 / 앱 비밀번호, 네이버는 smtp.naver.com / 587.
 */
@ConfigurationProperties("app.mail")
data class MailProperties(
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    /** 보내는 사람. 예: "Limeet <no-reply@example.com>" (SMTP 계정이 보낼 수 있는 주소여야 한다) */
    val from: String = "Limeet <no-reply@localhost>",
    val starttls: Boolean = true,
) {
    val enabled: Boolean get() = host.isNotBlank()
}

/** 웹 클라이언트 주소. 메일 안의 링크(비밀번호 재설정, 초대 확인)를 만들 때 쓴다 */
@ConfigurationProperties("app.web")
data class WebProperties(val url: String = "http://localhost:5173") {
    fun link(path: String): String = url.trimEnd('/') + path
}

/**
 * 관리자 사이트 설정.
 * @param tablePrefix 데이터 관리에 보일 테이블 이름 접두어. 운영·로컬은 비워 둔다 (테스트용 사본 zz_ 테이블을 볼 때만 zz_)
 */
@ConfigurationProperties("app.admin")
data class AdminProperties(val tablePrefix: String = "")
