package com.webrtcmeetpro.application.mail

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/** 보낼 메일 한 통. html 이 있으면 HTML + 텍스트 둘 다 보낸다 */
data class MailMessage(val to: String, val subject: String, val text: String, val html: String? = null)

/** 실제 발송 (infrastructure/mail). 느린 SMTP 가 요청을 붙잡지 않게 비동기로 보낸다 */
interface MailSender {
    fun send(message: MailMessage)
}

/** 메일 언어. 웹·앱이 보내는 Accept-Language 의 첫 번째 지원 언어, 없으면 한국어 */
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

        /** 지금 처리 중인 HTTP 요청의 Accept-Language (예: "ja,en;q=0.8") */
        fun current(): MailLanguage {
            val header = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)
                ?.request?.getHeader("Accept-Language")
            return header?.split(',')?.firstNotNullOfOrNull { of(it.substringBefore(';')) } ?: KO
        }
    }
}

@Service
class MailService(private val sender: MailSender) {

    /**
     * 트랜잭션 안에서 부르면 커밋된 뒤에 보낸다 (롤백되면 안 보낸다).
     * 예: 초대를 저장하다 실패했는데 초대 메일만 가는 일을 막는다
     */
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
