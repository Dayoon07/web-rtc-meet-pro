package com.webrtcmeetpro.infrastructure.chat

import com.webrtcmeetpro.domain.chat.ChatMessage
import com.webrtcmeetpro.domain.chat.ChatMessageRepository
import com.webrtcmeetpro.domain.chat.ChatRoom
import com.webrtcmeetpro.domain.chat.ChatRoomMember
import com.webrtcmeetpro.domain.chat.ChatRoomMemberRepository
import com.webrtcmeetpro.domain.chat.ChatRoomRepository
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomJpaRepository : JpaRepository<ChatRoom, Long>, ChatRoomRepository

interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMember, Long>, ChatRoomMemberRepository

interface ChatMessageJpaRepository : JpaRepository<ChatMessage, Long>, ChatMessageRepository
