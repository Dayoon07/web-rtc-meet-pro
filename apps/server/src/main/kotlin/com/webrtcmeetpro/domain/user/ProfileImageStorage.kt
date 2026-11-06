package com.webrtcmeetpro.domain.user

import java.io.InputStream

/** 프로필 이미지 저장소 포트. 지금은 로컬 디스크, 이후 S3 등으로 교체 가능 */
interface ProfileImageStorage {
    /** @return 클라이언트가 그대로 쓸 수 있는 URL (예: /uploads/profile/xxx.png) */
    fun store(userId: Long, extension: String, content: InputStream): String

    /** 우리 저장소가 만든 URL 이 아니면(구글 사진 등) 아무것도 하지 않는다 */
    fun deleteIfOwned(url: String)
}
