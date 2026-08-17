package com.utub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// docs/03 1절 스타일: 유튜브 계열 다크/라이트
private val Accent = Color(0xFFFF0033)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFF272727),
    onBackground = Color(0xFFF1F1F1),
    onSurface = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFFAAAAAA),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF2F2F2),
    onBackground = Color(0xFF0F0F0F),
    onSurface = Color(0xFF0F0F0F),
    onSurfaceVariant = Color(0xFF606060),
)

@Composable
fun UTubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
