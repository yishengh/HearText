package com.yishenghuang.heartext.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalIsDarkTheme = staticCompositionLocalOf { false }

object AppColors {
    private val LightWindowBg = Color(0xFFFBFBFC)
    private val LightCardBg = Color.White
    private val LightTextPrimary = Color(0xFF1A1A2E)
    private val LightTextSecondary = Color(0xFF6E6E73)
    private val LightBgGray = Color(0xFFF2F2F7)
    private val LightDivider = Color(0xFFE5E5EA)

    private val DarkWindowBg = Color(0xFF000000)
    private val DarkCardBg = Color(0xFF1C1C1E)
    private val DarkTextPrimary = Color(0xFFFFFFFF)
    private val DarkTextSecondary = Color(0xFF98989D)
    private val DarkBgGray = Color(0xFF2C2C2E)
    private val DarkDivider = Color(0xFF38383A)

    val AccentPurple = Color(0xFF6C63FF)
    val AccentPurpleSoft = Color(0xFF8B85FF)
    val AccentCoral = Color(0xFFE85D5D)
    val ListenGreen = Color(0xFF34C759)

    val Accent: Color
        @Composable get() = AccentPurple

    val WindowBg: Color
        @Composable get() = if (LocalIsDarkTheme.current) DarkWindowBg else LightWindowBg

    val CardBg: Color
        @Composable get() = if (LocalIsDarkTheme.current) DarkCardBg else LightCardBg

    val TextPrimary: Color
        @Composable get() = if (LocalIsDarkTheme.current) DarkTextPrimary else LightTextPrimary

    val TextSecondary: Color
        @Composable get() = if (LocalIsDarkTheme.current) DarkTextSecondary else LightTextSecondary

    val BgGray: Color
        @Composable get() = if (LocalIsDarkTheme.current) DarkBgGray else LightBgGray

    val Divider: Color
        @Composable get() = if (LocalIsDarkTheme.current) DarkDivider else LightDivider
}

object AppType {
    val Display = 32.sp
    val Title = 28.sp
    val Section = 20.sp
    val Body = 16.sp
    val BodySmall = 14.sp
    val Caption = 12.sp
    val Huge = 36.sp
}

object AppSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object AppRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val full = 999.dp
    val capsule = 28.dp
    val cover = 8.dp
}

/** Bottom inset so scroll content clears the floating tab bar. */
val MainTabBarInset = 96.dp
