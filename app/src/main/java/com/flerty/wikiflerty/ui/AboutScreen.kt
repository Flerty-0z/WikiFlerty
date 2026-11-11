package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
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

private val Good get() = AppColors.Good
private val Bad get() = AppColors.Bad

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
            .revealScroll()
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

        var support by remember { mutableStateOf(false) }
        SupportTile { support = true }
        if (support) SupportSheet(app) { support = false }

        run {
            SettingsGroup("Обновления") {
                UpdatePanel(app, Modifier.padding(vertical = 8.dp))
                SettingSwitch(
                    label = "Проверять автоматически",
                    checked = auto,
                    hint = "При запуске, не чаще раза в 6 часов",
                    icon = AppIcons.Clock,
                ) { app.updates.setAuto(it) }
            }
        }

        SettingsGroup("Журнал сбоев") {
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

        SettingsGroup(null) {
            SettingsNavRow(AppIcons.Lock, "Политика конфиденциальности", "Какие данные хранятся и куда передаются", framed = false) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_URL))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
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
    val scope = rememberCoroutineScope()
    val state by app.updates.state.collectAsStateWithLifecycle()
    val where = if (com.flerty.wikiflerty.system.Updates.IN_RUSTORE) "RuStore" else "Telegram-канале"

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val (title, subtitle, color) = when (val s = state) {
            UpdateState.Idle -> Triple("Проверить обновления", "Узнать, вышла ли новая версия", AppColors.Text)
            UpdateState.Checking -> Triple("Проверяю…", "Секунду", AppColors.Text)
            UpdateState.UpToDate -> Triple("Установлена последняя версия", "Обновлений нет", Good)
            is UpdateState.Available -> Triple("Вышла версия ${s.info.version}", "Скачай её в $where", AppColors.Accent)
            is UpdateState.Failed -> Triple("Не получилось", s.message, Bad)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingIcon(AppIcons.Rotate)
            Column(Modifier.weight(1f)) {
                Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = AppColors.Faint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (state is UpdateState.Checking) {
                com.flerty.wikiflerty.ui.KitSpinner(Modifier.size(18.dp), AppColors.Accent)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Failed -> {
                    AppButton("Проверить", primary = true, modifier = Modifier.weight(1f)) {
                        scope.launch { app.updates.check() }
                    }
                }
                UpdateState.Checking -> Unit
                is UpdateState.Available -> {
                    if (com.flerty.wikiflerty.system.Updates.IN_RUSTORE) {
                        AppButton("RuStore", primary = true, modifier = Modifier.weight(1f)) {
                            app.updates.open(com.flerty.wikiflerty.system.Updates.RUSTORE)
                        }
                    }
                    AppButton("Telegram", primary = !com.flerty.wikiflerty.system.Updates.IN_RUSTORE, modifier = Modifier.weight(1f)) {
                        app.updates.open(com.flerty.wikiflerty.system.Updates.TELEGRAM)
                    }
                }
            }
            if (onLater != null) {
                AppButton("Позже", primary = false, modifier = Modifier.weight(0.6f), onClick = onLater)
            }
        }
    }
}

@Composable
fun AppButton(text: String, primary: Boolean, modifier: Modifier = Modifier, labeled: Boolean = false, onClick: () -> Unit) {
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
        val icon = actionIcon(text)
        val tint = if (primary) AppColors.OnAccent else AppColors.Accent
        if (labeled) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
                FitText(text, tint, 14.sp, weight = FontWeight.SemiBold)
            }
        } else if (icon != null) {
            ActionLabel(text, tint, fontSize = 14.sp, iconSize = 21.dp)
        } else {
            FitText(text, if (primary) AppColors.OnAccent else AppColors.Accent, 14.sp, weight = FontWeight.SemiBold)
        }
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

    val offered = (state as? UpdateState.Available)?.info?.code
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

const val PRIVACY_URL = "https://github.com/Flerty-0z/WikiFlerty/blob/main/PRIVACY.md"

const val DONATE_URL = "https://www.donationalerts.com/r/f1erty0z"

@Composable
private fun SupportTile(onClick: () -> Unit) {
    val shape = appCorner(20)
    val beat = androidx.compose.animation.core.rememberInfiniteTransition(label = "support")
    val pulse by beat.animateFloat<Float>(
        1f,
        1.12f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "support-pulse",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.18f).compositeOver(AppColors.Field), AppColors.Field)), shape)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.3f), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(appCorner(13))
                .background(AppColors.Accent.copy(alpha = 0.22f))
                .border(1.dp, AppColors.Accent.copy(alpha = 0.3f), appCorner(13)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.Heart,
                null,
                tint = AppColors.Accent,
                modifier = Modifier.size(22.dp).graphicsLayer { if (Motion.enabled) { scaleX = pulse; scaleY = pulse } },
            )
        }
        Column(Modifier.weight(1f)) {
            Text("Поддержать WikiFlerty", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Если приложение стало родным", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SupportSheet(app: App, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppSheet(onDismiss, title = "Поддержать") {
        val close = sheetClose(onDismiss)
        Text(
            "WikiFlerty бесплатная и такой останется. Если хочется сказать спасибо — вот как можно помочь",
            color = AppColors.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsNavRow(AppIcons.Heart, "Поддержать автора", "Любая сумма через DonationAlerts") {
                app.updates.open(DONATE_URL)
                close()
            }
            SettingsNavRow(AppIcons.Share, "Рассказать другу", "Поделиться ссылкой на канал") {
                shareText(context, "Попробуй WikiFlerty — читалка для книг: ${com.flerty.wikiflerty.system.Updates.TELEGRAM}", "WikiFlerty")
                close()
            }
            SettingsNavRow(AppIcons.Star, "Оценить в RuStore", "Отзыв помогает другим найти приложение") {
                app.updates.open(com.flerty.wikiflerty.system.Updates.RUSTORE)
                close()
            }
        }
    }
}
