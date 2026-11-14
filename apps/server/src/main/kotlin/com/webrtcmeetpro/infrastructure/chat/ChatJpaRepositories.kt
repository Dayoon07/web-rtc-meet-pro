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

interface ChatMessageJpaRepository : JpaRepository<ChatMessage, Long>, ChatMessageRepository

interface ChatMessageReactionJpaRepository : JpaRepository<ChatMessageReaction, Long>, ChatMessageReactionRepository

interface ChatMessageFileJpaRepository : JpaRepository<ChatMessageFile, Long>, ChatMessageFileRepository

interface ChatCategoryJpaRepository : JpaRepository<ChatCategory, Long>, ChatCategoryRepository
