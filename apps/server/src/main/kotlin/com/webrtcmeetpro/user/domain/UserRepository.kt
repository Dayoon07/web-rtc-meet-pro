package com.webrtcmeetpro.user.domain

/** 도메인 포트. 구현은 infrastructure 의 Spring Data 리포지토리가 맡는다. */
interface UserRepository {
    fun save(user: User): User
    fun findUserById(id: Long): User?
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findAllByIdIn(ids: Collection<Long>): List<User>
}
