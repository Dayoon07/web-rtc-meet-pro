package com.webrtcmeetpro.domain.common

import java.nio.file.Path

/** 저장소에서 파일을 내려주는 방법: 서버 디스크면 직접 보내고, S3 면 잠깐 쓸 수 있는 주소로 보낸다 */
sealed interface StoredFile {
    data class Local(val path: Path) : StoredFile

    /** 오브젝트 스토리지의 서명된 주소 (몇 시간 뒤 만료). 응답은 302 로 이 주소를 알려준다 */
    data class Redirect(val url: String) : StoredFile
}

/** 내려받을 때 브라우저에 알려줄 이름·형식. attachment = 저장 대화상자, 아니면 바로 보기 */
data class ServeAs(val fileName: String, val contentType: String, val attachment: Boolean)
