package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.config.properties.JwtProperties
import com.webrtcmeetpro.config.properties.TossProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 토스 빌링키 암호화(AES-GCM)와 customerKey 서명.
 * 빌링키는 이것만 있으면 결제를 할 수 있는 값이라 DB 에 그대로 두지 않는다.
 */
@Component
class BillingKeyCipher(toss: TossProperties, jwt: JwtProperties) {

    private val secret = toss.billingKeySecret.ifBlank { "billing:${jwt.secret}" }
    private val aesKey = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()), "AES")
    private val macKey = SecretKeySpec("customer:$secret".toByteArray(), "HmacSHA256")
    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv)) }
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray()))
    }

    fun decrypt(encrypted: String): String {
        val bytes = Base64.getDecoder().decode(encrypted)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, bytes, 0, 12)) }
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }

    /**
     * 카드 등록창에 넘길 customerKey: "u{userId}-{무작위}-{서명}" (토스 규칙: 22~50자, - 포함).
     * 등록이 끝나고 돌아왔을 때 이 사용자가 만든 키인지 서명으로 확인한다 (DB 에 미리 저장하지 않아도 됨).
     */
    fun newCustomerKey(userId: Long): String {
        val nonce = ByteArray(6).also(random::nextBytes).toHex()
        return "u$userId-$nonce-${sign(userId, nonce)}"
    }

    fun isOwnCustomerKey(userId: Long, customerKey: String): Boolean {
        val parts = customerKey.split('-')
        if (parts.size != 3 || parts[0] != "u$userId") return false
        return MessageDigest.isEqual(sign(userId, parts[1]).toByteArray(), parts[2].toByteArray())
    }

    private fun sign(userId: Long, nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(macKey) }
        return mac.doFinal("$userId:$nonce".toByteArray()).toHex().take(16)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
