package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable

/**
 * 사용자가 고른 채팅 첨부파일.
 * 서버 한도([ChatFileLimits.MAX_FILE_BYTES])를 넘는 파일은 메모리에 읽지 않고 bytes 를 null 로 둔다 (화면이 "너무 커요" 로 안내).
 */
class PickedFile(val name: String, val mimeType: String?, val size: Long, val bytes: ByteArray?)

/** 서버 ChatService 와 같은 한도. 보내기 전에 앱에서 먼저 확인한다 */
object ChatFileLimits {
    const val MAX_FILES = 10
    const val MAX_FILE_BYTES = 20L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

    /** 받는 사람 PC 에서 바로 실행될 수 있는 형식 */
    val BLOCKED_EXTENSIONS = setOf(
        "exe", "msi", "bat", "cmd", "com", "scr", "pif", "cpl", "vbs", "vbe", "wsf", "wsh", "ps1", "hta", "lnk", "reg",
    )

    /** 문제가 있으면 에러 코드(서버와 같은 이름), 없으면 null */
    fun check(files: List<PickedFile>): String? = when {
        files.size > MAX_FILES -> "TOO_MANY_FILES"
        files.any { it.name.substringAfterLast('.', "").lowercase() in BLOCKED_EXTENSIONS } -> "BLOCKED_FILE_TYPE"
        files.any { it.size == 0L } -> "EMPTY_FILE"
        files.any { it.bytes == null || it.size > MAX_FILE_BYTES } -> "FILE_TOO_LARGE"
        files.sumOf { it.size } > MAX_TOTAL_BYTES -> "FILE_TOO_LARGE"
        else -> null
    }
}

/**
 * 파일 고르기 창(여러 개 선택)을 여는 함수를 돌려준다. 고르면 onPicked, 취소하면 아무 일도 없다.
 * Android: 시스템 파일 선택기 / Windows: 파일 열기 대화상자
 */
@Composable
expect fun rememberFilePicker(title: String, onPicked: (List<PickedFile>) -> Unit): () -> Unit
