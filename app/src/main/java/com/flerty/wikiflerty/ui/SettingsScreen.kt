package com.flerty.wikiflerty.ui

import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.luminance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.reader.FontCatalog
import com.flerty.wikiflerty.reader.FontCategory
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsPage(val title: String) {
    Ai("ИИ-помощник"),
    Custom("Кастомизация"),
    Ink("Режим E-Ink"),
    Sound("Звук и отклик"),
    Backup("Резервная копия"),
    Sync("Синхронизация"),
    Guide("Подсказки"),
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onAbout: (() -> Unit)? = null) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    var tour by remember { mutableStateOf(false) }
    if (tour) WelcomeTour { tour = false }
    val pending = SettingsNav.open
    LaunchedEffect(pending) {
        if (pending != null) {
            page = SettingsPage.entries.firstOrNull { it.name == pending }
            SettingsNav.open = null
        }
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val deeper = targetState != null
            val dir = if (deeper) 1 else -1
            (slideInHorizontally(Motion.tween(260)) { dir * it / 5 } + fadeIn(Motion.fade(220)))
                .togetherWith(slideOutHorizontally(Motion.tween(220)) { -dir * it / 5 } + fadeOut(Motion.fade(160)))
        },
        label = "settings",
        modifier = modifier,
    ) { current ->
        if (current == SettingsPage.Custom) {
            CustomizationContent { PageHeader(current.title) { page = null } }
            return@AnimatedContent
        }
        Column(
            Modifier
                .fillMaxSize()
                .revealScroll()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current == null) {
                SettingsHome(onAbout) { page = it }
            } else {
                PageHeader(current.title) { page = null }
                when (current) {
                    SettingsPage.Ai -> AiSettingsContent((LocalContext.current.applicationContext as App).ai)
                    SettingsPage.Custom -> Unit
                    SettingsPage.Ink -> InkContent()
                    SettingsPage.Sound -> SoundContent()
                    SettingsPage.Backup -> BackupContent()
                    SettingsPage.Sync -> SyncContent()
                    SettingsPage.Guide -> GuideContent { tour = true }
                }
            }
        }
    }
}

@Composable
private fun SettingsHome(onAbout: (() -> Unit)?, onOpen: (SettingsPage) -> Unit) {
    val rows = listOfNotNull<Triple<androidx.compose.ui.graphics.vector.ImageVector, Pair<String, String>, () -> Unit>>(
        Triple(AppIcons.Help, "Подсказки" to "Все функции и где их найти") { onOpen(SettingsPage.Guide) },
        Triple(AppIcons.Sparkle, "ИИ-помощник" to "Ключи, модели, перевод") { onOpen(SettingsPage.Ai) },
        Triple(AppIcons.Theme, "Кастомизация" to "Цвета, форма, фон") { onOpen(SettingsPage.Custom) },
        Triple(AppIcons.Ereader, "Режим E-Ink" to if (EInk.on) "Включён" else "Для электронных книг") { onOpen(SettingsPage.Ink) },
        Triple(AppIcons.Sound, "Звук и отклик" to "Звуки и вибрация") { onOpen(SettingsPage.Sound) },
        Triple(AppIcons.Cloud, "Синхронизация" to "Между устройствами") { onOpen(SettingsPage.Sync) },
        Triple(AppIcons.Backup, "Резервная копия" to "Экспорт и импорт") { onOpen(SettingsPage.Backup) },
        onAbout?.let { Triple(AppIcons.Info, "О приложении" to "Обновления, журнал сбоев", it) },
    )
    NewsShowcase { SettingsNav.news = true }
    Column(Modifier.fillMaxWidth().fieldSurface(appCorner(20))) {
        rows.forEachIndexed { i, (icon, text, action) ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(AppColors.PanelBorder))
            SettingsNavRow(icon, text.first, text.second, framed = false, onClick = action)
        }
    }
}

@Composable
private fun NewsShowcase(onClick: () -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val items = WhatsNewItems
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val glow = androidx.compose.animation.core.rememberInfiniteTransition(label = "news")
    val pulse by glow.animateFloat(
        0f,
        1f,
        androidx.compose.animation.core.infiniteRepeatable<Float>(androidx.compose.animation.core.tween<Float>(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "news-pulse",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .liveClick(appCorner(14), sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                AppIcons.Sparkle,
                null,
                tint = AppColors.Accent,
                modifier = Modifier.size(22.dp).graphicsLayer {
                    val k = 0.9f + 0.14f * pulse
                    scaleX = k
                    scaleY = k
                    rotationZ = 12f * pulse
                },
            )
            Text("Что нового?", color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            if (version.isNotBlank()) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .accentHighlight(CircleShape)
                        .padding(horizontal = 11.dp, vertical = 4.dp),
                ) { Text("v$version", color = accentContent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
        androidx.compose.foundation.lazy.LazyRow(
            state = state,
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 40.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items.size, key = { items[it].title }) { i ->
                val item = items[i]
                Column(
                    Modifier
                        .width(232.dp)
                        .height(162.dp)
                        .liveClick(appCorner(20), sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = onClick)
                        .fieldSurface(appCorner(20))
                        .kitFrame(),
                ) {
                    Column(Modifier.weight(1f).padding(start = 14.dp, end = 14.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(appCorner(12))
                                .background(AppColors.Accent.copy(alpha = 0.16f))
                                .border(1.dp, AppColors.Accent.copy(alpha = 0.45f), appCorner(12)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(item.icon, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
                        Text(item.title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(item.text, color = AppColors.Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) { KitDivider() }
                }
            }
        }
        val dots = minOf(items.size, 7)
        if (dots > 1) {
            val active by androidx.compose.runtime.remember {
                androidx.compose.runtime.derivedStateOf {
                    val last = (items.size - 1).coerceAtLeast(1)
                    val at = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > 120) 1 else 0
                    (at.coerceAtMost(last) * (dots - 1) + last / 2) / last
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(dots) { d ->
                    val on = d == active
                    val w by androidx.compose.animation.core.animateDpAsState(if (on) 18.dp else 6.dp, Motion.tween(260), label = "news-dot")
                    val c by androidx.compose.animation.animateColorAsState(if (on) AppColors.Accent else AppColors.Accent.copy(alpha = 0.3f), Motion.fade(260), label = "news-dot-c")
                    Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(CircleShape).background(c))
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    PageBar(title, onBack)
}

@Composable
private fun InkContent() {
    SettingsGroup(null) {
        SettingSwitch("Режим E-Ink", EInk.on, hint = "Чёрное на белом, без анимаций и декора", icon = AppIcons.Ereader) { v -> EInk.turn(v) }
        SettingSegmented(
            "Очистка экрана",
            EInk.refreshChoices.map { if (it == 0) "Нет" else "$it стр." },
            EInk.refreshChoices.indexOf(EInk.refresh).coerceAtLeast(0),
        ) { i -> EInk.refreshEvery(EInk.refreshChoices[i]) }
    }
    HintNote("Очистка мигает чёрным раз в несколько страниц и убирает следы на экране", AppIcons.Info)
    HintNote("Режим работает только на этом устройстве. Шаблон и тема вернутся, когда выключишь", AppIcons.Ereader)
}

@Composable
private fun SoundContent() {
    val app = LocalContext.current.applicationContext as App
    val settings by app.readerSettings.settings.collectAsStateWithLifecycle()
    SettingsGroup(null) {
        SettingSwitch("Звуки кнопок", settings.sounds, hint = null, icon = AppIcons.Sound) { v ->
            app.readerSettings.update { it.copy(sounds = v) }
        }
        SettingSlider(
            label = "Громкость",
            icon = AppIcons.Sound,
            value = settings.soundVolume.toFloat(),
            range = 0.1f..1f,
            format = ::percent,
            enabled = settings.sounds,
        ) { v ->
            app.readerSettings.update { it.copy(soundVolume = v.toDouble()) }
            app.feedback.play(FeedbackSound.Tap)
        }
        SettingSwitch("Лёгкая вибрация", settings.haptics, hint = null, icon = AppIcons.Vibration) { v ->
            app.readerSettings.update { it.copy(haptics = v) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomizationContent(header: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val store = app.style
    val style by store.style.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var customAccent by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
    var tuneKit by remember { mutableStateOf<Kit?>(null) }
    tuneKit?.let { kit -> KitTuneSheet(kit) { tuneKit = null } }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                copying = true
                val ok = withContext(Dispatchers.IO) { copyImage(context, uri, store.backgroundImage) }
                copying = false
                if (ok) {
                    store.update { it.copy(background = BackgroundKind.Image.id) }
                } else {
                    Toast.makeText(context, "Не удалось открыть картинку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                copying = true
                val ok = withContext(Dispatchers.IO) { copyFile(context, uri, store.backgroundVideo) }
                copying = false
                if (ok) {
                    store.update { it.copy(background = BackgroundKind.Video.id) }
                } else {
                    Toast.makeText(context, "Не удалось открыть видео", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
    revealItem(key = "header") { header() }
    if (EInk.on) revealItem(key = "ink-note") { HintNote("Включён режим E-Ink — оформление упрощено", AppIcons.Ereader) }
    revealItem(key = "preview") { StylePreview() }

    revealItem(key = "templates") {
    TabbedFrame("Шаблоны оформления", icon = AppIcons.Palette, inset = false) {
        val current = Kit.of(style.kit)
        val start = Kit.shown.indexOf(current).coerceAtLeast(0)
        Kit.shown.forEach { rememberKitThumb(it, AppStyle.light, 124.dp, 176.dp) }
        androidx.compose.foundation.lazy.LazyRow(
            state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Kit.shown.size) { i ->
                val kit = Kit.shown[i]
                val spot = androidx.compose.runtime.remember { arrayOf<Offset?>(null) }
                Box(Modifier.onGloballyPositioned { c -> spot[0] = c.positionInWindow() + Offset(c.size.width / 2f, c.size.height / 2f) }) {
                    KitCard(kit, selected = kit == current) {
                        if (kit == current) {
                            tuneKit = kit
                            return@KitCard
                        }
                        ThemeSwap.run(spot[0]) {
                            store.update { st ->
                                val mode = if (st.kit != kit.id && ThemeMode.of(st.mode) != ThemeMode.System) (if (kit.lightFirst) ThemeMode.Light else ThemeMode.Dark).id else st.mode
                                val light = when (ThemeMode.of(mode)) { ThemeMode.Light -> true; ThemeMode.Dark -> false; ThemeMode.System -> !AppStyle.systemDark }
                                kit.applyTo(st, light).copy(mode = mode)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.PanelBorder))
        val modeBox = androidx.compose.runtime.remember { arrayOf<androidx.compose.ui.geometry.Rect?>(null) }
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp).onGloballyPositioned { c -> modeBox[0] = c.boundsInWindow() }) {
            val modes = ThemeMode.entries
            SettingSegmented(null, listOf("☀ Светлая", "☾ Тёмная", "◐ Система"), modes.indexOf(ThemeMode.of(style.mode))) { idx ->
                if (modes[idx].id == style.mode) return@SettingSegmented
                val b = modeBox[0]
                val at = b?.let { Offset(it.left + it.width * (idx + 0.5f) / modes.size, it.center.y) }
                val before = AppStyle.light
                val after = when (modes[idx]) { ThemeMode.Light -> true; ThemeMode.Dark -> false; ThemeMode.System -> !AppStyle.systemDark }
                if (before == after) store.update { it.copy(mode = modes[idx].id) }
                else ThemeSwap.run(at) { store.update { it.copy(mode = modes[idx].id) } }
            }
        }
    }
    }

    revealItem(key = "app-icon") { AppIconGroup() }

    revealItem(key = "colors") {
    SettingsGroup("Цвета") {
        Text("Акцентный цвет", color = AppColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        FlowRow(
            Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AccentChoices.forEach { color ->
                val selected = style.accent == color
                Box(
                    Modifier
                        .size(40.dp)
                        .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.85f) {
                            customAccent = false
                            store.update { it.copy(accent = color) }
                        }
                        .border(2.dp, if (selected) AppColors.Text else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color))) }
            }
            Box(
                Modifier
                    .size(40.dp)
                    .liveClick(CircleShape, sound = FeedbackSound.Toggle) { customAccent = !customAccent }
                    .border(
                        2.dp,
                        if (style.accent !in AccentChoices) AppColors.Text else AppColors.Line,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(
                        Brush.sweepGradient((0..6).map { Color.hsv((it * 60f) % 360f, 0.6f, 0.95f) }),
                    ),
                )
            }
        }
        AnimatedVisibility(visible = customAccent, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(bottom = 8.dp)) {
                ColorPicker(style.accent) { color -> store.update { it.copy(accent = color) } }
            }
        }
    }
    }

    revealItem(key = "shape") {
    SettingsGroup("Форма и кнопки") {
        SettingSlider(
            label = "Скругление углов",
            icon = AppIcons.Corners,
            value = style.corners,
            range = 0f..1.6f,
            format = { if (it < 0.05f) "Квадратные" else "${(it * 100).roundToInt()}%" },
        ) { v -> store.update { it.copy(corners = v) } }
        PickerRow(
            label = "Вид активных кнопок",
            icon = AppIcons.Press,
            options = ButtonLook.entries.map { look ->
                PickerOption(
                    look.id,
                    look.title,
                    when (look) {
                        ButtonLook.Soft -> "Полупрозрачный цветной фон"
                        ButtonLook.Outline -> "Только цветная рамка"
                        ButtonLook.Filled -> "Сплошной акцентный цвет"
                    },
                )
            },
            selectedId = ButtonLook.of(style.buttons).id,
        ) { id -> store.update { it.copy(buttons = id) } }
        SettingSwitch("Контур панелей", style.outline, hint = null, icon = AppIcons.Outline) { v ->
            store.update { it.copy(outline = v) }
        }
    }
    }

    revealItem(key = "motion") {
    SettingsGroup("Анимации") {
        SettingSwitch(
            "Анимации",
            style.animations,
            hint = if (style.animations) "Плавные переходы окон и кнопок" else "Всё появляется мгновенно",
            icon = AppIcons.Animation,
        ) { v -> store.update { it.copy(animations = v) } }
        Appear(style.animations) {
            PickerRow(
                label = "Скорость",
                icon = AppIcons.Speed,
                options = MotionSpeed.entries.map { PickerOption(it.id, it.title, it.hint) },
                selectedId = MotionSpeed.of(style.motionSpeed).id,
            ) { id -> store.update { it.copy(motionSpeed = id) } }
            PickerRow(
                label = "Характер",
                icon = AppIcons.Sparkle,
                options = MotionStyle.entries.map { PickerOption(it.id, it.title, it.hint) },
                selectedId = MotionStyle.of(style.motionStyle).id,
            ) { id -> store.update { it.copy(motionStyle = id) } }
            PickerRow(
                label = "Отклик кнопок",
                icon = AppIcons.Gesture,
                options = PressStrength.entries.map { PickerOption(it.id, it.title, it.hint) },
                selectedId = PressStrength.of(style.press).id,
            ) { id -> store.update { it.copy(press = id) } }
            MotionPreview()
        }
    }
    }

    revealItem(key = "font") {
    SettingsGroup("Текст интерфейса") {
        SettingSlider(
            label = "Размер текста",
            icon = AppIcons.LineHeight,
            value = style.textScale,
            range = 0.85f..1.25f,
            steps = 7,
            format = ::percent,
        ) { v -> store.update { it.copy(textScale = v) } }
    }
    }

    revealItem(key = "background") {
    SettingsGroup("Фон приложения") {
        PickerRow(
            label = "Фон",
            icon = AppIcons.Image,
            options = BackgroundKind.entries.map { kind ->
                PickerOption(
                    kind.id,
                    kind.title,
                    when (kind) {
                        BackgroundKind.None -> "Однотонный фон темы"
                        BackgroundKind.Image -> "Своя картинка из галереи"
                        BackgroundKind.Video -> "Своё видео со звуком"
                    },
                    icon = when (kind) {
                        BackgroundKind.None -> AppIcons.Close
                        BackgroundKind.Image -> AppIcons.Image
                        BackgroundKind.Video -> AppIcons.Video
                    },
                )
            },
            selectedId = BackgroundKind.of(style.background).id,
        ) { id ->
            when (BackgroundKind.of(id)) {
                BackgroundKind.None -> store.update { it.copy(background = BackgroundKind.None.id) }
                BackgroundKind.Image ->
                    if (store.backgroundImage.exists()) {
                        store.update { it.copy(background = BackgroundKind.Image.id) }
                    } else {
                        pickImage.launch(arrayOf("image/*"))
                    }
                BackgroundKind.Video ->
                    if (store.backgroundVideo.exists()) {
                        store.update { it.copy(background = BackgroundKind.Video.id) }
                    } else {
                        pickVideo.launch(arrayOf("video/*"))
                    }
            }
        }
        val kind = BackgroundKind.of(style.background)
        if (copying) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.flerty.wikiflerty.ui.KitSpinner(Modifier.size(18.dp), AppColors.Accent)
                Spacer(Modifier.width(10.dp))
                Text("Загружаю…", color = AppColors.Muted, fontSize = 14.sp)
            }
        }
        Appear(kind != BackgroundKind.None) {
            val shape = appCorner(12)
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .liveClick(shape, sound = FeedbackSound.Open) {
                        if (kind == BackgroundKind.Image) pickImage.launch(arrayOf("image/*")) else pickVideo.launch(arrayOf("video/*"))
                    }
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.33f), shape),
                contentAlignment = Alignment.Center,
            ) {
                ActionLabel(if (kind == BackgroundKind.Image) "Выбрать другую картинку" else "Выбрать другое видео", AppColors.Accent, fontSize = 14.sp, iconSize = 20.dp, icon = if (kind == BackgroundKind.Image) AppIcons.Image else AppIcons.Video)
            }
            SettingSlider("Приглушение", style.backgroundDim, 0f..0.9f, ::percent, icon = AppIcons.Moon) { v ->
                store.update { it.copy(backgroundDim = v) }
            }
            Appear(kind == BackgroundKind.Video) {
                SettingSlider(
                    "Звук видео",
                    style.videoVolume,
                    0f..1f,
                    { if (it < 0.01f) "Без звука" else percent(it) },
                    icon = AppIcons.Sound,
                ) { v -> store.update { it.copy(videoVolume = v) } }
                PickerRow(
                    label = "Во время чтения",
                    icon = AppIcons.Book,
                    options = ReadingSound.entries.map {
                        PickerOption(
                            it.id,
                            it.title,
                            it.hint,
                            icon = when (it) {
                                ReadingSound.Normal -> AppIcons.Sound
                                ReadingSound.Quiet -> AppIcons.VolumeKeys
                                ReadingSound.Off -> AppIcons.Close
                            },
                        )
                    },
                    selectedId = ReadingSound.of(style.readingSound).id,
                ) { id -> store.update { it.copy(readingSound = id) } }
            }
            Appear(kind == BackgroundKind.Image && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingSlider("Размытие", style.backgroundBlur, 0f..30f, { if (it < 0.5f) "Нет" else "${it.roundToInt()}" }, icon = AppIcons.Drop) { v ->
                    store.update { it.copy(backgroundBlur = v) }
                }
            }
        }
    }
    }

    revealItem(key = "reset") {
    val resetShape = appCorner(14)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .liveClick(resetShape, sound = FeedbackSound.Close) {
                store.update { UiStyle() }
                customAccent = false
            }
            .border(1.dp, Color(0x55F08A8A), resetShape),
        contentAlignment = Alignment.Center,
    ) { ActionLabel("Сбросить оформление", AppColors.Bad, fontSize = 15.sp) }
    }
    }
}

@Composable
private fun StylePreview() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(appCorner(20))
            .background(AppColors.Background)
            .border(1.dp, AppColors.PanelBorder, appCorner(20))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Предпросмотр", color = AppColors.Muted, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().height(60.dp).panelSurface(appCorner(20)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(AppIcons.Toc, AppIcons.Theme, AppIcons.Note).forEach { icon ->
                Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = AppColors.Text, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                Modifier.weight(1f).height(46.dp).clip(appCorner(14)).accentHighlight(appCorner(14)),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Options, null, tint = accentContent, modifier = Modifier.size(22.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().clip(appCorner(12)).background(AppColors.Field)
                .border(1.dp, AppColors.PanelBorder, appCorner(12)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier.weight(1f).height(34.dp).clip(appCorner(9)).background(AppColors.AccentTintStrong),
                contentAlignment = Alignment.Center,
            ) { Text("Страницы", color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                Text("Лента", color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AppColors.Line)) {
            Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(CircleShape).background(AppColors.Accent))
        }
        Text("Аа Бб Вв — так выглядит текст интерфейса", color = AppColors.Text, fontSize = 15.sp)
    }
}

private fun copyFile(context: Context, uri: Uri, target: File): Boolean = runCatching {
    val tmp = File(target.parentFile, target.name + ".tmp")
    context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return false
    target.delete()
    tmp.renameTo(target)
}.getOrDefault(false)

private fun copyImage(context: Context, uri: Uri, target: File): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return false
    target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    true
}.getOrDefault(false)

@Composable
private fun MotionPreview() {
    var on by remember { mutableStateOf(false) }
    val offset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = Motion.tween(420),
        label = "preview",
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(36.dp)
                .fieldSurface(appCorner(18)),
        ) {
            val travel = maxWidth - 36.dp
            Box(
                Modifier
                    .padding(4.dp)
                    .offset { androidx.compose.ui.unit.IntOffset((travel * offset).roundToPx(), 0) }
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent),
            )
        }
        AppButton("Проверить", primary = true, modifier = Modifier.width(120.dp)) { on = !on }
    }
}

@Composable
private fun KitCard(kit: Kit, selected: Boolean, onClick: () -> Unit) {
    val light = AppStyle.light
    val base = if (light) kit.light else kit.dark
    val accent = Color(if (light) kit.accentLight else kit.accent)
    val shape = appCorner(18)
    val strip = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (selected) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(selected) {
        strip.animateTo(if (selected) 1f else 0f, if (selected) androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 340f) else Motion.tween(180))
    }
    Box(
        Modifier
            .width(124.dp)
            .height(176.dp)
            .liveClick(shape, sound = if (selected) FeedbackSound.Open else FeedbackSound.Toggle, onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else AppColors.PanelBorder, shape),
    ) {
        val thumb by rememberKitThumb(kit, light, 124.dp, 176.dp)
        androidx.compose.animation.Crossfade(thumb, Modifier.matchParentSize(), animationSpec = Motion.fade(180), label = "kit-thumb") { image ->
            if (image != null) {
                androidx.compose.foundation.Image(image, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
            } else {
                Box(Modifier.fillMaxSize().background(Color(base.background)))
            }
        }
        if (selected) KitBackdrop(kit.decor, base, accent, Modifier.matchParentSize(), animated = true, staticLayer = false)
        Column(Modifier.matchParentSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val coverShape = RoundedCornerShape((4 * kit.corners).dp + 2.dp)
            val covers = listOf(0xFFE59AAE, 0xFF4F7EA0, 0xFFD9B25A, 0xFF5F7A4E)
            repeat(2) { row ->
                Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                    repeat(3) { k ->
                        val c = Color(covers[(row * 3 + k) % covers.size])
                        Box(
                            Modifier
                                .size(width = 22.dp, height = 32.dp)
                                .then(
                                    when (kit.decor) {
                                        Decor.Comic -> Modifier.hardShadow(coverShape, 2.dp).border(1.5.dp, Color(0xFF111111), coverShape)
                                        Decor.News -> Modifier.border(1.dp, Color(base.text), coverShape)
                                        Decor.Sea -> Modifier.border(0.8.dp, Color.White.copy(alpha = 0.4f), coverShape)
                                        Decor.Clay -> Modifier.shadow(4.dp, coverShape, ambientColor = Color(0xFFC9B8EE), spotColor = Color(0xFFC9B8EE))
                                        else -> Modifier
                                    },
                                )
                                .clip(coverShape)
                                .background(if (kit.decor == Decor.News) (if ((row * 3 + k) % 4 == 1) accent else Color(base.muted)) else c),
                        )
                    }
                }
                val plank = when (kit.decor) {
                    Decor.Comic -> Color(0xFFFFCC00)
                    Decor.Zen -> Color(0xFFA7B37A)
                    Decor.Library -> Color(0xFF6B4322)
                    Decor.News -> Color(base.text)
                    Decor.Sea -> accent.copy(alpha = 0.5f)
                    Decor.Clay -> accent
                    else -> Color(0xFFA8743F)
                }
                Box(
                    Modifier.fillMaxWidth().height(if (kit.decor == Decor.News) 2.dp else 6.dp)
                        .clip(RoundedCornerShape(if (kit.decor == Decor.Clay) 3.dp else 1.dp))
                        .background(plank)
                        .then(if (kit.decor == Decor.Comic) Modifier.border(1.dp, Color(0xFF111111)) else Modifier)
                        .then(if (kit.decor == Decor.Frost) Modifier.drawBehind { drawRect(Color.White, size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.45f)) } else Modifier),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                kit.title,
                color = Color(base.text),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                lineHeight = 13.sp,
                fontSize = 11.5.sp,
                modifier = Modifier.graphicsLayer { alpha = 1f - strip.value },
            )
        }
        if (strip.value > 0.001f) {
            val ink = if (accent.luminance() > 0.45f) Color(0xFF15120C) else Color.White
            Box(Modifier.matchParentSize().clip(shape), contentAlignment = Alignment.BottomCenter) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .graphicsLayer {
                            translationY = (1f - strip.value) * size.height
                            alpha = strip.value.coerceIn(0f, 1f)
                        }
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(accent, Color.White, 0.18f), accent)))
                        .drawBehind { drawRect(Color.White.copy(alpha = 0.35f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
                        .padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        kit.title,
                        color = ink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 12.sp,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AppIcons.Options,
                            "Настроить шаблон",
                            tint = ink,
                            modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = -150f * (1f - strip.value) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppIconGroup() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIconStyle.current(context)) }
    var asking by remember { mutableStateOf<AppIconStyle?>(null) }
    TabbedFrame("Иконка приложения", icon = AppIcons.Grid, inset = false) {
        val styles = AppIconStyle.entries
        val start = styles.indexOf(current).coerceAtLeast(0)
        androidx.compose.foundation.lazy.LazyRow(
            state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(styles.size) { i ->
                val style = styles[i]
                val selected = style == current
                val shape = appCorner(18)
                Column(
                    Modifier
                        .width(96.dp)
                        .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.92f) { if (!selected) asking = style }
                        .fieldSurface(shape, selected = selected)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIconPreview(style, 60.dp)
                    FitText(style.title, if (selected) AppColors.Accent else AppColors.Muted, 13.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
    asking?.let { style ->
        ConfirmSheet(
            title = "Сменить иконку?",
            text = "Иконка обновится через пару секунд. На некоторых телефонах ярлык пропадёт с рабочего стола — его нужно будет заново вынести из меню приложений.",
            confirm = "Сменить",
            danger = false,
            onConfirm = {
                AppIconStyle.apply(context, style)
                current = style
            },
            onDismiss = { asking = null },
        )
    }
}
