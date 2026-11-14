package com.webrtcmeetpro.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 다른 시간대의 브라우저가 회의 시간을 잘못 읽지 않게, API 로는 항상 오프셋을 붙여 내보낸다.
 * 입력에 오프셋이 없으면 서버 시간으로 본다.
 */
@Configuration
class JacksonConfig {

    @Bean
    fun offsetLocalDateTimeModule(): SimpleModule = SimpleModule("offset-local-date-time")
        .addSerializer(LocalDateTime::class.java, object : ValueSerializer<LocalDateTime>() {
            override fun serialize(value: LocalDateTime, gen: JsonGenerator, ctxt: SerializationContext) {
                gen.writeString(toOffsetString(value))
            }
        })
        .addDeserializer(LocalDateTime::class.java, object : ValueDeserializer<LocalDateTime>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalDateTime = parse(p.string.trim())
        })

    companion object {
        fun toOffsetString(value: LocalDateTime): String =
            value.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()

        fun parse(text: String): LocalDateTime =
            if (text.endsWith("Z") || Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(text)) {
                OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            } else {
                LocalDateTime.parse(text)
            }
    }
}
