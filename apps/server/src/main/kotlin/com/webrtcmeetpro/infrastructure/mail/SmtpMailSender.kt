package com.webrtcmeetpro.infrastructure.mail

import com.webrtcmeetpro.application.mail.MailMessage
import com.webrtcmeetpro.application.mail.MailSender
import com.webrtcmeetpro.config.properties.MailProperties
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * host 가 비어 있으면 보내지 않고 로그에 본문(링크 포함)을 남긴다.
 * 스프링 부트의 메일 자동 설정은 호스트가 빈 값이어도 발송기를 만들어 개발 중에 실패 로그가 쌓여서 쓰지 않는다.
 */
@Component
class SmtpMailSender(private val props: MailProperties) : MailSender {
    private val log = LoggerFactory.getLogger(javaClass)

    private val mailer: JavaMailSenderImpl? = if (!props.enabled) null else JavaMailSenderImpl().apply {
        host = props.host
        port = props.port
        if (props.username.isNotBlank()) username = props.username
        if (props.password.isNotBlank()) password = props.password
        defaultEncoding = "UTF-8"
        javaMailProperties["mail.smtp.auth"] = props.username.isNotBlank().toString()
        javaMailProperties["mail.smtp.starttls.enable"] = props.starttls.toString()
        // STARTTLS 를 켰으면 암호화 없이는 보내지 않는다
        javaMailProperties["mail.smtp.starttls.required"] = props.starttls.toString()
        javaMailProperties["mail.smtp.connectiontimeout"] = "10000"
        javaMailProperties["mail.smtp.timeout"] = "10000"
        javaMailProperties["mail.smtp.writetimeout"] = "10000"
    }

    @Async
    override fun send(message: MailMessage) {
        val mailer = mailer
        if (mailer == null) {
            log.info("[메일 발송 꺼짐: MAIL_HOST 없음] to={} subject={}\n{}", message.to, message.subject, message.text)
            return
        }
        // 메일 실패가 초대·재설정 요청까지 실패시키면 안 된다
        runCatching {
            val mime = mailer.createMimeMessage()
            MimeMessageHelper(mime, message.html != null, "UTF-8").apply {
                setFrom(props.from)
                setTo(message.to)
                setSubject(message.subject)
                if (message.html != null) setText(message.text, message.html) else setText(message.text)
            }
            mailer.send(mime)
        }.onFailure { e ->
            // Gmail 은 "535-5.7.8 Username and Password not accepted" 처럼 안쪽 예외에 이유가 있다
            val causes = generateSequence(e) { it.cause }.mapNotNull { it.message?.lineSequence()?.firstOrNull() }.distinct().joinToString(" ← ")
            log.warn("메일 발송 실패 to={} subject={}: {}", message.to, message.subject, causes)
        }
    }
}
