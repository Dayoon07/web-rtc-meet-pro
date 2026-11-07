package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 토스페이먼츠 자동결제(빌링). 개발자센터 → API 키 → "API 개별 연동 키" (test_ck_ / test_sk_).
 * 둘 중 하나라도 비어 있으면 결제 기능이 꺼진다 (요금제는 보이지만 결제 버튼이 막힘).
 * @param billingKeySecret 빌링키를 DB 에 암호화해 넣을 때 쓰는 비밀값. 비우면 JWT 비밀값에서 만든다 (운영은 따로 정하기)
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
