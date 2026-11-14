package com.webrtcmeetpro.application.presence

import com.webrtcmeetpro.domain.presence.PresenceStatus
import com.webrtcmeetpro.domain.presence.dto.res.PresenceResDto
import com.webrtcmeetpro.application.team.TeamAccess
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
import com.webrtcmeetpro.infrastructure.realtime.RealtimePublisher
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 접속 상태. STOMP 연결 하나가 세션 하나이고, 모든 세션이 자리 비움이면 자리 비움이다.
 * 서버가 한 대라 메모리로 충분하다. 여러 대가 되면 Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Service
class PresenceService(
    private val teamMembers: TeamMemberRepository,
    private val teamAccess: TeamAccess,
    private val realtime: RealtimePublisher,
) {
    /** userId → (세션 id → 자리 비움인지) */
    private val sessions = ConcurrentHashMap<Long, ConcurrentHashMap<String, Boolean>>()

    fun status(userId: Long): PresenceStatus {
        val mine = sessions[userId] ?: return PresenceStatus.OFFLINE
        if (mine.isEmpty()) return PresenceStatus.OFFLINE
        return if (mine.values.all { it }) PresenceStatus.AWAY else PresenceStatus.ONLINE
    }

    fun connected(userId: Long, sessionId: String) = changing(userId) {
        sessions.computeIfAbsent(userId) { ConcurrentHashMap() }[sessionId] = false
    }

    fun disconnected(userId: Long, sessionId: String) = changing(userId) {
        sessions[userId]?.let {
            it.remove(sessionId)
            if (it.isEmpty()) sessions.remove(userId, it)
        }
    }

    /** 화면은 세션 id 를 모르므로 그 사람의 모든 세션에 적용한다 */
    fun setAway(userId: Long, away: Boolean) = changing(userId) {
        sessions[userId]?.replaceAll { _, _ -> away }
    }

    fun team(teamId: Long, userId: Long): List<PresenceResDto> {
        teamAccess.requireMember(teamId, userId)
        return teamAccess.activeMemberIds(teamId).map { PresenceResDto(it, status(it)) }.filter { it.status != PresenceStatus.OFFLINE }
    }

    private fun changing(userId: Long, change: () -> Unit) {
        val before = status(userId)
        change()
        val after = status(userId)
        if (before != after) broadcast(userId, after)
    }

    /** 다른 기기 화면도 맞추도록 나에게도 보낸다 */
    private fun broadcast(userId: Long, status: PresenceStatus) {
        val teams = teamMembers.findAllByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE).map { it.teamId }
        val audience = teams.flatMap { teamAccess.activeMemberIds(it) }.toSet()
        realtime.push(audience, RealtimePublisher.PRESENCE, PresenceResDto(userId, status))
    }
}
