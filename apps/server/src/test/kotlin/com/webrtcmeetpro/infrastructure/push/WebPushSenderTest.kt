package com.webrtcmeetpro.infrastructure.push

import com.webrtcmeetpro.config.properties.PushProperties
import com.webrtcmeetpro.domain.user.User
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WebPushSenderTest {

    /** 브라우저 쪽(RFC 8291 받는 쪽) 계산으로 풀어서 원래 내용이 나오는지 */
    @Test
    fun `암호화한 내용을 브라우저처럼 풀면 원래 글이 나온다`() {
        val ua = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val uaPublic = raw(ua.public as ECPublicKey)
        val auth = ByteArray(16) { it.toByte() }
        val payload = """{"title":"# 영업","body":"윤지우: 안녕하세요 👋"}""".toByteArray()

        val body = WebPushSender(PushProperties()).encrypt(payload, uaPublic, auth)

        val buf = ByteBuffer.wrap(body)
        val salt = ByteArray(16).also(buf::get)
        assertEquals(4096, buf.int)
        val idLen = buf.get().toInt()
        val asPublic = ByteArray(idLen).also(buf::get)
        val cipherText = ByteArray(buf.remaining()).also(buf::get)

        val shared = KeyAgreement.getInstance("ECDH").run {
            init(ua.private)
            doPhase(publicKey(asPublic, ua.public as ECPublicKey), true)
            generateSecret()
        }
        val ikm = hkdf(auth, shared, "WebPush: info".toByteArray() + 0 + uaPublic + asPublic, 32)
        val cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm".toByteArray() + 0, 16)
        val nonce = hkdf(salt, ikm, "Content-Encoding: nonce".toByteArray() + 0, 12)
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce))
            doFinal(cipherText)
        }
        assertEquals(2.toByte(), plain.last(), "마지막 레코드 표시 0x02")
        assertArrayEquals(payload, plain.copyOf(plain.size - 1))
    }

    @Test
    fun `방해 금지 시간 - 밤을 넘기는 시간과 같은 날 시간`() {
        val user = User.registerSocial("dnd@test.com", "닉네임", null)
        assertFalse(user.inQuietHours(), "정하지 않으면 늘 아님")
        assertTrue(user.setQuietHours("22:00", "08:00", "Asia/Seoul"))
        assertTrue(user.inQuietHours(Instant.parse("2026-11-12T14:30:00Z")), "서울 23:30")
        assertTrue(user.inQuietHours(Instant.parse("2026-11-12T22:59:00Z")), "서울 07:59")
        assertFalse(user.inQuietHours(Instant.parse("2026-11-12T23:00:00Z")), "서울 08:00 끝")
        assertFalse(user.inQuietHours(Instant.parse("2026-11-12T03:00:00Z")), "서울 12:00")
        assertTrue(user.setQuietHours("12:00", "13:00", "Asia/Seoul"))
        assertTrue(user.inQuietHours(Instant.parse("2026-11-12T03:30:00Z")), "서울 12:30")
        assertFalse(user.setQuietHours("25:00", "08:00", null), "잘못된 시각")
        assertTrue(user.setQuietHours(null, null, null))
        assertFalse(user.inQuietHours(Instant.parse("2026-11-12T03:30:00Z")), "끄면 아님")
    }

    private fun raw(key: ECPublicKey): ByteArray {
        fun fixed(n: BigInteger) = n.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else ByteArray(32 - it.size) + it }
        return byteArrayOf(4) + fixed(key.w.affineX) + fixed(key.w.affineY)
    }

    private fun publicKey(raw: ByteArray, sameCurve: ECPublicKey): ECPublicKey {
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, sameCurve.params)) as ECPublicKey
    }

    private fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(salt, "HmacSHA256")); doFinal(ikm) }
        return Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(prk, "HmacSHA256")); doFinal(info + 1) }.copyOf(length)
    }
}
