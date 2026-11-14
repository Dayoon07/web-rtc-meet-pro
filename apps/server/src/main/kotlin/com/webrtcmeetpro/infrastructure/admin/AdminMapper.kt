package com.webrtcmeetpro.infrastructure.admin

import com.webrtcmeetpro.domain.admin.AdminQueryRepository
import org.apache.ibatis.annotations.Mapper

@Mapper
interface AdminMapper : AdminQueryRepository
