package com.webrtcmeetpro.domain.user.dto.res

import com.webrtcmeetpro.domain.plan.dto.res.PlanResDto
import com.webrtcmeetpro.domain.user.Gender

data class MeResDto(
    val id: Long,
    val email: String,
    val nickname: String,
    val realName: String?,
    val phoneNumber: String?,
    val countryCode: String?,
    val birthYear: Int?,
    val gender: Gender?,
    val profileImageUrl: String?,
    val marketingAgreed: Boolean,
    /** false 면 프론트가 온보딩(추가 정보 입력)으로 보낸다 */
    val profileCompleted: Boolean,
    val hasPassword: Boolean,
    /** false 면 프론트가 "이메일을 확인해 주세요" 화면으로 보낸다 */
    val emailVerified: Boolean,
    val linkedProviders: List<String>,
    val plan: PlanResDto,
    /** 방해 금지 시간 (HH:mm) · 시간대. 안 쓰면 null */
    val dndStart: String? = null,
    val dndEnd: String? = null,
    val dndTimeZone: String? = null,
)
