package com.webrtcmeetpro.domain

import com.webrtcmeetpro.auth.domain.RefreshToken
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.user.application.ImageUpload
import com.webrtcmeetpro.user.application.UserService
import com.webrtcmeetpro.user.domain.User
import com.webrtcmeetpro.user.domain.UserProfile
import java.time.Year
import com.webrtcmeetpro.plan.domain.Payment
import com.webrtcmeetpro.plan.domain.PaymentStatus
import com.webrtcmeetpro.plan.domain.Plan
import com.webrtcmeetpro.team.domain.InvitationStatus
import com.webrtcmeetpro.team.domain.TeamInvitation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainRulesTest {

    private fun plan(teams: Int, members: Int, channels: Int) = Plan(
        code = "T", name = "t", description = null, monthlyPrice = 0,
        maxOwnedTeams = teams, maxMembersPerTeam = members, maxGroupRoomsPerTeam = channels,
        recordingEnabled = false,
    )

    @Test
    fun `플랜 한도는 현재 개수가 최대치 미만일 때만 허용`() {
        val free = plan(teams = 1, members = 10, channels = 1)
        assertTrue(free.canOwnMoreTeams(0))
        assertFalse(free.canOwnMoreTeams(1))
        assertTrue(free.canAddMember(9))
        assertFalse(free.canAddMember(10))
        assertFalse(free.canAddGroupRoom(1))
    }

    @Test
    fun `만료되거나 폐기된 리프레시 토큰은 사용할 수 없다`() {
        val now = LocalDateTime.now()
        val token = RefreshToken(userId = 1, tokenHash = "h", expiresAt = now.plusDays(1))
        assertTrue(token.isUsable(now))
        assertFalse(token.isUsable(now.plusDays(2)))
        token.revoke(now)
        assertFalse(token.isUsable(now))
    }

    @Test
    fun `초대는 대기 중일 때 한 번만 응답할 수 있다`() {
        val now = LocalDateTime.now()
        val invitation = TeamInvitation(
            teamId = 1, inviterId = 1, inviteeEmail = "a@b.c", inviteeId = null,
            token = "t", expiresAt = now.plusDays(7),
        )
        invitation.accept(now)
        assertEquals(InvitationStatus.ACCEPTED, invitation.status)
        assertThrows<IllegalStateException> { invitation.decline(now) }
    }

    private fun profile(phone: String = "+821012345678", country: String = "KR", birthYear: Int = 1995) =
        UserProfile(nickname = "닉", realName = "홍길동", phoneNumber = phone, countryCode = country, birthYear = birthYear, gender = null)

    @Test
    fun `프로필은 E164 전화번호, ISO 국가코드, 만 14세 이상만 허용`() {
        profile() // ok
        assertThrows<BusinessException> { profile(phone = "010-1234-5678") }
        assertThrows<BusinessException> { profile(country = "ZZ") }
        assertThrows<BusinessException> { profile(birthYear = Year.now().value - 5) }
        assertEquals("+821012345678", UserProfile.normalizePhone("+82 10-1234-5678"))
    }

    @Test
    fun `구글 가입자는 추가 정보와 약관 동의 전까지 프로필 미완성`() {
        val user = User.registerSocial("a@b.com", "Kim", "https://pic")
        assertFalse(user.hasPassword)
        assertFalse(user.isProfileCompleted)
        user.updateProfile(profile())
        assertFalse(user.isProfileCompleted)
        user.agreeTerms(marketing = false)
        assertTrue(user.isProfileCompleted)
    }

    @Test
    fun `이미지 형식은 Content-Type 이 아니라 매직 바이트로 판별`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
        assertEquals("png", UserService.detectImageExtension(ImageUpload("text/plain", png)))
        assertEquals(null, UserService.detectImageExtension(ImageUpload("image/png", "<svg>".toByteArray())))
    }

    @Test
    fun `결제는 READY 에서만 승인된다`() {
        val payment = Payment(userId = 1, planId = 1, orderId = "o", orderName = "n", amount = 0)
        payment.approve(paymentKey = null, method = "FREE", receiptUrl = null)
        assertEquals(PaymentStatus.DONE, payment.status)
        assertThrows<IllegalStateException> { payment.approve(null, null, null) }
    }
}
