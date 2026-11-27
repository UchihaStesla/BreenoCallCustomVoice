package com.custom.breenocallvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.custom.breenocallvoice.config.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun AppTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val schemeMode = when (themeMode) {
        ThemeMode.SYSTEM -> ColorSchemeMode.System
        ThemeMode.LIGHT -> ColorSchemeMode.Light
        ThemeMode.DARK -> ColorSchemeMode.Dark
        ThemeMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
    }
    val controller = remember(schemeMode) {
        ThemeController(colorSchemeMode = schemeMode)
    }
    MiuixTheme(
        controller = controller,
        content = content,
    )
}

@Composable
fun isThemeDark(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM, ThemeMode.MONET_SYSTEM -> isSystemInDarkTheme()
}
