package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** 둘 다 있어야 창을 닫아도 알림이 간다. 키 만들기: python scripts/vapid-keys.py */
@ConfigurationProperties("app.push")
data class PushProperties(
    val vapidPublicKey: String = "",
    val vapidPrivateKey: String = "",
    /** 푸시 서버가 문제가 있을 때 연락할 곳 */
    val subject: String = "mailto:admin@localhost",
    /** 앱 푸시(FCM) 서비스 계정 키(JSON) 경로. 비우면 앱 푸시를 끈다 */
    val fcmCredentialsFile: String = "",
) {
    val enabled: Boolean get() = vapidPublicKey.isNotBlank() && vapidPrivateKey.isNotBlank()
}
