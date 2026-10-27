package com.flerty.wikiflerty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.App

/** Main settings. For now: the AI assistant. Customization of the whole interface comes next. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ИИ-помощник", color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        AiSettingsContent(app.ai)
        Text(
            "Настройки чтения (шрифты, темы, жесты) — в книге: тап по центру страницы → «Опции».",
            color = AppColors.Faint,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
