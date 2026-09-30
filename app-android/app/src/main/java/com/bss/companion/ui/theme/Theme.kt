package com.bss.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * App chỉ dùng 1 theme tối duy nhất (HUD ban đêm) nên không cần nhánh
 * dynamicColor / light scheme như template mặc định.
 */
private val BssDarkColorScheme = darkColorScheme(
    primary = BssAccent,
    onPrimary = BssBackground,
    background = BssBackground,
    onBackground = BssOnSurface,
    surface = BssSurface,
    onSurface = BssOnSurface,
    surfaceVariant = BssSurfaceVariant,
    onSurfaceVariant = BssMuted,
    outline = BssOutline,
    error = UrgencyCriticalColor,
)

@Composable
fun BssTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BssDarkColorScheme,
        typography = BssTypography,
        content = content,
    )
}
