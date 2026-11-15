package com.webrtcmeetpro.infrastructure.push

import com.webrtcmeetpro.config.properties.PushProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * 앱 푸시(FCM HTTP v1). google-auth 라이브러리 없이 서비스 계정 키로 JWT(RS256)를 만들어 OAuth 토큰을 받는다.
 * 앱이 알림을 직접 만들어야 채팅방별로 묶고 눌렀을 때 그 방으로 갈 수 있어서 data 메시지만 보낸다.
 */
@Component
class FcmSender(private val props: PushProperties, private val json: JsonMapper) {
    private class Account(val projectId: String, val clientEmail: String, val privateKey: PrivateKey, val tokenUri: String)

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val account: Account? by lazy { load() }

    @Volatile private var accessToken: String? = null
    @Volatile private var expiresAt: Instant = Instant.EPOCH

    val enabled: Boolean get() = account != null

    fun send(token: String, data: Map<String, String>, high: Boolean): PushResult {
        val acc = account ?: return PushResult.FAILED
        return try {
            val message = mapOf(
                "message" to mapOf(
                    "token" to token,
                    "data" to data,
                    // HIGH 여야 Doze 상태의 휴대폰도 깨워서 바로 보여 준다
                    "android" to mapOf("priority" to if (high) "HIGH" else "NORMAL", "ttl" to "86400s"),
                ),
            )
            val request = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/${acc.projectId}/messages:send"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer ${accessToken(acc)}")
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(message)))
                .build()
            val res = http.send(request, HttpResponse.BodyHandlers.ofString())
            when {
                res.statusCode() in 200..299 -> PushResult.SENT
                // 앱을 지웠거나 토큰이 바뀌어 다시는 쓸 수 없는 토큰
                res.statusCode() == 404 || "UNREGISTERED" in res.body() || "not a valid FCM registration token" in res.body() -> PushResult.GONE
                else -> {
                    log.warn("FCM 응답 {}: {}", res.statusCode(), res.body().take(300))
                    PushResult.FAILED
                }
            }
        } catch (e: Exception) {
            log.warn("FCM 보내기 실패: {}", e.message)
            PushResult.FAILED
        }
    }

    /** 토큰은 1시간 쓸 수 있어서 만료 1분 전까지 다시 쓴다 */
    @Synchronized
    private fun accessToken(acc: Account): String {
        val now = Instant.now()
        accessToken?.takeIf { now.isBefore(expiresAt) }?.let { return it }
        val header = b64.encodeToString(json.writeValueAsBytes(mapOf("alg" to "RS256", "typ" to "JWT")))
        val claims = b64.encodeToString(
            json.writeValueAsBytes(
                mapOf("iss" to acc.clientEmail, "scope" to SCOPE, "aud" to acc.tokenUri, "iat" to now.epochSecond, "exp" to now.epochSecond + 3600),
            ),
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(acc.privateKey)
            update("$header.$claims".toByteArray())
            sign()
        }
        val form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", Charsets.UTF_8) +
            "&assertion=$header.$claims.${b64.encodeToString(signature)}"
        val res = http.send(
            HttpRequest.newBuilder(URI.create(acc.tokenUri))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(res.statusCode() == 200) { "FCM 토큰 발급 실패 ${res.statusCode()}: ${res.body().take(200)}" }
        val body = json.readTree(res.body())
        val token = body["access_token"].asString()
        accessToken = token
        expiresAt = now.plusSeconds(body["expires_in"].asLong() - 60)
        return token
    }

    private fun load(): Account? {
        val path = props.fcmCredentialsFile.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.isFile) {
            log.warn("FCM 서비스 계정 키 파일이 없어 앱 푸시를 끕니다: {}", path)
            return null
        }
        return try {
            val node = json.readTree(file)
            val pem = node["private_key"].asString()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace(Regex("\\s"), "")
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
            Account(
                projectId = node["project_id"].asString(),
                clientEmail = node["client_email"].asString(),
                privateKey = key,
                tokenUri = node["token_uri"]?.asString() ?: "https://oauth2.googleapis.com/token",
            )
        } catch (e: Exception) {
            log.warn("FCM 서비스 계정 키를 읽지 못해 앱 푸시를 끕니다: {}", e.message)
            null
        }
    }

    private companion object {
        const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
    }
}
