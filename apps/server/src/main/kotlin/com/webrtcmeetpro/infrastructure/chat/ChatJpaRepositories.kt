package com.webrtcmeetpro.infrastructure.chat

import com.webrtcmeetpro.domain.chat.ChatCategory
import com.webrtcmeetpro.domain.chat.ChatCategoryRepository
import com.webrtcmeetpro.domain.chat.ChatMessage
import com.webrtcmeetpro.domain.chat.ChatMessageFile
import com.webrtcmeetpro.domain.chat.ChatMessageFileRepository
import com.webrtcmeetpro.domain.chat.ChatMessageReaction
import com.webrtcmeetpro.domain.chat.ChatMessageReactionRepository
import com.webrtcmeetpro.domain.chat.ChatMessageRepository
import com.webrtcmeetpro.domain.chat.ChatRoom
import com.webrtcmeetpro.domain.chat.ChatRoomMember
import com.webrtcmeetpro.domain.chat.ChatRoomMemberRepository
import com.webrtcmeetpro.domain.chat.ChatRoomRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChatRoomJpaRepository : JpaRepository<ChatRoom, Long>, ChatRoomRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.id = :id")
    override fun lockRoomById(@Param("id") id: Long): ChatRoom?
}

interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMember, Long>, ChatRoomMemberRepository

interface ChatMessageJpaRepository : JpaRepository<ChatMessage, Long>, ChatMessageRepository {
    /** Oracle: CLOB 도 LOWER·LIKE 가 된다. 첨부파일 이름으로도 찾는다 */
    @Query(
        value = """
            SELECT m.* FROM chat_messages m
             WHERE m.room_id IN (:roomIds) AND m.deleted_yn = 'N' AND m.message_type IN ('TEXT', 'FILE')
               AND (LOWER(m.content) LIKE :pattern ESCAPE '\'
                    OR EXISTS (SELECT 1 FROM chat_message_files f
                                WHERE f.message_id = m.message_id AND f.deleted_yn = 'N' AND LOWER(f.original_name) LIKE :pattern ESCAPE '\'))
             ORDER BY m.message_id DESC
             FETCH FIRST :limit ROWS ONLY
        """,
        nativeQuery = true,
    )
    override fun search(@Param("roomIds") roomIds: Collection<Long>, @Param("pattern") pattern: String, @Param("limit") limit: Int): List<ChatMessage>
}

interface ChatMessageReactionJpaRepository : JpaRepository<ChatMessageReaction, Long>, ChatMessageReactionRepository

interface ChatMessageFileJpaRepository : JpaRepository<ChatMessageFile, Long>, ChatMessageFileRepository

interface ChatCategoryJpaRepository : JpaRepository<ChatCategory, Long>, ChatCategoryRepository
