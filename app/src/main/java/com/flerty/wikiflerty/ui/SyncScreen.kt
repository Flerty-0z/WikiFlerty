package com.flerty.wikiflerty.ui

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.sync.CloudSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SyncContent() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val cloud = app.cloud
    val status by cloud.status.collectAsStateWithLifecycle()
    val state by app.sync.store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var entering by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun begin(code: String, existing: Boolean) {
        busy = true
        scope.launch {
            cloud.start(code, existing)
                .onSuccess { toast(if (existing) "Подключено. ${it.describe()}" else "Синхронизация включена") }
                .onFailure { toast(it.message ?: "Не получилось") }
            busy = false
        }
    }

    Crossfade(status.enabled) { on ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!on) {
                Text(
                    "Прогресс, заметки, закладки, словарь, статистика и настройки — одинаковые на всех твоих устройствах. Без регистрации, данные зашифрованы.",
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SyncButton("Включить", "Новый код", AppIcons.Cloud, Modifier.weight(1f), filled = true, enabled = !busy) {
                        begin(cloud.newCode(), existing = false)
                    }
                    SyncButton("Ввести код", "С другого телефона", AppIcons.Lock, Modifier.weight(1f), filled = false, enabled = !busy) {
                        entering = true
                    }
                }
                Appear(busy) {
                    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                        Text("Подключаю…", color = AppColors.Muted, fontSize = 13.sp)
                    }
                }
            } else {
                CodeCard(status.code.orEmpty())
                StatusRow(status) { scope.launch { cloud.syncNow() } }
                SettingsGroup(null) {
                    SettingSwitch("Переносить настройки", state.syncSettings, hint = "Оформление и опции чтения", icon = AppIcons.Options) { v ->
                        app.sync.store.update { it.copy(syncSettings = v) }
                    }
                }
                Text(
                    "На другом телефоне: Настройки → Синхронизация → «Ввести код». Книги добавь туда сам — прогресс подтянется автоматически.",
                    color = AppColors.Faint,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                val shape = appCorner(14)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .liveClick(shape, sound = FeedbackSound.Close) { confirmOff = true }
                        .border(1.dp, AppColors.Bad.copy(alpha = 0.4f), shape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(AppIcons.Close, null, tint = AppColors.Bad, modifier = Modifier.size(18.dp))
                    Text("Отключить на этом телефоне", color = AppColors.Bad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }

    if (entering) {
        TextInputSheet(
            title = "Код синхронизации",
            initial = "",
            hint = "XXXXX-XXXXX-…",
            onDismiss = { entering = false },
            icon = AppIcons.Lock,
            maxLength = 40,
            code = true,
        ) { input ->
            val code = cloud.normalize(input)
            if (code == null) toast("В коде должно быть 25 символов") else begin(code, existing = true)
        }
    }

    if (confirmOff) {
        ConfirmSheet(
            title = "Отключить синхронизацию?",
            text = "Данные на этом телефоне останутся. Чтобы подключиться снова, понадобится код.",
            confirm = "Отключить",
            onConfirm = { cloud.stop() },
            onDismiss = { confirmOff = false },
        )
    }
}

@Composable
private fun <T> Crossfade(target: T, content: @Composable (T) -> Unit) {
    androidx.compose.animation.Crossfade(target, animationSpec = Motion.fade(260), label = "sync") { content(it) }
}

@Composable
private fun CodeCard(code: String) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(20)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingIcon(AppIcons.Lock)
            Text("Твой код", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Text(
            code,
            color = AppColors.Accent,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 19.sp, stepSize = 0.5.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Сохрани его — без кода данные на новом телефоне не восстановить",
            color = AppColors.Faint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Копировать", primary = true, modifier = Modifier.weight(1f)) {
                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                clip?.setPrimaryClip(android.content.ClipData.newPlainText("WikiFlerty", code))
                Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
            }
            AppButton("Поделиться", primary = false, modifier = Modifier.weight(1f)) {
                shareText(context, "Код синхронизации WikiFlerty: $code", "Код синхронизации WikiFlerty")
            }
        }
    }
}

@Composable
private fun StatusRow(status: CloudSync.Status, onSync: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().fieldSurface(appCorner(16)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingIcon(AppIcons.Cloud)
        Column(Modifier.weight(1f)) {
            val title = when {
                status.running -> "Синхронизирую…"
                status.error != null -> "Не получилось"
                status.lastSync > 0 -> "Синхронизировано"
                else -> "Ещё не было"
            }
            val subtitle = when {
                status.running -> "Секунду"
                status.error != null -> status.error
                status.lastSync > 0 -> SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru")).format(Date(status.lastSync))
                else -> "Начнётся автоматически"
            }
            Text(title, color = if (status.error != null && !status.running) AppColors.Bad else AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle.orEmpty(), color = AppColors.Faint, fontSize = 12.sp, maxLines = 2)
        }
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (status.running) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.Accent)
            } else {
                Box(
                    Modifier.size(40.dp).liveClick(androidx.compose.foundation.shape.CircleShape, sound = FeedbackSound.Toggle, onClick = onSync),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Rotate, "Синхронизировать", tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun SyncButton(
    title: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = appCorner(16)
    Column(
        modifier
            .heightIn(min = 76.dp)
            .liveClick(shape, sound = FeedbackSound.Open, enabled = enabled, onClick = onClick)
            .then(if (filled) Modifier.accentHighlight(shape) else Modifier.border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = if (filled) accentContent else AppColors.Accent, modifier = Modifier.size(22.dp))
        Text(title, color = if (filled) accentContent else AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(hint, color = if (filled) accentContent.copy(alpha = 0.75f) else AppColors.Muted, fontSize = 11.5.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}
