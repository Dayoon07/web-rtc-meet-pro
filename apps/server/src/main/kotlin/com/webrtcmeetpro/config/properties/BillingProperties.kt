package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 토스 개발자센터의 "API 개별 연동 키". 하나라도 비어 있으면 결제가 꺼진다.
 * @param billingKeySecret 비우면 JWT 비밀값에서 만든다. 운영은 따로 정해야 JWT 키를 바꿔도 빌링키를 풀 수 있다
 */
@ConfigurationProperties("app.toss")
data class TossProperties(
    val clientKey: String = "",
    val secretKey: String = "",
    val billingKeySecret: String = "",
    val apiUrl: String = "https://api.tosspayments.com",
) {
    val enabled: Boolean get() = clientKey.isNotBlank() && secretKey.isNotBlank()
}
