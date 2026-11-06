package com.webrtcmeetpro.chat.infrastructure

import com.webrtcmeetpro.chat.domain.ChatMessage
import com.webrtcmeetpro.chat.domain.ChatMessageRepository
import com.webrtcmeetpro.chat.domain.ChatRoom
import com.webrtcmeetpro.chat.domain.ChatRoomMember
import com.webrtcmeetpro.chat.domain.ChatRoomMemberRepository
import com.webrtcmeetpro.chat.domain.ChatRoomRepository
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomJpaRepository : JpaRepository<ChatRoom, Long>, ChatRoomRepository

interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMember, Long>, ChatRoomMemberRepository

interface ChatMessageJpaRepository : JpaRepository<ChatMessage, Long>, ChatMessageRepository
