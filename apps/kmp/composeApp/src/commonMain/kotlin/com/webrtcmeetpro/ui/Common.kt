package com.webrtcmeetpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.app_name
import com.webrtcmeetpro.resources.common_retry
import com.webrtcmeetpro.resources.error_INVALID_CREDENTIALS
import com.webrtcmeetpro.resources.error_NETWORK
import com.webrtcmeetpro.resources.error_SOCIAL_ACCOUNT_ONLY
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
        "NETWORK" -> Res.string.error_NETWORK
        else -> Res.string.error_UNKNOWN
    },
)

@Composable
fun ErrorState(code: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = errorMessage(code),
        modifier = modifier,
        action = { SecondaryButton(stringResource(Res.string.common_retry), onClick = onRetry) },
    )
}

/** 보라 사각형 + 카메라 아이콘 + "Limeet Pro" (웹 헤더 로고와 같은 모양) */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, size: Dp = 32.dp, showName: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        }
        if (showName) {
            Spacer(Modifier.width(10.dp))
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** 화면 제목 + 부제 */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
