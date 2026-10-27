package com.flerty.wikiflerty.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** App colors. The app is dark by default; a light theme will come with the settings screen. */
object AppColors {
    val Background = Color(0xFF101113)
    val Panel = Color(0xFF1F2125)
    val PanelBorder = Color(0xFF2C2F34)
    val Field = Color(0xFF16171A)
    val Line = Color(0xFF33363C)
    val Text = Color(0xFFECEDEF)
    val Muted = Color(0xFFA3A7AF)
    val Faint = Color(0xFF8A8E96)
    val Accent = Color(0xFF8FB3FF)
    val OnAccent = Color(0xFF0D1424)
    val AccentSoft = Color(0x2E8FB3FF)
}

private val DarkColors = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    primaryContainer = Color(0xFF26324A),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = AppColors.Accent,
    secondaryContainer = Color(0xFF26324A),
    onSecondaryContainer = Color(0xFFD9E4FF),
    background = AppColors.Background,
    onBackground = AppColors.Text,
    surface = AppColors.Background,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Panel,
    onSurfaceVariant = AppColors.Muted,
    surfaceContainerLowest = AppColors.Background,
    surfaceContainerLow = Color(0xFF17181B),
    surfaceContainer = AppColors.Panel,
    surfaceContainerHigh = Color(0xFF25282C),
    surfaceContainerHighest = Color(0xFF2C2F34),
    outline = AppColors.Line,
    outlineVariant = AppColors.PanelBorder,
)

@Composable
fun WikiFlertyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
