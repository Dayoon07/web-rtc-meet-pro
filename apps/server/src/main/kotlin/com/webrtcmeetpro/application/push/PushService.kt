package com.webrtcmeetpro.application.push

import com.webrtcmeetpro.domain.presence.PresenceStatus
import com.webrtcmeetpro.domain.push.dto.res.PushConfigResDto
import com.webrtcmeetpro.application.presence.PresenceService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.PushProperties
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.push.DevicePlatform
import com.webrtcmeetpro.domain.push.PushDevice
import com.webrtcmeetpro.domain.push.PushDeviceRepository
import com.webrtcmeetpro.domain.push.PushSubscription
import com.webrtcmeetpro.domain.push.PushSubscriptionRepository
import com.webrtcmeetpro.domain.user.UserRepository
import com.webrtcmeetpro.infrastructure.push.FcmSender
import com.webrtcmeetpro.infrastructure.push.PushResult
import com.webrtcmeetpro.infrastructure.push.PushTarget
import com.webrtcmeetpro.infrastructure.push.WebPushSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.concurrent.Executors

/** 테스트에서는 빈 구현으로 바꾼다 */
fun interface PushNotifier {
    fun notification(userIds: Collection<Long>, type: NotificationType, params: Map<String, Any?>, link: String?)
}

/** 서비스 워커(public/sw.js)가 받아서 띄운다 */
data class PushPayload(val title: String, val body: String, val link: String?, val tag: String?)

/**
 * 창을 닫거나 앱을 꺼도 오는 알림 (브라우저는 웹 푸시, Android 앱은 FCM).
 * 접속 중인 사람에게는 화면 알림이 이미 뜨므로 보내지 않고, 방해 금지 시간인 사람은 빼고, 사라진 구독·토큰은 지운다.
 * 요청을 늦추지 않게 커밋 뒤 별도 스레드에서 보낸다.
 */
@Service
class PushService(
    private val subscriptions: PushSubscriptionRepository,
    private val devices: PushDeviceRepository,
    private val sender: WebPushSender,
    private val fcm: FcmSender,
    private val props: PushProperties,
    private val presence: PresenceService,
    private val users: UserRepository,
    private val json: JsonMapper,
    private val tx: TransactionTemplate,
) : PushNotifier {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    fun config() = PushConfigResDto(props.enabled, props.vapidPublicKey.takeIf { props.enabled })

    @Transactional
    fun subscribe(userId: Long, endpoint: String, p256dh: String, auth: String, lang: String?, userAgent: String?) {
        if (!props.enabled) throw BusinessException(ErrorCode.PUSH_NOT_CONFIGURED)
        if (!endpoint.startsWith("https://") || endpoint.length > 1000) throw BusinessException(ErrorCode.INVALID_INPUT)
        val existing = subscriptions.findByEndpoint(endpoint)
        if (existing != null) existing.renew(userId, p256dh, auth, lang?.take(10), userAgent?.take(500))
        else subscriptions.save(PushSubscription(userId, endpoint, p256dh, auth, lang?.take(10), userAgent?.take(500)))
    }

    @Transactional
    fun unsubscribe(userId: Long, endpoint: String) {
        subscriptions.findByEndpoint(endpoint)?.takeIf { it.userId == userId }?.cancel()
    }

    @Transactional
    fun registerDevice(userId: Long, token: String, platform: DevicePlatform, lang: String?, userAgent: String?) {
        val existing = devices.findByToken(token)
        if (existing != null) existing.renew(userId, lang?.take(10), userAgent?.take(500))
        else devices.save(PushDevice(userId, platform, token, lang?.take(10), userAgent?.take(500)))
    }

    /** 로그아웃한 기기에 다른 사람의 알림이 가지 않게 지운다 */
    @Transactional
    fun unregisterDevice(userId: Long, token: String) {
        devices.findByToken(token)?.takeIf { it.userId == userId }?.cancel()
    }

    fun chatMessage(recipients: Collection<Long>, title: String, link: String, tag: String, body: (lang: String?) -> String) = afterCommit {
        dispatch(recipients.filter { presence.status(it) != PresenceStatus.ONLINE }, "high") { lang -> PushPayload(title, body(lang), link, tag) }
    }

    override fun notification(userIds: Collection<Long>, type: NotificationType, params: Map<String, Any?>, link: String?) = afterCommit {
        dispatch(userIds.filter { presence.status(it) == PresenceStatus.OFFLINE }, "normal") { lang ->
            if (type == NotificationType.CHAT_MENTION) {
                PushPayload(PushTexts.text(lang, "mention", mapOf("sender" to params["senderName"])), params["preview"]?.toString().orEmpty(), link, "mention")
            } else if (type == NotificationType.THREAD_REPLY) {
                val body = params["preview"]?.toString() ?: PushTexts.text(lang, "file")
                PushPayload(PushTexts.text(lang, "threadReply", mapOf("sender" to params["senderName"])), body, link, "thread-${params["rootId"]}")
            } else {
                PushPayload("MQ", PushTexts.text(lang, type.name, params), link, type.name)
            }
        }
    }

    private fun dispatch(userIds: Collection<Long>, urgency: String, build: (lang: String?) -> PushPayload) {
        if ((!props.enabled && !fcm.enabled) || userIds.isEmpty()) return
        val ids = userIds.toSet()
        executor.execute {
            try {
                val now = Instant.now()
                val awake = users.findAllByIdIn(ids).filter { !it.inQuietHours(now) }.map { it.id!! }
                if (awake.isEmpty()) return@execute
                val gone = mutableListOf<String>()
                val sent = mutableListOf<String>()
                if (props.enabled) {
                    subscriptions.findAllByUserIdInAndDeletedFalse(awake).forEach { s ->
                        when (sender.send(PushTarget(s.endpoint, s.p256dh, s.authSecret), json.writeValueAsBytes(build(s.lang)), urgency)) {
                            PushResult.SENT -> sent += s.endpoint
                            PushResult.GONE -> gone += s.endpoint
                            PushResult.FAILED -> Unit
                        }
                    }
                }
                val goneTokens = mutableListOf<String>()
                val sentTokens = mutableListOf<String>()
                if (fcm.enabled) {
                    devices.findAllByUserIdInAndDeletedFalse(awake).forEach { d ->
                        val p = build(d.lang)
                        val data = listOfNotNull("title" to p.title, "body" to p.body, p.link?.let { "link" to it }, p.tag?.let { "tag" to it }).toMap()
                        when (fcm.send(d.token, data, high = urgency == "high")) {
                            PushResult.SENT -> sentTokens += d.token
                            PushResult.GONE -> goneTokens += d.token
                            PushResult.FAILED -> Unit
                        }
                    }
                }
                if (gone.isNotEmpty() || sent.isNotEmpty() || goneTokens.isNotEmpty() || sentTokens.isNotEmpty()) {
                    tx.executeWithoutResult {
                        gone.forEach { subscriptions.findByEndpoint(it)?.cancel() }
                        sent.forEach { subscriptions.findByEndpoint(it)?.sent() }
                        goneTokens.forEach { devices.findByToken(it)?.cancel() }
                        sentTokens.forEach { devices.findByToken(it)?.sent() }
                    }
                }
            } catch (e: Exception) {
                log.warn("푸시 보내기 실패: {}", e.message)
            }
        }
    }

    /** 롤백되면 보내지 않는다 */
    private fun afterCommit(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return action()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = action()
        })
    }
}
