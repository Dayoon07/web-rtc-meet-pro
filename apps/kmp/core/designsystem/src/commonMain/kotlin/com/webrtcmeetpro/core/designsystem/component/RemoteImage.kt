package com.webrtcmeetpro.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/**
 * 서버 이미지 (채팅 첨부 사진 등). 불러오는 동안은 회색 칸, 실패하면 깨진 이미지 아이콘.
 * @param placeholderSize 불러오는 동안·실패했을 때 칸 크기. 원래 비율로 보여 줄 이미지는 불러오기 전엔 크기를 모른다
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholderSize: DpSize = DpSize(200.dp, 150.dp),
) {
    @Composable
    fun Placeholder(failed: Boolean) {
        Box(
            Modifier.size(placeholderSize).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (failed) Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { Placeholder(failed = false) },
        error = { Placeholder(failed = true) },
    )
}
