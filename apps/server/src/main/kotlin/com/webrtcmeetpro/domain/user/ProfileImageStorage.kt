package com.webrtcmeetpro.domain.user

import com.webrtcmeetpro.domain.common.StoredFile
import java.io.InputStream

/** 서버 디스크든 S3 든 주소는 /uploads/profile/{이름} 으로 같다 */
interface ProfileImageStorage {
    fun store(userId: Long, extension: String, content: InputStream, size: Long): String

    /** 구글 사진처럼 우리가 만든 URL 이 아니면 아무것도 하지 않는다 */
    fun deleteIfOwned(url: String)

    /** 경로 조작을 막으려고 이상한 이름이면 null */
    fun open(fileName: String): StoredFile?

    companion object {
        const val URL_PREFIX = "/uploads/profile/"
        /** u{userId}-{UUID}.{확장자} */
        val FILE_NAME = Regex("""^u\d+-[0-9a-f-]{36}\.(jpg|png|webp|gif)$""")
    }
}
