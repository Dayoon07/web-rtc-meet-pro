package com.webrtcmeetpro.presentation.user

import com.webrtcmeetpro.domain.user.ProfileImageStorage
import com.webrtcmeetpro.presentation.common.StoredFileResponses
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * 프로필 이미지 (공개). 저장 이름에 UUID 가 들어가서 바뀌지 않으므로 오래 캐시한다.
 * 서버 디스크면 파일을, S3 면 서명된 주소로 보낸다.
 */
@RestController
class ProfileImageController(private val storage: ProfileImageStorage) {

    @GetMapping("/uploads/profile/{fileName}")
    fun image(@PathVariable fileName: String): ResponseEntity<Resource> {
        val file = storage.open(fileName) ?: return ResponseEntity.notFound().build()
        return StoredFileResponses.of(file, 3600) {
            it.contentType(MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
        }
    }
}
