package com.webrtcmeetpro.infrastructure.push

import com.webrtcmeetpro.config.properties.PushProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class PushTarget(val endpoint: String, val p256dh: String, val auth: String)

/** GONE = 브라우저에서 알림을 끄거나 오래 안 써서 구독이 사라짐 */
enum class PushResult { SENT, GONE, FAILED }

/**
 * 외부 라이브러리 없이 JDK 암호화만으로 보낸다.
 * 내용은 RFC 8291(aes128gcm), 보낸 사람 확인은 RFC 8292 VAPID(ES256 JWT).
 * 키는 base64url: 공개 키 65바이트(04||x||y), 개인 키 32바이트.
 */
@Component
class WebPushSender(private val props: PushProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val random = SecureRandom()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val unb64 = Base64.getUrlDecoder()

    private val curve: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }.getParameterSpec(ECParameterSpec::class.java)
    }
    private val vapidPrivate: ECPrivateKey? by lazy {
        if (!props.enabled) null
        else KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, unb64.decode(props.vapidPrivateKey)), curve)) as ECPrivateKey
    }

    fun send(target: PushTarget, payload: ByteArray, urgency: String = "normal"): PushResult {
        val key = vapidPrivate ?: return PushResult.FAILED
        return try {
            val body = encrypt(payload, unb64.decode(target.p256dh), unb64.decode(target.auth))
            val uri = URI.create(target.endpoint)
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", "86400")
                .header("Urgency", urgency)
                .header("Authorization", "vapid t=${vapidJwt(key, "${uri.scheme}://${uri.authority}")}, k=${props.vapidPublicKey}")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
            val res = http.send(request, HttpResponse.BodyHandlers.ofString())
            when (res.statusCode()) {
                in 200..299 -> PushResult.SENT
                404, 410 -> PushResult.GONE
                else -> {
                    log.warn("웹 푸시 실패 {} {}: {}", res.statusCode(), uri.host, res.body().take(200))
                    PushResult.FAILED
                }
            }
        } catch (e: Exception) {
            log.warn("웹 푸시 실패 {}: {}", target.endpoint.take(60), e.message)
            PushResult.FAILED
        }
    }

    /** RFC 8291: salt(16) | rs(4) | idlen(1) | 내 임시 공개 키(65) | 암호문 */
    internal fun encrypt(payload: ByteArray, uaPublic: ByteArray, authSecret: ByteArray): ByteArray {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }
        val ephemeral = generator.generateKeyPair()
        val asPublic = uncompressed(ephemeral.public as ECPublicKey)

        val shared = KeyAgreement.getInstance("ECDH").run {
            init(ephemeral.private)
            doPhase(publicKey(uaPublic), true)
            generateSecret()
        }
        val ikm = hkdf(authSecret, shared, "WebPush: info".toByteArray() + 0 + uaPublic + asPublic, 32)
        val salt = ByteArray(16).also(random::nextBytes)
        val cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm".toByteArray() + 0, 16)
        val nonce = hkdf(salt, ikm, "Content-Encoding: nonce".toByteArray() + 0, 12)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce)) }
        // 하나뿐인 레코드가 마지막 레코드라 0x02 를 붙인다
        val encrypted = cipher.doFinal(payload + 2)
        return ByteBuffer.allocate(16 + 4 + 1 + asPublic.size + encrypted.size)
            .put(salt).putInt(RECORD_SIZE).put(asPublic.size.toByte()).put(asPublic).put(encrypted).array()
    }

    /** RFC 8292: aud = 받는 푸시 서버, 12시간 유효 */
    private fun vapidJwt(key: ECPrivateKey, audience: String): String {
        val header = b64.encodeToString("""{"typ":"JWT","alg":"ES256"}""".toByteArray())
        val exp = Instant.now().plusSeconds(12 * 3600).epochSecond
        val claims = b64.encodeToString("""{"aud":"$audience","exp":$exp,"sub":"${props.subject}"}""".toByteArray())
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update("$header.$claims".toByteArray())
            sign()
        }
        return "$header.$claims.${b64.encodeToString(derToRaw(der))}"
    }

    private fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(salt, "HmacSHA256"))
            doFinal(ikm)
        }
        // 32바이트 이하만 쓰므로 한 블록이면 된다
        val okm = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(prk, "HmacSHA256"))
            doFinal(info + 1)
        }
        return okm.copyOf(length)
    }

    private fun publicKey(raw: ByteArray): ECPublicKey {
        require(raw.size == 65 && raw[0] == 4.toByte()) { "브라우저 공개 키 모양이 아닙니다" }
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, curve)) as ECPublicKey
    }

    private fun uncompressed(key: ECPublicKey): ByteArray =
        byteArrayOf(4) + fixed32(key.w.affineX.toByteArray()) + fixed32(key.w.affineY.toByteArray())

    private fun fixed32(n: ByteArray): ByteArray = when {
        n.size == 32 -> n
        n.size > 32 -> n.copyOfRange(n.size - 32, n.size)
        else -> ByteArray(32 - n.size) + n
    }

    /** JDK 서명은 DER 인데 JWS 는 R||S 64바이트를 쓴다 */
    private fun derToRaw(der: ByteArray): ByteArray {
        var i = 2
        if (der[1].toInt() and 0x80 != 0) i += der[1].toInt() and 0x7f
        val rLen = der[i + 1].toInt()
        val r = der.copyOfRange(i + 2, i + 2 + rLen)
        i += 2 + rLen
        val sLen = der[i + 1].toInt()
        val s = der.copyOfRange(i + 2, i + 2 + sLen)
        return fixed32(r) + fixed32(s)
    }

    companion object {
        private const val RECORD_SIZE = 4096
    }
}
