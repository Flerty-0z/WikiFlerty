package com.flerty.wikiflerty.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E4A3F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E8DC),
    onPrimaryContainer = Color(0xFF0F2119),
    secondary = Color(0xFF7A5C3E),
    secondaryContainer = Color(0xFFF1E2D0),
    background = Color(0xFFFBF8F3),
    surface = Color(0xFFFBF8F3),
    surfaceVariant = Color(0xFFECE6DC),
    surfaceContainerLow = Color(0xFFF5F1EA),
    surfaceContainer = Color(0xFFF0EBE3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9CDB8),
    onPrimary = Color(0xFF15352A),
    primaryContainer = Color(0xFF2E4A3F),
    onPrimaryContainer = Color(0xFFD5E8DC),
    secondary = Color(0xFFDDC2A4),
    secondaryContainer = Color(0xFF5A4430),
    background = Color(0xFF17191A),
    surface = Color(0xFF17191A),
    surfaceVariant = Color(0xFF2A2D2E),
    surfaceContainerLow = Color(0xFF1D2021),
    surfaceContainer = Color(0xFF222526),
)

@Composable
fun WikiFlertyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
