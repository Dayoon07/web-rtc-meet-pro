package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files

/** Windows 파일 열기 대화상자 (여러 개 선택). 대화상자는 모달이라 고를 때까지 기다리고, 파일은 IO 스레드에서 읽는다 */
@Composable
actual fun rememberFilePicker(title: String, onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    val currentTitle by rememberUpdatedState(title)
    return remember {
        {
            val dialog = FileDialog(null as Frame?, currentTitle, FileDialog.LOAD).apply {
                isMultipleMode = true
                isVisible = true
            }
            val chosen = dialog.files.toList()
            dialog.dispose()
            if (chosen.isNotEmpty()) {
                scope.launch { latest(withContext(Dispatchers.IO) { chosen.map { read(it, ChatFileLimits.MAX_FILE_BYTES) } }) }
            }
        }
    }
}

private fun read(file: File, limit: Long): PickedFile {
    val size = file.length()
    return PickedFile(
        name = file.name,
        mimeType = runCatching { Files.probeContentType(file.toPath()) }.getOrNull(),
        size = size,
        bytes = if (size <= limit) file.readBytes() else null,
    )
}

/** Windows 열기 대화상자는 파일 이름 패턴으로 이미지 형식만 보여 준다 */
@Composable
actual fun rememberImagePicker(title: String, onPicked: (PickedFile) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    val currentTitle by rememberUpdatedState(title)
    return remember {
        {
            val dialog = FileDialog(null as Frame?, currentTitle, FileDialog.LOAD).apply {
                file = "*.jpg;*.jpeg;*.png;*.webp;*.gif"
                isVisible = true
            }
            val chosen = dialog.files.firstOrNull()
            dialog.dispose()
            if (chosen != null) scope.launch { latest(withContext(Dispatchers.IO) { read(chosen, PROFILE_IMAGE_MAX_BYTES) }) }
        }
    }
}
