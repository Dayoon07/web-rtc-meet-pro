package com.webrtcmeetpro.domain.user

interface UserRepository {
    fun save(user: User): User
    fun findUserById(id: Long): User?
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findAllByIdIn(ids: Collection<Long>): List<User>
}
