package com.webrtcmeetpro.application.chat

import com.webrtcmeetpro.config.properties.JwtProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * <img src> 는 Authorization 헤더를 못 보내므로, 메시지를 볼 권한이 있는 사람에게만 서명된 주소를 준다.
 * 만료 시각을 12시간 단위로 끊어 같은 구간 안에서는 주소가 같게 해야 브라우저 캐시가 먹는다 (유효 기간 12~24시간).
 */
@Component
class ChatFileUrlSigner(props: JwtProperties) {

    // JWT 서명 키가 새어도 파일 주소를 만들 수 없게 용도 이름을 붙여 따로 쓴다
    private val key = SecretKeySpec("chat-file:${props.secret}".toByteArray(), "HmacSHA256")

    fun url(fileId: Long, now: Instant = Instant.now()): String {
        val exp = (now.epochSecond / WINDOW_SECONDS + 2) * WINDOW_SECONDS
        return "/api/chat/files/$fileId?exp=$exp&sig=${sign(fileId, exp)}"
    }

    fun isValid(fileId: Long, exp: Long, sig: String, now: Instant = Instant.now()): Boolean =
        now.epochSecond <= exp && MessageDigest.isEqual(sign(fileId, exp).toByteArray(), sig.toByteArray())

    private fun sign(fileId: Long, exp: Long): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("$fileId:$exp".toByteArray()))
    }

    companion object {
        const val WINDOW_SECONDS = 12 * 60 * 60L
    }
}
