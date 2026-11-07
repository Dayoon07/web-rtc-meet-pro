package com.webrtcmeetpro.external.toss

import com.webrtcmeetpro.config.properties.TossProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * 토스가 거절했거나(카드 한도·정지 등) 연결에 실패함. message 는 토스가 보낸 한국어 문구.
 * @param uncertain 연결이 끊겨서 결제가 됐는지 모름 (같은 orderId 로 다시 시도하면 토스가 한 번만 처리한다)
 */
class TossException(val code: String, override val message: String, val uncertain: Boolean = false) : RuntimeException(message)

data class TossBilling(val billingKey: String, val cardCompany: String?, val cardNumber: String?)

data class TossPayment(
    val paymentKey: String,
    val method: String?,
    val receiptUrl: String?,
    val cardCompany: String? = null,
    val cardNumber: String? = null,
    val approveNo: String? = null,
)

/**
 * 토스페이먼츠 자동결제 API (https://docs.tosspayments.com/reference).
 * 인증: Basic base64("시크릿키:")
 */
@Component
class TossPaymentsClient(private val props: TossProperties, private val json: JsonMapper) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    /** 카드 등록창이 돌려준 authKey 로 빌링키 발급 */
    fun issueBillingKey(authKey: String, customerKey: String): TossBilling {
        val body = post("/v1/billing/authorizations/issue", mapOf("authKey" to authKey, "customerKey" to customerKey), null)
        val card = body["card"] as? Map<*, *>
        return TossBilling(
            billingKey = body["billingKey"] as? String ?: throw TossException("INVALID_RESPONSE", "빌링키를 받지 못했어요."),
            cardCompany = (body["cardCompany"] as? String) ?: (card?.get("issuerCode") as? String)?.let(::issuerName),
            cardNumber = (body["cardNumber"] as? String) ?: (card?.get("number") as? String),
        )
    }

    /** 빌링키로 결제 승인. orderId 를 멱등 키로 써서 재시도해도 두 번 결제되지 않게 */
    fun charge(billingKey: String, customerKey: String, amount: Long, orderId: String, orderName: String, email: String?, name: String?): TossPayment {
        val payload = buildMap<String, Any> {
            put("customerKey", customerKey)
            put("amount", amount)
            put("orderId", orderId)
            put("orderName", orderName)
            email?.let { put("customerEmail", it) }
            name?.let { put("customerName", it.take(100)) }
        }
        val body = post("/v1/billing/${URLEncoder.encode(billingKey, Charsets.UTF_8)}", payload, orderId)
        if (body["status"] != "DONE") throw TossException(body["status"] as? String ?: "UNKNOWN", "결제가 완료되지 않았어요.")
        val card = body["card"] as? Map<*, *>
        return TossPayment(
            paymentKey = body["paymentKey"] as String,
            method = body["method"] as? String,
            receiptUrl = (body["receipt"] as? Map<*, *>)?.get("url") as? String,
            cardCompany = (card?.get("issuerCode") as? String)?.let(::issuerName),
            cardNumber = card?.get("number") as? String,
            approveNo = card?.get("approveNo") as? String,
        )
    }

    private fun post(path: String, payload: Map<String, Any>, idempotencyKey: String?): Map<*, *> {
        val auth = Base64.getEncoder().encodeToString("${props.secretKey}:".toByteArray())
        val request = HttpRequest.newBuilder(URI.create(props.apiUrl + path))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Basic $auth")
            .header("Content-Type", "application/json")
            .apply { idempotencyKey?.let { header("Idempotency-Key", it) } }
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
            .build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            log.warn("토스페이먼츠 연결 실패", e)
            throw TossException("NETWORK_ERROR", "결제 서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.", uncertain = true)
        }
        val body = runCatching { json.readValue(response.body(), Map::class.java) }.getOrNull() ?: emptyMap<String, Any>()
        if (response.statusCode() !in 200..299) {
            val code = body["code"] as? String ?: "HTTP_${response.statusCode()}"
            val message = body["message"] as? String ?: "결제 요청이 거절됐어요."
            log.info("토스페이먼츠 거절: {} {}", code, message)
            throw TossException(code, message, uncertain = response.statusCode() >= 500)
        }
        return body
    }

    /** 카드사 코드 → 이름 (없으면 코드 그대로) */
    private fun issuerName(code: String) = ISSUERS[code] ?: code

    companion object {
        private val ISSUERS = mapOf(
            "3K" to "기업BC", "46" to "광주", "71" to "롯데", "30" to "산업", "31" to "BC", "51" to "삼성",
            "38" to "새마을", "41" to "신한", "62" to "신협", "36" to "씨티", "33" to "우리", "W1" to "우리",
            "37" to "우체국", "39" to "저축", "35" to "전북", "42" to "제주", "15" to "카카오뱅크", "3A" to "케이뱅크",
            "24" to "토스뱅크", "21" to "하나", "61" to "현대", "11" to "KB국민", "91" to "NH농협", "34" to "Sh수협",
        )
    }
}
