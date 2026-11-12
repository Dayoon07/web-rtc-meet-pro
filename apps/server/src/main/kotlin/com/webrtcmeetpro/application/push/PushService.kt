package com.webrtcmeetpro.application.push

import com.webrtcmeetpro.application.presence.PresenceService
import com.webrtcmeetpro.application.presence.PresenceStatus
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.PushProperties
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.push.PushSubscription
import com.webrtcmeetpro.domain.push.PushSubscriptionRepository
import com.webrtcmeetpro.domain.user.UserRepository
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

/** 알림을 브라우저 푸시로도 보내는 쪽 (NotificationService 가 쓴다. 테스트에서는 빈 구현) */
fun interface PushNotifier {
    fun notification(userIds: Collection<Long>, type: NotificationType, params: Map<String, Any?>, link: String?)
}

/** 화면이 구독할 때 쓰는 값. enabled=false 면 서버에 VAPID 키가 없어 푸시를 못 보낸다 */
data class PushConfigView(val enabled: Boolean, val publicKey: String?)

/** 서비스 워커(public/sw.js)가 받아서 띄우는 값 */
data class PushPayload(val title: String, val body: String, val link: String?, val tag: String?)

/**
 * 브라우저 푸시 (창을 닫아도 오는 알림).
 * - 채팅 새 메시지: 화면을 보고 있지 않은 사람(자리 비움·접속 안 함)에게. 알림 끈 방은 빼고
 * - 알림(초대·회의·멘션…): 접속하지 않은 사람에게 (접속 중이면 화면 알림·OS 알림이 이미 뜬다)
 * - 방해 금지 시간인 사람은 빼고, 사라진 구독(404·410)은 지운다
 * 보내기는 트랜잭션이 끝난 뒤 별도 스레드에서 (요청을 늦추지 않게)
 */
@Service
class PushService(
    private val subscriptions: PushSubscriptionRepository,
    private val sender: WebPushSender,
    private val props: PushProperties,
    private val presence: PresenceService,
    private val users: UserRepository,
    private val json: JsonMapper,
    private val tx: TransactionTemplate,
) : PushNotifier {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    fun config() = PushConfigView(props.enabled, props.vapidPublicKey.takeIf { props.enabled })

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

    /** 채팅 새 메시지. title = 채널 이름이나 보낸 사람, body(언어) = 내용 */
    fun chatMessage(recipients: Collection<Long>, title: String, link: String, tag: String, body: (lang: String?) -> String) = afterCommit {
        dispatch(recipients.filter { presence.status(it) != PresenceStatus.ONLINE }, "high") { lang -> PushPayload(title, body(lang), link, tag) }
    }

    /** 알림 목록에 쌓이는 알림 (NotificationService 가 부른다) */
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
        if (!props.enabled || userIds.isEmpty()) return
        val ids = userIds.toSet()
        executor.execute {
            try {
                val now = Instant.now()
                val awake = users.findAllByIdIn(ids).filter { !it.inQuietHours(now) }.map { it.id!! }
                if (awake.isEmpty()) return@execute
                val gone = mutableListOf<String>()
                val sent = mutableListOf<String>()
                subscriptions.findAllByUserIdInAndDeletedFalse(awake).forEach { s ->
                    when (sender.send(PushTarget(s.endpoint, s.p256dh, s.authSecret), json.writeValueAsBytes(build(s.lang)), urgency)) {
                        PushResult.SENT -> sent += s.endpoint
                        PushResult.GONE -> gone += s.endpoint
                        PushResult.FAILED -> Unit
                    }
                }
                if (gone.isNotEmpty() || sent.isNotEmpty()) {
                    tx.executeWithoutResult {
                        gone.forEach { subscriptions.findByEndpoint(it)?.cancel() }
                        sent.forEach { subscriptions.findByEndpoint(it)?.sent() }
                    }
                }
            } catch (e: Exception) {
                log.warn("웹 푸시 보내기 실패: {}", e.message)
            }
        }
    }

    /** 저장이 끝난 뒤에 보낸다 (롤백되면 안 보냄). 트랜잭션 밖이면 바로 */
    private fun afterCommit(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return action()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = action()
        })
    }
}
