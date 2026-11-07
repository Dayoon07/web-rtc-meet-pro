package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable

/**
 * POST /api/client-errors — 앱에서 난 예상하지 못한 오류 신고 (admin_error_logs, source = APP).
 * errorCode 는 앱이 만든다 (ERR-YYYYMMDD-16진수 20자리). 서버에 연결이 안 돼도 사용자에게 바로 보여 주려고.
 *
 * @param category CLIENT(앱 코드 오류) 또는 NETWORK
 * @param path 어느 앱에서 났는지 (예: app/android, app/windows)
 */
@Serializable
data class ClientErrorRequest(
    val errorCode: String,
    val source: String = "APP",
    val category: String = "CLIENT",
    val name: String? = null,
    val message: String? = null,
    val stack: String? = null,
    val path: String? = null,
)
