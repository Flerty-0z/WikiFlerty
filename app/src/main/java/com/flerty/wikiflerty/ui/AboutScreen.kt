package com.flerty.wikiflerty.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.system.CrashLog
import com.flerty.wikiflerty.system.UpdateState
import kotlinx.coroutines.launch

private val Good = Color(0xFF7ED3A0)
private val Bad = Color(0xFFF08A8A)

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val auto by app.updates.auto.collectAsStateWithLifecycle()
    val crashes by app.crashLog.entries.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .fieldSurface(appCorner(20))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).clip(appCorner(16)).background(colorResource(R.color.icon_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(70.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("WikiFlerty", color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Версия $version", color = AppColors.Muted, fontSize = 13.sp)
            }
        }

        SettingsGroup("Обновления", AppIcons.Backup) {
            UpdatePanel(app, Modifier.padding(vertical = 8.dp))
            SettingSwitch(
                label = "Проверять автоматически",
                checked = auto,
                hint = "При запуске, не чаще раза в 6 часов",
                icon = AppIcons.Clock,
            ) { app.updates.setAuto(it) }
        }

        SettingsGroup("Журнал сбоев", AppIcons.Info) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (crashes.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingIcon(AppIcons.Check)
                        Text("Сбоев не было", color = AppColors.Text, fontSize = 15.sp)
                    }
                } else {
                    crashes.take(5).forEach { entry ->
                        Column {
                            Text(CrashLog.format(entry.time), color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(entry.title, color = AppColors.Faint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (crashes.size > 5) Text("И ещё ${crashes.size - 5}", color = AppColors.Faint, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton("Отправить", primary = true, modifier = Modifier.weight(1f)) {
                            app.crashLog.markSeen()
                            shareText(context, app.crashLog.report(), "Отчёт о сбоях WikiFlerty")
                        }
                        AppButton("Очистить", primary = false, modifier = Modifier.weight(1f)) { app.crashLog.clear() }
                    }
                }
                Text(
                    "Если приложение закроется с ошибкой, здесь появится запись. Отправь её разработчику — так ошибку проще исправить",
                    color = AppColors.Faint,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        Text(
            "Движок отображения книг: Readium (BSD-3)",
            color = AppColors.Faint,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
fun UpdatePanel(app: App, modifier: Modifier = Modifier, onLater: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by app.updates.state.collectAsStateWithLifecycle()
    var needsPermission by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val (title, subtitle, color) = when (val s = state) {
            UpdateState.Idle -> Triple("Проверить обновления", "Новые версии берутся с GitHub", AppColors.Text)
            UpdateState.Checking -> Triple("Проверяю…", "Секунду", AppColors.Text)
            UpdateState.UpToDate -> Triple("Установлена последняя версия", "Обновлений нет", Good)
            is UpdateState.Available -> Triple("Доступна версия ${s.info.version}", sizeText(s.info.size), AppColors.Accent)
            is UpdateState.Downloading -> Triple("Скачиваю ${s.info.version}", "${(s.progress * 100).toInt()}%", AppColors.Accent)
            is UpdateState.Ready -> Triple("Версия ${s.info.version} скачана", "Осталось установить", Good)
            is UpdateState.Failed -> Triple("Не получилось", s.message, Bad)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingIcon(AppIcons.Rotate)
            Column(Modifier.weight(1f)) {
                Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = AppColors.Faint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (state is UpdateState.Checking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.Accent)
            }
        }
        val notes = (state as? UpdateState.Available)?.info?.notes.orEmpty()
        if (notes.isNotBlank()) {
            Text(
                notes,
                color = AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(appCorner(12))
                    .background(AppColors.Background.copy(alpha = 0.4f))
                    .padding(10.dp),
            )
        }
        (state as? UpdateState.Downloading)?.let { s ->
            LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(appCorner(3)),
                color = AppColors.Accent,
                trackColor = AppColors.Line,
                drawStopIndicator = {},
            )
        }
        if (needsPermission) {
            Text(
                "Разреши WikiFlerty устанавливать приложения, вернись и нажми «Установить» ещё раз",
                color = AppColors.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val s = state) {
                UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Failed -> {
                    val retry = (s as? UpdateState.Failed)?.info
                    AppButton(if (retry != null) "Повторить" else "Проверить", primary = true, modifier = Modifier.weight(1f)) {
                        scope.launch { if (retry != null) app.updates.download(retry) else app.updates.check() }
                    }
                }
                UpdateState.Checking -> Unit
                is UpdateState.Available -> {
                    AppButton("Скачать и установить", primary = true, modifier = Modifier.weight(1f)) {
                        scope.launch {
                            app.updates.download(s.info)
                            val ready = app.updates.state.value as? UpdateState.Ready
                            if (ready != null) needsPermission = !app.updates.install(ready.file)
                        }
                    }
                }
                is UpdateState.Downloading -> Unit
                is UpdateState.Ready -> {
                    AppButton("Установить", primary = true, modifier = Modifier.weight(1f)) {
                        needsPermission = !app.updates.install(s.file)
                    }
                }
            }
            if (onLater != null && state !is UpdateState.Downloading) {
                AppButton("Позже", primary = false, modifier = Modifier.weight(0.6f), onClick = onLater)
            }
        }
    }
}

@Composable
fun AppButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = appCorner(12)
    Box(
        modifier
            .height(44.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, onClick = onClick)
            .then(
                if (primary) Modifier.background(AppColors.Accent)
                else Modifier.border(1.dp, AppColors.Accent.copy(alpha = 0.35f), shape),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) AppColors.OnAccent else AppColors.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StartupPrompts() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val state by app.updates.state.collectAsStateWithLifecycle()
    var crashPrompt by remember { mutableStateOf(app.crashLog.hasUnseen) }
    var updateDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { app.updates.autoCheck() }

    if (crashPrompt) {
        AppSheet(onDismiss = { crashPrompt = false; app.crashLog.markSeen() }, title = "Приложение закрылось с ошибкой") {
            val close = sheetClose { crashPrompt = false }
            Text(
                "В прошлый раз WikiFlerty закрылось неожиданно. Отправь отчёт разработчику — в нём только техническая информация, без книг и ключей",
                color = AppColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton("Отправить", primary = true, modifier = Modifier.weight(1f)) {
                    app.crashLog.markSeen()
                    shareText(context, app.crashLog.report(), "Отчёт о сбоях WikiFlerty")
                    close()
                }
                AppButton("Не сейчас", primary = false, modifier = Modifier.weight(1f)) {
                    app.crashLog.markSeen()
                    close()
                }
            }
        }
        return
    }

    val offered = when (val s = state) {
        is UpdateState.Available -> s.info.code
        is UpdateState.Downloading -> s.info.code
        is UpdateState.Ready -> s.info.code
        else -> null
    }
    if (offered != null && !updateDismissed && offered != app.updates.dismissedCode) {
        val dismiss = {
            updateDismissed = true
            app.updates.dismissedCode = offered
        }
        AppSheet(onDismiss = dismiss, title = "Есть обновление") {
            UpdatePanel(app, onLater = sheetClose(dismiss))
        }
    }
}

fun shareText(context: Context, text: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun sizeText(bytes: Long): String =
    if (bytes <= 0) "Нажми, чтобы скачать" else "Размер %.1f МБ".format(bytes / 1_048_576.0)
