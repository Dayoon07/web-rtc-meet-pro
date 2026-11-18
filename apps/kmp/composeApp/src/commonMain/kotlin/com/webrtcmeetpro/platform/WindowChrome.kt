package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 데스크톱 창의 타이틀 바를 앱 테마 색에 맞춘다. 앱 설정이 다크인데 Windows 가 라이트면
 * 흰 타이틀 바가 어두운 화면 위에 따로 놀아서다. Android 는 할 일이 없다.
 */
@Composable
expect fun WindowChromeColors(caption: Color, text: Color, border: Color, dark: Boolean)

/** 창 맨 위에 앱이 그린 타이틀 바(MQ 로고 포함)가 있는지. 있으면 왼쪽 메뉴의 로고는 빼서 두 번 보이지 않게 한다 */
val LocalHasTitleBar = staticCompositionLocalOf { false }
