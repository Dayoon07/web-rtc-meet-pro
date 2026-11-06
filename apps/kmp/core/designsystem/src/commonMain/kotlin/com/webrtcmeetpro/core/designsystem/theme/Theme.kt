package com.webrtcmeetpro.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors: ColorScheme = lightColorScheme(
    primary = Violet.V600,
    onPrimary = Color.White,
    primaryContainer = Violet.V100,
    onPrimaryContainer = Violet.V800,
    secondary = Violet.V500,
    onSecondary = Color.White,
    secondaryContainer = Violet.V50,
    onSecondaryContainer = Violet.V700,
    background = Color.White,
    onBackground = Zinc.Z900,
    surface = Color.White,
    onSurface = Zinc.Z900,
    surfaceVariant = Zinc.Z100,
    onSurfaceVariant = Zinc.Z500,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Zinc.Z50,
    surfaceContainer = Zinc.Z50,
    surfaceContainerHigh = Zinc.Z100,
    surfaceContainerHighest = Zinc.Z200,
    outline = Zinc.Z300,
    outlineVariant = Zinc.Z200,
    error = Danger,
    onError = Color.White,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Violet.V400,
    onPrimary = Violet.V950,
    primaryContainer = Violet.V900,
    onPrimaryContainer = Violet.V100,
    secondary = Violet.V300,
    onSecondary = Violet.V950,
    secondaryContainer = Color(0xFF231A3D),
    onSecondaryContainer = Violet.V200,
    background = Zinc.Z950,
    onBackground = Zinc.Z100,
    surface = Zinc.Z950,
    onSurface = Zinc.Z100,
    surfaceVariant = Zinc.Z800,
    onSurfaceVariant = Zinc.Z400,
    surfaceContainerLowest = Zinc.Z950,
    surfaceContainerLow = Zinc.Z900,
    surfaceContainer = Zinc.Z900,
    surfaceContainerHigh = Zinc.Z800,
    surfaceContainerHighest = Zinc.Z700,
    outline = Zinc.Z700,
    outlineVariant = Zinc.Z800,
    error = DangerDark,
    onError = Zinc.Z950,
)

private val LimeetShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** 흰 배경 + 보라 포인트. darkTheme 은 설정(시스템/라이트/다크)에 따라 앱이 정한다 */
@Composable
fun LimeetTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = LimeetShapes,
        content = content,
    )
}
