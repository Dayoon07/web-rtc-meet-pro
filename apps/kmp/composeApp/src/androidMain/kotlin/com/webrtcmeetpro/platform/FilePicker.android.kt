package com.webrtcmeetpro.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** 시스템 파일 선택기 (모든 형식, 여러 개). 고른 파일은 IO 스레드에서 읽는다 */
@Composable
actual fun rememberFilePicker(title: String, onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { read(context, it) } }
            if (files.isNotEmpty()) latest(files)
        }
    }
    return { launcher.launch("*/*") }
}

private fun read(context: Context, uri: Uri): PickedFile? = runCatching {
    val resolver = context.contentResolver
    var name: String? = null
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            name = c.getString(0)
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    // 한도를 넘는 파일은 읽지 않는다 (크기를 모르면 한도까지만 읽어 본다)
    val bytes = if (size > ChatFileLimits.MAX_FILE_BYTES) null else resolver.openInputStream(uri)?.use { readAtMost(it, ChatFileLimits.MAX_FILE_BYTES) }
    PickedFile(
        name = name ?: uri.lastPathSegment ?: "file",
        mimeType = resolver.getType(uri),
        size = bytes?.size?.toLong() ?: maxOf(size, ChatFileLimits.MAX_FILE_BYTES + 1),
        bytes = bytes,
    )
}.getOrNull()

/** limit 바이트까지만 읽는다. 넘으면 null (InputStream.readNBytes 는 API 33 부터라 직접 읽는다) */
private fun readAtMost(input: InputStream, limit: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
