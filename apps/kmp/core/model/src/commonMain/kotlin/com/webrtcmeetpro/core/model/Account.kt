package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Plan(
    val code: String,
    val name: String,
    val description: String? = null,
    val monthlyPrice: Long = 0,
    /** 더 이상 쓰지 않음 (워크스페이스는 몇 개든 만들 수 있다) */
    val maxOwnedTeams: Int = 0,
    val maxMembersPerTeam: Int,
    /** null = 제한 없음 */
    val maxGroupRoomsPerTeam: Int? = null,
    val recordingEnabled: Boolean = false,
    val yearlyPrice: Long = 0,
    val maxMeetingParticipants: Int = 0,
    val recordingStorageGb: Int = 0,
    val recordingRetentionDays: Int = 0,
    val rank: Int = 0,
)

/** GET /api/users/me */
@Serializable
data class Me(
    val id: Long,
    val email: String,
    val nickname: String,
    val realName: String? = null,
    val countryCode: String? = null,
    val profileImageUrl: String? = null,
    /** false 면 웹에서 온보딩(추가 정보 입력)이 필요 */
    val profileCompleted: Boolean = true,
    val hasPassword: Boolean = true,
    /** false 면 이메일 가입 후 인증 전 → "이메일을 확인해 주세요" 화면 */
    val emailVerified: Boolean = true,
    val linkedProviders: List<String> = emptyList(),
    val plan: Plan,
)

/** POST /api/users/me/password. 비밀번호가 없는(구글 전용) 계정은 currentPassword 없이 */
@Serializable
data class ChangePasswordRequest(val currentPassword: String? = null, val newPassword: String)

/** POST /api/users/me/withdraw. 비밀번호가 있는 계정만 password 를 보낸다 */
@Serializable
data class WithdrawRequest(val password: String? = null)

/** 아직 확인하지 않은 워크스페이스 플랜 만료 → "플랜이 만료됐어요" 창 (GET /api/subscriptions/notices) */
@Serializable
data class ExpiryNotice(
    val subscriptionId: Long,
    val plan: Plan,
    val expiredAt: String? = null,
    val teamId: Long? = null,
    val teamName: String? = null,
    /** 그 워크스페이스에 지금 적용 중인 플랜 */
    val currentPlan: Plan? = null,
)


