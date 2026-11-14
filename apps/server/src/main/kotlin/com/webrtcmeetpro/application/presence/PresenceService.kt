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
 * 접속 상태 (서버 메모리). 실시간 연결(STOMP) 하나 = 세션 하나.
 * 사람마다 세션이 하나라도 있으면 접속 중이고, 모든 세션이 "자리 비움" 이면 자리 비움.
 * 상태가 바뀌면 같은 워크스페이스 사람들에게 /user/queue/presence 로 알린다.
 * (서버가 한 대라서 메모리로 충분하다. 여러 대가 되면 Redis 같은 공유 저장소로 옮긴다)
 */
@Service
class PresenceService(
    private val teamMembers: TeamMemberRepository,
    private val teamAccess: TeamAccess,
    private val realtime: RealtimePublisher,
) {
    /** userId → (세션 id → 그 세션이 자리 비움인지) */
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

    /** 화면이 알려 주는 자리 비움 / 돌아옴 (그 사람의 모든 세션에 적용 — 세션 id 를 화면이 모르기 때문) */
    fun setAway(userId: Long, away: Boolean) = changing(userId) {
        sessions[userId]?.replaceAll { _, _ -> away }
    }

    /** 워크스페이스 사람들 중 접속 중이거나 자리 비움인 사람 */
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

    /** 나와 같은 워크스페이스에 있는 사람들에게 (나 포함: 다른 기기 화면도 맞춘다) */
    private fun broadcast(userId: Long, status: PresenceStatus) {
        val teams = teamMembers.findAllByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE).map { it.teamId }
        val audience = teams.flatMap { teamAccess.activeMemberIds(it) }.toSet()
        realtime.push(audience, RealtimePublisher.PRESENCE, PresenceResDto(userId, status))
    }
}
