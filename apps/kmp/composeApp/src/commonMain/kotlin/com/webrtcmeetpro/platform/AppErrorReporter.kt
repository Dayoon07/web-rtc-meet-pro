package com.webrtcmeetpro.platform

import com.webrtcmeetpro.core.model.ClientErrorRequest
import com.webrtcmeetpro.core.network.ApiJson
import com.webrtcmeetpro.core.network.LimeetApi
import com.webrtcmeetpro.core.network.UnexpectedError
import com.webrtcmeetpro.core.network.UnexpectedErrors
import com.webrtcmeetpro.core.network.UnknownErrors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** 앱이 죽기 직전에 파일에 남기는 신고 1건 (다음 실행 때 서버로 보낸다) */
@Serializable
private data class PendingError(val request: ClientErrorRequest, val crashed: Boolean = false)

/**
 * 앱 오류를 서버 에러 기록(관리자 사이트 > 에러 기록, 출처 "앱")으로 보낸다. 웹의 reportClientError 와 같은 역할.
 *
 *   - 알 수 없는 오류(서버 에러도 네트워크 끊김도 아닌 것): 바로 보내고 에러 코드 안내 창을 띄운다
 *   - 앱이 죽는 오류: 네트워크를 기다릴 수 없으니 파일에 남기고, 다음 실행 때 보내면서 안내 창으로 알려 준다
 *   - 보내다 실패하면(오프라인 등) 파일에 남겨 다음 실행 때 다시 보낸다 (최근 20건까지)
 */
object AppErrorReporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var api: LimeetApi? = null
    private var pendingFile: String? = null
    private var platform: String = "app"

    /**
     * 앱 시작 때 한 번 (Koin 을 만든 직후).
     * @param dataDir 앱 전용 저장 폴더 (Android filesDir, Windows %APPDATA%\LimeetPro)
     * @param platform 에러 기록의 "경로" 칸에 남길 이름 (예: android, windows)
     */
    fun install(api: LimeetApi, dataDir: String, platform: String) {
        this.api = api
        this.pendingFile = "$dataDir/$PENDING_FILE"
        this.platform = platform
        UnknownErrors.handler = { e -> report(e) }
        installCrashHandler { e -> save(PendingError(build(newErrorCode(), e), crashed = true)) }
        scope.launch { flushPending() }
    }

    /** 알 수 없는 오류 1건: 서버로 보내고 안내 창. @return 사용자에게 보여 준 에러 코드 */
    fun report(e: Throwable): String {
        val request = build(newErrorCode(), e)
        UnexpectedErrors.report(UnexpectedError(request.errorCode, "CLIENT"))
        scope.launch { if (!send(request)) save(PendingError(request)) }
        return request.errorCode
    }

    private suspend fun flushPending() {
        val file = pendingFile ?: return
        val pending = readAndDeleteText(file)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { runCatching { ApiJson.decodeFromString(PendingError.serializer(), it) }.getOrNull() }
            ?.toList()
            ?.takeLast(MAX_PENDING)
            .orEmpty()
        if (pending.isEmpty()) return
        // 지난번에 앱이 갑자기 꺼졌다면, 문의할 수 있게 그때의 에러 코드를 보여 준다
        pending.lastOrNull { it.crashed }?.let { crash ->
            UnexpectedErrors.awaitListener()
            UnexpectedErrors.report(UnexpectedError(crash.request.errorCode, "CLIENT"))
        }
        pending.forEach { if (!send(it.request)) save(it.copy(crashed = false)) }
    }

    private suspend fun send(request: ClientErrorRequest): Boolean {
        val api = api ?: return false
        // 신고가 실패해도 다시 신고하지 않는다 (무한 반복 방지)
        return runCatching { api.reportClientError(request) }.isSuccess
    }

    private fun save(error: PendingError) {
        val file = pendingFile ?: return
        runCatching { appendLine(file, ApiJson.encodeToString(PendingError.serializer(), error)) }
    }

    private fun build(code: String, e: Throwable) = ClientErrorRequest(
        errorCode = code,
        category = "CLIENT",
        name = e::class.qualifiedName?.take(300),
        message = e.message?.take(4000),
        stack = e.stackTraceToString().take(32_000),
        path = "app/$platform",
    )

    /** ERR-YYYYMMDD-(대문자 16진수 20자리). 서버·웹과 같은 형식 */
    fun newErrorCode(): String {
        val hex = Random.nextBytes(10).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.uppercase()
        return "ERR-${todayYmd()}-$hex"
    }

    private const val PENDING_FILE = "pending-errors.jsonl"
    private const val MAX_PENDING = 20
}

/** 오늘 날짜 yyyyMMdd (기기 시간대) */
expect fun todayYmd(): String

/** 처리되지 않은 예외로 앱이 죽기 직전에 onCrash 를 부른다 (원래 처리기도 그대로 이어서 부른다) */
expect fun installCrashHandler(onCrash: (Throwable) -> Unit)

/** 파일 끝에 한 줄 덧붙인다 (앱이 죽는 중에도 쓸 수 있게 동기로) */
expect fun appendLine(path: String, line: String)

/** 파일 내용을 읽고 지운다. 없으면 null */
expect fun readAndDeleteText(path: String): String?
