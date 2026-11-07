package com.webrtcmeetpro.domain.user

import com.webrtcmeetpro.domain.common.StoredFile
import java.io.InputStream

/** 프로필 이미지 저장소 포트 (서버 디스크 또는 S3). 주소는 둘 다 /uploads/profile/{이름} */
interface ProfileImageStorage {
    /** @return 클라이언트가 그대로 쓸 수 있는 URL (예: /uploads/profile/xxx.png) */
    fun store(userId: Long, extension: String, content: InputStream, size: Long): String

    /** 우리 저장소가 만든 URL 이 아니면(구글 사진 등) 아무것도 하지 않는다 */
    fun deleteIfOwned(url: String)

    /** /uploads/profile/{fileName} 요청. 없거나 이상한 이름이면 null */
    fun open(fileName: String): StoredFile?

    companion object {
        const val URL_PREFIX = "/uploads/profile/"
        /** 저장소가 만드는 이름: u{userId}-{UUID}.{확장자} */
        val FILE_NAME = Regex("""^u\d+-[0-9a-f-]{36}\.(jpg|png|webp|gif)$""")
    }
}
