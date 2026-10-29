package com.flerty.wikiflerty.ui

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Sound("Звук и отклик"),
    Backup("Резервная копия"),
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onAbout: (() -> Unit)? = null) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }

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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current == null) {
                SettingsHome(onAbout) { page = it }
            } else {
                PageHeader(current.title) { page = null }
                when (current) {
                    SettingsPage.Ai -> AiSettingsContent((LocalContext.current.applicationContext as App).ai)
                    SettingsPage.Custom -> CustomizationContent()
                    SettingsPage.Sound -> SoundContent()
                    SettingsPage.Backup -> BackupContent()
                }
            }
        }
    }
}

@Composable
private fun SettingsHome(onAbout: (() -> Unit)?, onOpen: (SettingsPage) -> Unit) {
    SettingsNavRow(AppIcons.Sparkle, "ИИ-помощник", "Ключи, модели, перевод") { onOpen(SettingsPage.Ai) }
    SettingsNavRow(AppIcons.Theme, "Кастомизация", "Цвета, форма, шрифт, фон") { onOpen(SettingsPage.Custom) }
    SettingsNavRow(AppIcons.Sound, "Звук и отклик", "Звуки и вибрация") { onOpen(SettingsPage.Sound) }
    SettingsNavRow(AppIcons.Backup, "Резервная копия", "Экспорт и импорт") { onOpen(SettingsPage.Backup) }
    if (onAbout != null) SettingsNavRow(AppIcons.Info, "О приложении", "Обновления, журнал сбоев", onAbout)
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(44.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Back, "Назад", tint = AppColors.Text, modifier = Modifier.size(22.dp)) }
        Text(title, color = AppColors.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
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
private fun CustomizationContent() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val store = app.style
    val style by store.style.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var customAccent by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }

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

    StylePreview()

    Text("Шаблоны оформления", color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StyleTemplates.forEach { template ->
            TemplateCard(template, selected = template.matches(style)) { store.update { template.applyTo(it) } }
        }
    }

    if (AppStyle.base.ambience != null) {
        SettingsGroup(null) {
            SettingSwitch("Свечение фона", style.ambience, hint = null, icon = AppIcons.Sparkle) { v ->
                store.update { it.copy(ambience = v) }
            }
        }
    }

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

    SettingsGroup("Анимации") {
        SettingSwitch(
            "Анимации",
            style.animations,
            hint = if (style.animations) "Плавные переходы окон и кнопок" else "Всё появляется мгновенно",
            icon = AppIcons.Animation,
        ) { v -> store.update { it.copy(animations = v) } }
        if (style.animations) {
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

    SettingsGroup("Шрифт интерфейса") {
        val fonts = remember { FontCatalog.available(context).filter { it.css != null && it.category != FontCategory.Mono || it.id == "jetbrainsmono" } }
        PickerRow(
            label = "Шрифт",
            icon = AppIcons.TextSize,
            options = listOf(PickerOption("system", "Системный", "Как в Android")) +
                fonts.filter { it.bundled }.map {
                    PickerOption(it.id, it.title, "Съешь же ещё этих мягких французских булок", FontCatalog.preview(context, it))
                },
            selectedId = style.font,
        ) { id -> store.update { it.copy(font = id) } }
        SettingSlider(
            label = "Размер текста",
            icon = AppIcons.LineHeight,
            value = style.textScale,
            range = 0.85f..1.25f,
            steps = 7,
            format = ::percent,
        ) { v -> store.update { it.copy(textScale = v) } }
    }

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
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                Spacer(Modifier.width(10.dp))
                Text("Загружаю…", color = AppColors.Muted, fontSize = 14.sp)
            }
        }
        if (kind != BackgroundKind.None) {
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
                Text(
                    if (kind == BackgroundKind.Image) "Выбрать другую картинку" else "Выбрать другое видео",
                    color = AppColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SettingSlider("Приглушение", style.backgroundDim, 0f..0.9f, ::percent, icon = AppIcons.Moon) { v ->
                store.update { it.copy(backgroundDim = v) }
            }
            if (kind == BackgroundKind.Video) {
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
            if (kind == BackgroundKind.Image && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingSlider("Размытие", style.backgroundBlur, 0f..30f, { if (it < 0.5f) "Нет" else "${it.roundToInt()}" }, icon = AppIcons.Drop) { v ->
                    store.update { it.copy(backgroundBlur = v) }
                }
            }
        }
    }

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
    ) { Text("Сбросить оформление", color = AppColors.Bad, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
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
                    .offset(x = travel * offset)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent),
            )
        }
        AppButton("Проверить", primary = true, modifier = Modifier.width(120.dp)) { on = !on }
    }
}

@Composable
private fun TemplateCard(template: StyleTemplate, selected: Boolean, onClick: () -> Unit) {
    val shape = appCorner(18)
    val accent = Color(template.accent)
    val base = template.base
    Box(
        Modifier
            .width(124.dp)
            .height(168.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else AppColors.PanelBorder, shape),
    ) {
        base.ambience?.let { AmbienceBackground(base, it, Modifier.matchParentSize()) }
            ?: Box(Modifier.matchParentSize().background(Color(base.background)))
        Column(Modifier.matchParentSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.18f).compositeOver(Color(base.field)))
                    .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 18.dp, height = 26.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.7f)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.width(46.dp).height(5.dp).clip(CircleShape).background(Color(base.text).copy(alpha = 0.8f)))
                    Box(Modifier.width(34.dp).height(4.dp).clip(CircleShape).background(accent))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) {
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(base.field))
                            .border(1.dp, Color(base.border), RoundedCornerShape(8.dp)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    template.title,
                    color = Color(base.text),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Check, null, tint = if (accent.luminance() > 0.4f) Color(0xFF0D1424) else Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Box(Modifier.width(40.dp).height(6.dp).clip(CircleShape).background(accent))
        }
    }
}
