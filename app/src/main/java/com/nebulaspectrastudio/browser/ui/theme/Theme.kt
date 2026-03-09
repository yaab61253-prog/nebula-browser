package com.nebulaspectrastudio.browser.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple = Color(0xFF7C3AED)
val PurpleLight = Color(0xFFA855F7)
val PurpleDark = Color(0xFF5B21B6)
val BgDark = Color(0xFF0B0B12)
val Surface = Color(0xFF13121E)
val Surface2 = Color(0xFF1F1F2E)
val Border = Color(0xFF2A1F5A)
val TextPrimary = Color(0xFFE5E7EB)
val TextHint = Color(0xFF6B7280)

private val DarkColorScheme = darkColorScheme(
    primary = Purple,
    secondary = PurpleLight,
    tertiary = PurpleDark,
    background = BgDark,
    surface = Surface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun BrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
