package com.webrtcmeetpro.domain.chat

/**
 * 조건이 3개 이상인 채팅 조회 (MyBatis: mapper/chat/ChatMapper.xml).
 * id 만 돌려준다: 엔티티를 고쳐야 할 때 JPA 로 다시 꺼내야 변경이 저장되기 때문이다
 */
interface ChatQueryRepository {
    fun findReactionId(messageId: Long, userId: Long, emoji: String): Long?

    /** pattern 은 이미 소문자이고 %, _ 를 이스케이프한 LIKE 패턴 */
    fun searchMessageIds(roomIds: Collection<Long>, pattern: String, limit: Int): List<Long>
}
