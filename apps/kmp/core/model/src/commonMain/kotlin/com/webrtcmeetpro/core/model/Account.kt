package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Plan(
    val code: String,
    val name: String,
    val description: String? = null,
    val monthlyPrice: Long = 0,
    val maxOwnedTeams: Int,
    val maxMembersPerTeam: Int,
    val maxGroupRoomsPerTeam: Int,
    val recordingEnabled: Boolean = false,
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

/** GET /api/subscriptions/me */
@Serializable
data class Subscription(
    val plan: Plan,
    val status: String,
    val ownedTeams: Long,
)
