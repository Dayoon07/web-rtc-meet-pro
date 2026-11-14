package com.webrtcmeetpro.presentation.common

import com.webrtcmeetpro.domain.common.StoredFile
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.net.URI
import java.time.Duration

/** S3 는 302 로 서명된 주소를 알려 준다. 주소가 몇 시간 유효하니 리다이렉트도 잠깐 캐시한다 */
object StoredFileResponses {

    fun of(file: StoredFile, maxAgeSeconds: Long, local: (ResponseEntity.BodyBuilder) -> ResponseEntity.BodyBuilder): ResponseEntity<Resource> =
        when (file) {
            is StoredFile.Local -> local(ResponseEntity.ok()).body(FileSystemResource(file.path))
            is StoredFile.Redirect -> ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(file.url))
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(maxAgeSeconds.coerceIn(0, REDIRECT_MAX_AGE))).cachePrivate())
                .build()
        }

    private const val REDIRECT_MAX_AGE = 3600L
}
