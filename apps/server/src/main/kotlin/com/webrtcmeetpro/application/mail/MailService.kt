package com.webrtcmeetpro.application.mail

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

data class MailMessage(val to: String, val subject: String, val text: String, val html: String? = null)

/** 느린 SMTP 가 요청을 붙잡지 않게 비동기로 보낸다 */
interface MailSender {
    fun send(message: MailMessage)
}

/** Accept-Language 의 첫 번째 지원 언어, 없으면 한국어 */
enum class MailLanguage {
    KO, EN, JA, ZH;

    companion object {
        fun of(tag: String?): MailLanguage? = when (tag?.trim()?.take(2)?.lowercase()) {
            "ko" -> KO
            "en" -> EN
            "ja" -> JA
            "zh" -> ZH
            else -> null
        }

        fun current(): MailLanguage {
            val header = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)
                ?.request?.getHeader("Accept-Language")
            return header?.split(',')?.firstNotNullOfOrNull { of(it.substringBefore(';')) } ?: KO
        }
    }
}

@Service
class MailService(private val sender: MailSender) {

    /** 롤백됐는데 메일만 나가는 일이 없게 트랜잭션 안이면 커밋된 뒤에 보낸다 */
    fun sendAfterCommit(message: MailMessage) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = sender.send(message)
            })
        } else {
            sender.send(message)
        }
    }
}
