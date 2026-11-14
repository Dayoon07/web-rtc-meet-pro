package com.webrtcmeetpro.domain

import com.webrtcmeetpro.application.admin.ErrorClassifier
import com.webrtcmeetpro.application.chat.ChatService
import com.webrtcmeetpro.domain.admin.AdminErrorLog
import com.webrtcmeetpro.domain.admin.ErrorCategory
import com.webrtcmeetpro.domain.team.TeamMember
import com.webrtcmeetpro.domain.team.TeamPermission
import com.webrtcmeetpro.domain.team.TeamRole
import com.webrtcmeetpro.common.error.BusinessException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessAndErrorRulesTest {

    @Test
    fun `팀 권한은 역할별 권한 표 한 곳에서 정한다`() {
        // OWNER 는 모든 권한 (권한이 새로 생기면 자동 포함)
        assertEquals(TeamPermission.entries.toSet(), TeamRole.OWNER.permissions)
        assertTrue(TeamRole.ADMIN.can(TeamPermission.INVITE_MEMBER))
        assertTrue(TeamRole.ADMIN.can(TeamPermission.CANCEL_ANY_MEETING))
        assertTrue(TeamRole.ADMIN.can(TeamPermission.CREATE_GROUP_ROOM)) // 관리자도 채널·카테고리를 관리
        assertFalse(TeamRole.ADMIN.can(TeamPermission.DELETE_TEAM))
        assertTrue(TeamRole.MEMBER.permissions.isEmpty())
    }

    @Test
    fun `팀을 나간 사람은 역할이 남아 있어도 권한이 없다`() {
        val owner = TeamMember(teamId = 1, userId = 2, role = TeamRole.OWNER)
        assertTrue(owner.can(TeamPermission.INVITE_MEMBER))
        owner.leave()
        assertFalse(owner.can(TeamPermission.INVITE_MEMBER))
    }

    @Test
    fun `OWNER 역할은 주거나 바꿀 수 없고 ADMIN 지정·해제만 된다`() {
        val member = TeamMember(teamId = 1, userId = 3, role = TeamRole.MEMBER)
        member.changeRole(TeamRole.ADMIN)
        assertTrue(member.can(TeamPermission.INVITE_MEMBER))
        member.changeRole(TeamRole.MEMBER)
        assertFalse(member.can(TeamPermission.INVITE_MEMBER))
        assertThrows<BusinessException> { member.changeRole(TeamRole.OWNER) }
        val owner = TeamMember(teamId = 1, userId = 2, role = TeamRole.OWNER)
        assertThrows<BusinessException> { owner.changeRole(TeamRole.MEMBER) }
        // 역할 바꾸기 권한은 OWNER 만
        assertTrue(TeamRole.OWNER.can(TeamPermission.MANAGE_ROLES))
        assertFalse(TeamRole.ADMIN.can(TeamPermission.MANAGE_ROLES))
    }

    @Test
    fun `에러 코드는 ERR-날짜8자리-UUID 20자리`() {
        val code = AdminErrorLog.newErrorCode(LocalDate.of(2026, 11, 7))
        assertTrue(code.startsWith("ERR-20261107-"))
        assertTrue(AdminErrorLog.ERROR_CODE.matches(code))
        assertEquals(33, code.length)
    }

    @Test
    fun `에러 원인은 ORA 번호로 구조·규칙·연결 문제를 나눈다`() {
        assertEquals(ErrorCategory.DB_SCHEMA, ErrorClassifier.classify(RuntimeException(SQLException("ORA-00942", "42000", 942))))
        assertEquals(ErrorCategory.DB_SCHEMA, ErrorClassifier.classify(RuntimeException(SQLException("ORA-12899", "72000", 12899))))
        assertEquals(ErrorCategory.DB_CONSTRAINT, ErrorClassifier.classify(RuntimeException(SQLException("ORA-00001", "23000", 1))))
        assertEquals(ErrorCategory.DB_CONNECTION, ErrorClassifier.classify(SQLRecoverableException("IO Error", "08006", 17002)))
        assertEquals(ErrorCategory.CODE, ErrorClassifier.classify(IllegalStateException("bug")))
    }

    @Test
    fun `처리 시간 초과는 잘못된 쿼리 예외로 감싸져 와도 DB 구조 문제가 아니라 연결·시간 초과로 본다`() {
        val wrapped = org.springframework.dao.InvalidDataAccessResourceUsageException("Transaction timed out: deadline was Sat Nov 14 02:40:22 GMT+09:00 2026; Bad SELECT ...")
        assertEquals(ErrorCategory.DB_CONNECTION, ErrorClassifier.classify(wrapped))
        assertEquals(ErrorCategory.DB_CONNECTION, ErrorClassifier.classify(RuntimeException(SQLException("ORA-01013", "72000", 1013))))
        // 진짜 구조 문제는 그대로
        assertEquals(ErrorCategory.DB_SCHEMA, ErrorClassifier.classify(org.springframework.dao.InvalidDataAccessResourceUsageException("ORA-00942: table does not exist")))
    }

    @Test
    fun `첨부파일 이름은 경로·위험 문자를 지우고, 이미지 여부는 매직 바이트로 판별`() {
        assertEquals("a.png", ChatService.cleanFileName("C:\\fakepath\\a.png"))
        assertEquals("file", ChatService.cleanFileName("  "))
        assertEquals("a_b.txt", ChatService.cleanFileName("a:b.txt"))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertEquals("image/png", ChatService.sniffImageType(png))
        // SVG 는 이미지라고 해도 미리보기하지 않는다 (스크립트를 품을 수 있음)
        assertNull(ChatService.sniffImageType("<svg xmlns=\"http://www.w3.org/2000/svg\">".toByteArray()))
    }
}
