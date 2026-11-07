package com.webrtcmeetpro.infrastructure.storage

import com.webrtcmeetpro.config.properties.StorageProperties
import com.webrtcmeetpro.domain.chat.ChatFileStorage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import com.webrtcmeetpro.domain.common.ServeAs
import com.webrtcmeetpro.domain.common.StoredFile
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.YearMonth
import java.util.UUID

/**
 * 채팅 첨부파일을 서버 디스크(app.storage.chat-file-dir)에 저장한다.
 * /uploads 처럼 정적 경로로 공개하지 않고, 서명된 주소(/api/chat/files/{id})로만 내려준다.
 * 저장 이름은 "년/월/UUID" — 원래 파일 이름·확장자는 DB 에만 둔다 (서버에서 실행되거나 해석될 일이 없게).
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = ["type"], havingValue = "local", matchIfMissing = true)
class LocalChatFileStorage(storageProperties: StorageProperties) : ChatFileStorage {

    private val log = LoggerFactory.getLogger(javaClass)
    private val root: Path = Path.of(storageProperties.chatFileDir).toAbsolutePath().normalize()

    init {
        Files.createDirectories(root)
    }

    override fun store(content: InputStream, size: Long): String {
        val name = newName()
        val target = resolve(name) ?: error("잘못된 저장 이름: $name")
        Files.createDirectories(target.parent)
        Files.copy(content, target)
        return name
    }

    override fun open(storedName: String, serveAs: ServeAs): StoredFile? =
        resolve(storedName)?.takeIf { Files.isRegularFile(it) }?.let(StoredFile::Local)

    override fun delete(storedName: String) {
        val target = resolve(storedName) ?: return
        runCatching { Files.deleteIfExists(target) }
            .onFailure { log.warn("채팅 첨부파일 삭제 실패: {}", target, it) }
    }

    /** 우리가 만든 형식의 이름만, 저장 폴더 밖으로 나가지 않게 (경로 조작 방지) */
    private fun resolve(storedName: String): Path? {
        if (!STORED_NAME.matches(storedName)) return null
        return root.resolve(storedName).normalize().takeIf { it.startsWith(root) }
    }

    companion object {
        val STORED_NAME = Regex("^\\d{4}/\\d{2}/[0-9a-f]{32}$")

        fun newName(): String {
            val month = YearMonth.now()
            return "%04d/%02d/%s".format(month.year, month.monthValue, UUID.randomUUID().toString().replace("-", ""))
        }
    }
}
