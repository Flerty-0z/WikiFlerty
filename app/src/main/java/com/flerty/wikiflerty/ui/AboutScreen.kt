package com.flerty.wikiflerty.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
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

private val RibbonTop = Color(0xFFB3412F)
private val RibbonBottom = Color(0xFF7D2418)
private val PaperTop = Color(0xFFF1E3C4)
private val PaperBottom = Color(0xFFE3CDA2)
private val PaperInk = Color(0xFF3A2614)
private val PaperEdge = Color(0xFFC7A66C)
private val SealGold = Color(0xFFF2D48A)

private fun ribbonPath(w: Float, h: Float) = androidx.compose.ui.graphics.Path().apply {
    moveTo(0f, 0f)
    lineTo(w, 0f)
    lineTo(w, h)
    lineTo(w / 2f, h * 0.8f)
    lineTo(0f, h)
    close()
}

private fun heartPath(cx: Float, cy: Float, s: Float) = androidx.compose.ui.graphics.Path().apply {
    moveTo(cx, cy + s * 0.45f)
    cubicTo(cx - s * 0.75f, cy - s * 0.05f, cx - s * 0.5f, cy - s * 0.6f, cx, cy - s * 0.25f)
    cubicTo(cx + s * 0.5f, cy - s * 0.6f, cx + s * 0.75f, cy - s * 0.05f, cx, cy + s * 0.45f)
    close()
}

@Composable
private fun SupportTile(onClick: () -> Unit) {
    val shape = appCorner(18)
    val sway = androidx.compose.animation.core.rememberInfiniteTransition(label = "ribbon")
    val angle by sway.animateFloat(
        -3f,
        3f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2200, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "ribbon-sway",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.28f), shape),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 70.dp, top = 16.dp, bottom = 16.dp)) {
            Text("Поддержать WikiFlerty", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Любая сумма — уже спасибо", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        androidx.compose.foundation.Canvas(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp)
                .size(width = 26.dp, height = 46.dp)
                .graphicsLayer {
                    translationY = -4.dp.toPx()
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    rotationZ = if (Motion.enabled) angle else 0f
                },
        ) {
            val path = ribbonPath(size.width, size.height)
            drawPath(path, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(RibbonTop, RibbonBottom)))
            drawLine(Color.Black.copy(alpha = 0.18f), Offset(size.width * 0.15f, 0f), Offset(size.width * 0.15f, size.height * 0.92f), 1.dp.toPx())
            drawPath(heartPath(size.width / 2f, size.height * 0.36f, size.width * 0.42f), SealGold)
        }
    }
}

@Composable
private fun SupportSheet(app: App, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppSheet(onDismiss, title = "Поддержать") {
        val close = sheetClose(onDismiss)
        Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            val card = appCorner(16)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(card)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(PaperTop, PaperBottom)))
                    .border(1.dp, PaperEdge, card)
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
            ) {
                Text(
                    "Привет! WikiFlerty бесплатная и без рекламы. Если она стала твоей читалкой — можешь поддержать, это правда помогает.",
                    color = PaperInk,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                Text("— Flerty", color = Color(0xFF8C5A2A), fontSize = 12.sp, modifier = Modifier.align(Alignment.End).padding(top = 10.dp))
            }
            androidx.compose.foundation.Canvas(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-8).dp, y = 14.dp)
                    .size(38.dp),
            ) {
                val r = size.minDimension / 2f
                drawCircle(Color(0xFF6B170F), r, Offset(r, r + 1.5.dp.toPx()))
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFD0503F), Color(0xFF8E2219)), center = Offset(r * 0.8f, r * 0.7f), radius = r * 1.3f), r, Offset(r, r))
                drawCircle(Color.White.copy(alpha = 0.08f), r - 3.dp.toPx(), Offset(r, r), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawPath(heartPath(r, r + r * 0.06f, r * 0.85f), Color(0xFFF6D9A0))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .liveClick(appCorner(16), sound = FeedbackSound.Open, pressedScale = 0.97f) {
                    app.updates.open(DONATE_URL)
                    close()
                }
                .background(AppColors.Accent, appCorner(16)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Heart, null, tint = AppColors.OnAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Поддержать", color = AppColors.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AppIcons.Share to {
                    shareText(context, "Попробуй WikiFlerty — читалка для книг: ${com.flerty.wikiflerty.system.Updates.TELEGRAM}", "WikiFlerty")
                    close()
                },
                AppIcons.Star to {
                    app.updates.open(com.flerty.wikiflerty.system.Updates.RUSTORE)
                    close()
                },
            ).forEach { (icon, action) ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .liveClick(appCorner(16), sound = FeedbackSound.Open, pressedScale = 0.95f, onClick = action)
                        .fieldSurface(appCorner(16)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}
