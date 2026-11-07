package com.webrtcmeetpro.infrastructure.storage

import com.webrtcmeetpro.config.properties.StorageProperties
import com.webrtcmeetpro.domain.chat.ChatFileStorage
import com.webrtcmeetpro.domain.common.ServeAs
import com.webrtcmeetpro.domain.common.StoredFile
import com.webrtcmeetpro.domain.meeting.RecordingStorage
import com.webrtcmeetpro.domain.user.ProfileImageStorage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID

/*
 * app.storage.type=s3 일 때의 저장소. 버킷 안 위치:
 *   {prefix}profile/{u아이디-UUID.확장자}
 *   {prefix}chat-files/{년}/{월}/{UUID}
 *   {prefix}recordings/{년}/{월}/{UUID}.webm
 * DB 에 넣는 이름(stored_name, storage_url, profile_image_url)은 서버 디스크를 쓸 때와 같다.
 */

@Component
@ConditionalOnProperty(prefix = "app.storage", name = ["type"], havingValue = "s3")
class S3ProfileImageStorage(private val s3: S3ObjectStore) : ProfileImageStorage {

    override fun store(userId: Long, extension: String, content: InputStream, size: Long): String {
        val name = "u$userId-${UUID.randomUUID()}.$extension"
        s3.put(KEY + name, content, size, CONTENT_TYPES[extension])
        return ProfileImageStorage.URL_PREFIX + name
    }

    override fun deleteIfOwned(url: String) {
        val name = url.removePrefix(ProfileImageStorage.URL_PREFIX).takeIf { url.startsWith(ProfileImageStorage.URL_PREFIX) } ?: return
        if (ProfileImageStorage.FILE_NAME.matches(name)) s3.delete(KEY + name)
    }

    override fun open(fileName: String): StoredFile? =
        if (ProfileImageStorage.FILE_NAME.matches(fileName)) StoredFile.Redirect(s3.presignGet(KEY + fileName)) else null

    companion object {
        private const val KEY = "profile/"
        val CONTENT_TYPES = mapOf("jpg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp", "gif" to "image/gif")
    }
}

@Component
@ConditionalOnProperty(prefix = "app.storage", name = ["type"], havingValue = "s3")
class S3ChatFileStorage(private val s3: S3ObjectStore) : ChatFileStorage {

    override fun store(content: InputStream, size: Long): String {
        val name = LocalChatFileStorage.newName()
        s3.put(KEY + name, content, size)
        return name
    }

    override fun open(storedName: String, serveAs: ServeAs): StoredFile? =
        if (LocalChatFileStorage.STORED_NAME.matches(storedName)) StoredFile.Redirect(s3.presignGet(KEY + storedName, serveAs)) else null

    override fun delete(storedName: String) {
        if (LocalChatFileStorage.STORED_NAME.matches(storedName)) s3.delete(KEY + storedName)
    }

    companion object {
        private const val KEY = "chat-files/"
    }
}

/** 녹화 중에는 서버 디스크(recording-dir)에 모으고, 끝나면 S3 에 올린 뒤 디스크 파일을 지운다 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = ["type"], havingValue = "s3")
class S3RecordingStorage(private val s3: S3ObjectStore, props: StorageProperties) : RecordingStorage {

    private val spool = LocalRecordingStorage(props)

    override val needsUpload = true

    override fun create(): String = spool.create()

    override fun append(storedName: String, content: InputStream): Long = spool.append(storedName, content)

    override fun upload(storedName: String) {
        val file = spool.path(storedName) ?: return // 이미 올리고 지웠다
        if (Files.isRegularFile(file)) {
            s3.putFile(KEY + storedName, file, "video/webm")
            spool.delete(storedName)
        }
    }

    override fun open(storedName: String, serveAs: ServeAs): StoredFile? =
        if (LocalRecordingStorage.STORED_NAME.matches(storedName)) StoredFile.Redirect(s3.presignGet(KEY + storedName, serveAs)) else null

    override fun delete(storedName: String) {
        if (!LocalRecordingStorage.STORED_NAME.matches(storedName)) return
        spool.delete(storedName)
        s3.delete(KEY + storedName)
    }

    companion object {
        private const val KEY = "recordings/"
    }
}
