package com.dororok.keyboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

/**
 * 도로록 브랜드 컬러.
 * 노랑 = 다이얼/스티커의 경쾌함, 잉크블랙 = 손맛의 단단함.
 */
private val DororokYellow = Color(0xFFFFD400)
private val DororokInk = Color(0xFF111111)
private val DororokPaper = Color(0xFFFAFAF7)

private val LightColors = lightColorScheme(
    primary = DororokYellow,
    onPrimary = DororokInk,
    secondary = Color(0xFF3A3A36),
    onSecondary = Color.White,
    background = DororokPaper,
    onBackground = DororokInk,
    surface = Color(0xFFE9E9E3),
    onSurface = DororokInk,
    error = Color(0xFFD63B2F),
)

private val DarkColors = darkColorScheme(
    primary = DororokYellow,
    onPrimary = DororokInk,
    secondary = Color(0xFFD8D8D0),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2EC),
    surface = Color(0xFF1E1E1C),
    onSurface = Color(0xFFF2F2EC),
    error = Color(0xFFFF6B5E),
)

private val DororokTypography = Typography(
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun DororokTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DororokTypography,
        content = content,
    )
}
