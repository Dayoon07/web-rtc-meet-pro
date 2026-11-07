package com.webrtcmeetpro.infrastructure.storage

import com.webrtcmeetpro.config.properties.StorageProperties
import com.webrtcmeetpro.domain.meeting.RecordingStorage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import com.webrtcmeetpro.domain.common.ServeAs
import com.webrtcmeetpro.domain.common.StoredFile
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.YearMonth
import java.util.UUID

/**
 * 회의 녹화 파일을 서버 디스크(app.storage.recording-dir)에 저장한다. 공개 경로가 아니다 (서명된 주소로만 재생·내려받기).
 * 주최자 브라우저가 10초마다 보내는 조각(webm)을 순서대로 이어 붙이면 그대로 재생되는 파일이 된다.
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = ["type"], havingValue = "local", matchIfMissing = true)
class LocalRecordingStorage(storageProperties: StorageProperties) : RecordingStorage {

    override val needsUpload = false

    private val log = LoggerFactory.getLogger(javaClass)
    private val root: Path = Path.of(storageProperties.recordingDir).toAbsolutePath().normalize()

    init {
        Files.createDirectories(root)
    }

    override fun create(): String {
        val month = YearMonth.now()
        val name = "%04d/%02d/%s.webm".format(month.year, month.monthValue, UUID.randomUUID().toString().replace("-", ""))
        val target = resolve(name) ?: error("잘못된 저장 이름: $name")
        Files.createDirectories(target.parent)
        Files.createFile(target)
        return name
    }

    override fun append(storedName: String, content: InputStream): Long {
        val target = resolve(storedName) ?: error("잘못된 저장 이름: $storedName")
        Files.newOutputStream(target, StandardOpenOption.APPEND).use { out -> return content.copyTo(out) }
    }

    override fun upload(storedName: String) = Unit

    override fun open(storedName: String, serveAs: ServeAs): StoredFile? = path(storedName)?.takeIf { Files.isRegularFile(it) }?.let(StoredFile::Local)

    fun path(storedName: String): Path? = resolve(storedName)

    override fun delete(storedName: String) {
        val target = resolve(storedName) ?: return
        runCatching { Files.deleteIfExists(target) }.onFailure { log.warn("녹화 파일 삭제 실패: {}", target, it) }
    }

    /** 우리가 만든 형식의 이름만, 저장 폴더 밖으로 나가지 않게 (경로 조작 방지) */
    private fun resolve(storedName: String): Path? {
        if (!STORED_NAME.matches(storedName)) return null
        return root.resolve(storedName).normalize().takeIf { it.startsWith(root) }
    }

    companion object {
        val STORED_NAME = Regex("^\\d{4}/\\d{2}/[0-9a-f]{32}\\.webm$")
    }
}
