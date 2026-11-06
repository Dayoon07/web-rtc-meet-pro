package com.webrtcmeetpro.common.realtime

import com.webrtcmeetpro.common.security.CorsProperties
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * STOMP over WebSocket (/ws).
 * - CONNECT 프레임의 Authorization: Bearer <JWT> 로 인증, Principal 이름 = user_id
 * - 클라이언트는 자기 큐(/user/queue/...)만 구독할 수 있다. 보내기는 REST API 로 한다
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val jwtDecoder: JwtDecoder,
    private val corsProperties: CorsProperties,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws").setAllowedOrigins(*corsProperties.allowedOrigins.toTypedArray())
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/queue")
        registry.setUserDestinationPrefix("/user")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(object : ChannelInterceptor {
            override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
                val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java) ?: return message
                when (accessor.command) {
                    StompCommand.CONNECT -> {
                        val token = accessor.getFirstNativeHeader("Authorization")?.removePrefix("Bearer ")?.trim()
                            ?: throw IllegalArgumentException("Authorization 헤더가 필요합니다.")
                        val jwt = try {
                            jwtDecoder.decode(token)
                        } catch (e: JwtException) {
                            throw IllegalArgumentException("유효하지 않은 토큰입니다.", e)
                        }
                        val userId = jwt.subject ?: throw IllegalArgumentException("유효하지 않은 토큰입니다.")
                        accessor.user = UsernamePasswordAuthenticationToken(userId, null, emptyList())
                    }
                    StompCommand.SUBSCRIBE -> {
                        val dest = accessor.destination.orEmpty()
                        require(accessor.user != null && dest.startsWith("/user/queue/")) { "구독할 수 없는 경로입니다: $dest" }
                    }
                    StompCommand.SEND -> throw IllegalArgumentException("메시지 전송은 REST API 를 사용합니다.")
                    else -> Unit
                }
                return message
            }
        })
    }
}
