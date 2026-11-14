package com.webrtcmeetpro.infrastructure.chat

import com.webrtcmeetpro.domain.chat.ChatQueryRepository
import org.apache.ibatis.annotations.Mapper

@Mapper
interface ChatMapper : ChatQueryRepository
