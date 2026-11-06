package com.webrtcmeetpro.infrastructure.storage

import com.webrtcmeetpro.config.properties.StorageProperties
import com.webrtcmeetpro.domain.user.ProfileImageStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@Component
class LocalProfileImageStorage(storageProperties: StorageProperties) : ProfileImageStorage {

    private val log = LoggerFactory.getLogger(javaClass)
    private val dir: Path = Path.of(storageProperties.uploadDir, "profile").toAbsolutePath().normalize()

    init {
        Files.createDirectories(dir)
    }

    override fun store(userId: Long, extension: String, content: InputStream): String {
        val filename = "u$userId-${UUID.randomUUID()}.$extension"
        Files.copy(content, dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING)
        return "$URL_PREFIX$filename"
    }

    override fun deleteIfOwned(url: String) {
        if (!url.startsWith(URL_PREFIX)) return
        val target = dir.resolve(url.removePrefix(URL_PREFIX)).normalize()
        // 경로 조작(../) 방지
        if (target.parent != dir) return
        runCatching { Files.deleteIfExists(target) }
            .onFailure { log.warn("프로필 이미지 삭제 실패: {}", target, it) }
    }

    companion object {
        const val URL_PREFIX = "/uploads/profile/"
    }
}
