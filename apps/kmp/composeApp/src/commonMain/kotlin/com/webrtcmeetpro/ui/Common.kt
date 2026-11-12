package com.webrtcmeetpro.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import com.webrtcmeetpro.resources.error_MEETING_NOT_EDITABLE
import com.webrtcmeetpro.resources.error_INVITE_LINK_NOT_FOUND
import com.webrtcmeetpro.resources.error_INVITE_LINK_PASSWORD_REQUIRED
import com.webrtcmeetpro.resources.error_INVITE_LINK_WRONG_PASSWORD
import com.webrtcmeetpro.resources.error_INVALID_INVITE_LINK_PASSWORD
import com.webrtcmeetpro.resources.error_REFUND_ALREADY_DONE
import com.webrtcmeetpro.resources.error_REFUND_NOT_LATEST
import com.webrtcmeetpro.resources.error_REFUND_PLAN_ENDED
import com.webrtcmeetpro.resources.error_REFUND_NOTHING_LEFT
import com.webrtcmeetpro.resources.error_REFUND_ASK_SUPPORT
import com.webrtcmeetpro.resources.error_REFUND_FAILED
import com.webrtcmeetpro.resources.error_REFUND_NOT_ALLOWED
import com.webrtcmeetpro.resources.error_MESSAGE_NOT_FOUND
import com.webrtcmeetpro.resources.error_TOO_MANY_FILES
import com.webrtcmeetpro.resources.error_FILE_TOO_LARGE
import com.webrtcmeetpro.resources.error_EMPTY_FILE
import com.webrtcmeetpro.resources.error_OWNER_CANNOT_LEAVE
import com.webrtcmeetpro.resources.error_EMAIL_NOT_VERIFIED
import com.webrtcmeetpro.resources.error_RESEND_TOO_SOON
import com.webrtcmeetpro.resources.error_CANNOT_REMOVE_MEMBER
import com.webrtcmeetpro.resources.error_INVALID_PASSWORD
import com.webrtcmeetpro.resources.error_OWNED_TEAMS_EXIST
import com.webrtcmeetpro.resources.error_BLOCKED_FILE_TYPE
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.app_name
import com.webrtcmeetpro.resources.common_back
import com.webrtcmeetpro.resources.common_retry
import com.webrtcmeetpro.resources.error_ALREADY_TEAM_MEMBER
import com.webrtcmeetpro.resources.error_CANNOT_INVITE_SELF
import com.webrtcmeetpro.resources.error_EMAIL_ALREADY_EXISTS
import com.webrtcmeetpro.resources.error_FORBIDDEN
import com.webrtcmeetpro.resources.error_INVALID_CREDENTIALS
import com.webrtcmeetpro.resources.error_INVALID_INPUT
import com.webrtcmeetpro.resources.error_INVALID_PHONE
import com.webrtcmeetpro.resources.error_INVITATION_ALREADY_PENDING
import com.webrtcmeetpro.resources.error_MEETING_NOT_JOINABLE
import com.webrtcmeetpro.resources.error_MEETING_TIME_IN_PAST
import com.webrtcmeetpro.resources.error_NETWORK
import com.webrtcmeetpro.resources.error_PLAN_GROUP_ROOM_LIMIT
import com.webrtcmeetpro.resources.error_PLAN_MEMBER_LIMIT
import com.webrtcmeetpro.resources.error_PLAN_TEAM_LIMIT
import com.webrtcmeetpro.resources.error_SOCIAL_ACCOUNT_ONLY
import com.webrtcmeetpro.resources.error_UNDERAGE
import com.webrtcmeetpro.resources.error_UNKNOWN
import com.webrtcmeetpro.resources.error_USER_NOT_ACTIVE
import org.jetbrains.compose.resources.stringResource

/** 화면 데이터 로딩 상태. 새로고침 중에도 이전 data 는 그대로 보여 준다 */
data class Loadable<T>(
    val data: T? = null,
    val loading: Boolean = true,
    val errorCode: String? = null,
)

/** 서버/네트워크 에러 코드 → 현재 언어 문구. 번역이 없는 코드는 일반 문구 */
@Composable
fun errorMessage(code: String): String = stringResource(
    when (code) {
        "INVALID_CREDENTIALS" -> Res.string.error_INVALID_CREDENTIALS
        "USER_NOT_ACTIVE" -> Res.string.error_USER_NOT_ACTIVE
        "SOCIAL_ACCOUNT_ONLY" -> Res.string.error_SOCIAL_ACCOUNT_ONLY
        "EMAIL_ALREADY_EXISTS" -> Res.string.error_EMAIL_ALREADY_EXISTS
        "UNDERAGE" -> Res.string.error_UNDERAGE
        "INVALID_PHONE" -> Res.string.error_INVALID_PHONE
        "INVALID_INPUT" -> Res.string.error_INVALID_INPUT
        "FORBIDDEN", "NOT_TEAM_OWNER", "CANNOT_INVITE", "NOT_MEETING_HOST" -> Res.string.error_FORBIDDEN
        "PLAN_TEAM_LIMIT" -> Res.string.error_PLAN_TEAM_LIMIT
        "PLAN_MEMBER_LIMIT" -> Res.string.error_PLAN_MEMBER_LIMIT
        "PLAN_GROUP_ROOM_LIMIT" -> Res.string.error_PLAN_GROUP_ROOM_LIMIT
        "CANNOT_INVITE_SELF" -> Res.string.error_CANNOT_INVITE_SELF
        "ALREADY_TEAM_MEMBER" -> Res.string.error_ALREADY_TEAM_MEMBER
        "INVITATION_ALREADY_PENDING" -> Res.string.error_INVITATION_ALREADY_PENDING
        "MEETING_TIME_IN_PAST" -> Res.string.error_MEETING_TIME_IN_PAST
        "MEETING_NOT_JOINABLE", "MEETING_NOT_STARTED_YET" -> Res.string.error_MEETING_NOT_JOINABLE
        "TOO_MANY_FILES" -> Res.string.error_TOO_MANY_FILES
        "FILE_TOO_LARGE" -> Res.string.error_FILE_TOO_LARGE
        "BLOCKED_FILE_TYPE" -> Res.string.error_BLOCKED_FILE_TYPE
        "EMPTY_FILE" -> Res.string.error_EMPTY_FILE
        "OWNER_CANNOT_LEAVE" -> Res.string.error_OWNER_CANNOT_LEAVE
        "EMAIL_NOT_VERIFIED" -> Res.string.error_EMAIL_NOT_VERIFIED
        "NOT_MESSAGE_SENDER", "CANNOT_DELETE_MESSAGE" -> Res.string.error_FORBIDDEN
        "MEETING_NOT_EDITABLE" -> Res.string.error_MEETING_NOT_EDITABLE
        "RESEND_TOO_SOON" -> Res.string.error_RESEND_TOO_SOON
        "CANNOT_REMOVE_MEMBER" -> Res.string.error_CANNOT_REMOVE_MEMBER
        "INVALID_PASSWORD" -> Res.string.error_INVALID_PASSWORD
        "OWNED_TEAMS_EXIST" -> Res.string.error_OWNED_TEAMS_EXIST
        "INVITE_LINK_NOT_FOUND" -> Res.string.error_INVITE_LINK_NOT_FOUND
        "INVITE_LINK_PASSWORD_REQUIRED" -> Res.string.error_INVITE_LINK_PASSWORD_REQUIRED
        "INVITE_LINK_WRONG_PASSWORD" -> Res.string.error_INVITE_LINK_WRONG_PASSWORD
        "INVALID_INVITE_LINK_PASSWORD" -> Res.string.error_INVALID_INVITE_LINK_PASSWORD
        "REFUND_ALREADY_DONE" -> Res.string.error_REFUND_ALREADY_DONE
        "REFUND_NOT_LATEST" -> Res.string.error_REFUND_NOT_LATEST
        "REFUND_PLAN_ENDED" -> Res.string.error_REFUND_PLAN_ENDED
        "REFUND_NOTHING_LEFT" -> Res.string.error_REFUND_NOTHING_LEFT
        "REFUND_ASK_SUPPORT", "REFUND_NO_PAYMENT_KEY" -> Res.string.error_REFUND_ASK_SUPPORT
        "REFUND_FAILED" -> Res.string.error_REFUND_FAILED
        "REFUND_NOT_ALLOWED", "REFUND_NOT_PAID", "INVALID_REFUND_AMOUNT" -> Res.string.error_REFUND_NOT_ALLOWED
        "MESSAGE_NOT_FOUND" -> Res.string.error_MESSAGE_NOT_FOUND
        "NETWORK" -> Res.string.error_NETWORK
        else -> Res.string.error_UNKNOWN
    },
)

/** 서버의 예상하지 못한 에러(500) 코드. UnexpectedErrorHost 가 에러 코드와 함께 안내한다 */
private const val SERVER_ERROR_CODE = "INTERNAL_ERROR"
private const val UNKNOWN_ERROR_CODE = "UNKNOWN"

/** MainScreen 의 Scaffold 스낵바. 하위 화면이 저장/실패 결과를 잠깐 보여 줄 때 쓴다 */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** ViewModel 이 실패 코드를 내면 스낵바로 한 번 보여 주고 지운다 */
@Composable
fun ErrorSnackbarEffect(code: String?, onShown: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val message = code?.let { errorMessage(it) }
    // 코드를 지우면 이 effect 가 다시 시작되므로, 스낵바는 화면 수명의 scope 에서 띄운다
    val scope = rememberCoroutineScope()
    LaunchedEffect(code) {
        if (message != null) {
            onShown()
            // 서버 500 은 앱 루트의 에러 안내 창(에러 코드·문의 이메일)이 이미 보여 준다
            // 알 수 없는 앱 오류(UNKNOWN)도 AppErrorReporter 가 에러 코드 안내 창을 띄운다
            if (code != SERVER_ERROR_CODE && code != UNKNOWN_ERROR_CODE) scope.launch { snackbar.showSnackbar(message) }
        }
    }
}

@Composable
fun ErrorState(code: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = errorMessage(code),
        modifier = modifier,
        action = { SecondaryButton(stringResource(Res.string.common_retry), onClick = onRetry) },
    )
}

/** MQ 마크: 겹친 말풍선 두 개 (웹 MqMark 와 같은 모양, viewBox 100×92) */
val MqMark: ImageVector by lazy {
    ImageVector.Builder("MqMark", defaultWidth = 100.dp, defaultHeight = 92.dp, viewportWidth = 100f, viewportHeight = 92f).apply {
        listOf(
            "M4 24C4 11 14 1 28 1H66A12 12 0 0 1 75 21L54 41 38 30 19 48 4 56 6 43 4 42Z",
            "M26 52 38 43 54 53 80 30C86 25 98 28 98 40V56C98 64 93 70 86 74L87 90 68 77H35C18 77 14 64 20 57Z",
        ).forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
    }.build()
}

/** 보라 말풍선 마크 + "MQ" (웹 헤더 로고와 같은 모양) */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, size: Dp = 32.dp, showName: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(MqMark, contentDescription = null, tint = Color(0xFF7C1FFF), modifier = Modifier.size(size))
        if (showName) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

/** 화면 제목 + 부제 + 오른쪽 버튼 */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/**
 * 하위 화면(채팅방, 회의 상세 등) 공통 틀: 뒤로 + 제목 + 오른쪽 버튼.
 * 시스템 인셋은 바깥 MainScreen 이 이미 처리하므로 여기서는 쓰지 않는다.
 */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(Res.string.common_back))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}
