package com.webrtcmeetpro.application.chat

import com.webrtcmeetpro.application.plan.TestIds
import com.webrtcmeetpro.domain.chat.ChatFileStorage
import com.webrtcmeetpro.domain.chat.ChatMessageFile
import com.webrtcmeetpro.domain.chat.ChatMessageFileRepository
import com.webrtcmeetpro.domain.common.ServeAs
import com.webrtcmeetpro.domain.common.StoredFile
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.io.InputStream
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 어떤 파일이 "지운 메시지·방의 파일" 인지는 JPA 쿼리가 정하므로, 여기서는 지우는 순서와 실패했을 때를 본다 */
class ChatFileCleanupServiceTest {

    private val now = LocalDateTime.of(2026, 11, 21, 4, 30)
    private val files = mutableListOf<ChatMessageFile>()
    /** 파일 id → 메시지나 방을 지운 시각 */
    private val orphanedAt = mutableMapOf<Long, LocalDateTime>()

    private val repo = object : ChatMessageFileRepository {
        override fun save(file: ChatMessageFile) = TestIds.assign(file).also { if (it !in files) files += it }
        override fun findFileById(id: Long) = files.find { it.id == id }
        override fun findAllByMessageIdIn(messageIds: Collection<Long>) = files.filter { it.messageId in messageIds }
        override fun findOrphanedBefore(before: LocalDateTime) =
            files.filter { !it.deleted && orphanedAt[it.id]?.isBefore(before) == true }
    }

    private val removed = mutableListOf<String>()
    private var failFor: String? = null
    private val storage = object : ChatFileStorage {
        override fun store(content: InputStream, size: Long) = error("쓰지 않음")
        override fun open(storedName: String, serveAs: ServeAs): StoredFile? = null
        override fun delete(storedName: String) {
            if (storedName == failFor) error("저장소 오류")
            removed += storedName
        }
    }
    private val noTx = object : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) {}
        override fun rollback(status: TransactionStatus) {}
    }
    private val cleanup = ChatFileCleanupService(repo, storage, noTx)

    private fun file(name: String, deletedAt: LocalDateTime?) =
        repo.save(ChatMessageFile(messageId = 1, roomId = 1, originalName = "$name.png", storedName = name, contentType = "image/png", sizeBytes = 10))
            .also { f -> deletedAt?.let { orphanedAt[f.id!!] = it } }

    @Test
    fun `지운 지 하루가 지난 메시지의 파일만 저장소에서 지우고 지운 것으로 표시한다`() {
        val old = file("old", now.minusDays(2))
        val fresh = file("fresh", now.minusHours(1))
        val alive = file("alive", null)

        assertEquals(1, cleanup.purgeDeleted(now))
        assertEquals(listOf("old"), removed)
        assertTrue(old.deleted)
        assertFalse(fresh.deleted, "막 지운 것은 다운로드 중일 수 있어 하루 기다린다")
        assertFalse(alive.deleted)

        assertEquals(0, cleanup.purgeDeleted(now), "이미 정리한 것은 다시 지우지 않는다")
    }

    @Test
    fun `저장소에서 못 지우면 표시하지 않고 다음 날 다시 한다`() {
        val a = file("a", now.minusDays(2))
        val b = file("b", now.minusDays(2))
        failFor = "a"

        assertEquals(1, cleanup.purgeDeleted(now), "하나가 실패해도 나머지는 지운다")
        assertFalse(a.deleted)
        assertTrue(b.deleted)

        failFor = null
        assertEquals(1, cleanup.purgeDeleted(now.plusDays(1)))
        assertTrue(a.deleted)
    }
}
