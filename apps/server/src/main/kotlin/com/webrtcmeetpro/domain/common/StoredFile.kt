package com.webrtcmeetpro.domain.common

import java.nio.file.Path

/** 서버 디스크면 직접 보내고, S3 면 서버가 파일을 중계하지 않게 잠깐 쓸 수 있는 주소로 보낸다 */
sealed interface StoredFile {
    data class Local(val path: Path) : StoredFile

    data class Redirect(val url: String) : StoredFile
}

/** attachment = 저장 대화상자, 아니면 바로 보기 */
data class ServeAs(val fileName: String, val contentType: String, val attachment: Boolean)
