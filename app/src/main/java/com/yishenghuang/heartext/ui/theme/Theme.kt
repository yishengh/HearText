package com.yishenghuang.heartext.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = HearPurple,
    onPrimary = HearOnPurple,
    primaryContainer = HearPurpleSoft,
    secondary = HearPurpleDark,
    background = Color(0xFFFBFBFC),
    onBackground = HearTextPrimary,
    surface = Color.White,
    onSurface = HearTextPrimary,
    onSurfaceVariant = HearTextSecondary,
    outline = Color(0xFFE5E5EA)
)

private val DarkColorScheme = darkColorScheme(
    primary = HearPurpleSoft,
    onPrimary = Color.White,
    primaryContainer = HearPurpleDark,
    secondary = HearPurple,
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF98989D)
)

@Composable
fun HearTextTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
