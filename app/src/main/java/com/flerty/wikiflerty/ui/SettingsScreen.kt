package com.flerty.wikiflerty.ui

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

/** Main settings, split into sections so each thing is where you expect it. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val deeper = targetState != null
            val dir = if (deeper) 1 else -1
            (slideInHorizontally(tween(260)) { dir * it / 5 } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(tween(220)) { -dir * it / 5 } + fadeOut(tween(160)))
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
                SettingsHome { page = it }
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
private fun SettingsHome(onOpen: (SettingsPage) -> Unit) {
    SettingsNavRow(AppIcons.Sparkle, "ИИ-помощник", "Ключи, модели, перевод") { onOpen(SettingsPage.Ai) }
    SettingsNavRow(AppIcons.Theme, "Кастомизация", "Цвета, форма, шрифт, фон") { onOpen(SettingsPage.Custom) }
    SettingsNavRow(AppIcons.Sound, "Звук и отклик", "Звуки и вибрация") { onOpen(SettingsPage.Sound) }
    SettingsNavRow(AppIcons.Backup, "Резервная копия", "Экспорт и импорт") { onOpen(SettingsPage.Backup) }
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

// ---------------------------------------------------------------- sound

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

// ---------------------------------------------------------------- customization

private val AccentChoices = listOf(
    0xFF8FB3FF, 0xFF7FD1F5, 0xFF5FD4C0, 0xFF6FD3A6, 0xFFB5D96A,
    0xFFE8C14A, 0xFFE8A33D, 0xFFF28B6B, 0xFFF08FA8, 0xFFB79CFF,
)

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

    // Whole templates: background with glows, colors, font, buttons, corners.
    Text("Шаблоны оформления", color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StyleTemplates.forEach { template ->
            TemplateCard(template, selected = template.matches(style)) { store.update { template.applyTo(it) } }
        }
    }
    SettingsGroup(null) {
        SettingSwitch("Свечение фона", style.ambience, hint = "Мягкий цветной фон шаблона", icon = AppIcons.Sun) { v ->
            store.update { it.copy(ambience = v) }
        }
    }

    // Simple color pairs on the classic backgrounds.
    Text("Цветовые пары", color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StylePresets.forEach { preset ->
            val selected = style.base == preset.base.id && style.accent == preset.accent
            val shape = appCorner(16)
            Column(
                Modifier
                    .width(92.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle) {
                        store.update { it.copy(base = preset.base.id, accent = preset.accent) }
                    }
                    .background(Color(preset.base.background))
                    .border(if (selected) 2.dp else 1.dp, if (selected) Color(preset.accent) else AppColors.PanelBorder, shape)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(22.dp).clip(appCorner(8)).background(Color(preset.base.panel)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(Modifier.padding(end = 6.dp).size(10.dp).clip(CircleShape).background(Color(preset.accent)))
                }
                Box(Modifier.width(44.dp).height(6.dp).clip(CircleShape).background(Color(preset.accent)))
                Text(preset.title, color = Color(0xFFECEDEF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    SettingsGroup("Цвета") {
        PickerRow(
            label = "Основной фон",
            icon = AppIcons.Palette,
            options = UiBase.entries.map { PickerOption(it.id, it.title, swatch = Color(it.panel)) },
            selectedId = UiBase.of(style.base).id,
        ) { id -> store.update { it.copy(base = id) } }
        Text("Акцентный цвет", color = AppColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
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
        SettingSwitch("Эффект нажатия", style.pressEffect, hint = null, icon = AppIcons.Gesture) { v ->
            store.update { it.copy(pressEffect = v) }
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
                        BackgroundKind.Video -> "Своё видео, без звука"
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
            SettingSlider("Затемнение", style.backgroundDim, 0f..0.9f, ::percent, icon = AppIcons.Moon) { v ->
                store.update { it.copy(backgroundDim = v) }
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
    ) { Text("Сбросить оформление", color = Color(0xFFF08A8A), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

/** A small live sample of the current look: a panel with buttons, a chip and a field. */
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
        // A sample switch (one option chosen) and a progress line — not clickable, just for looks.
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

// ---------------------------------------------------------------- files

private fun copyFile(context: Context, uri: Uri, target: File): Boolean = runCatching {
    val tmp = File(target.parentFile, target.name + ".tmp")
    context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return false
    target.delete()
    tmp.renameTo(target)
}.getOrDefault(false)

/** Copies a picture, shrinking very large ones so they load fast and use little memory. */
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

/** A small picture of a template: its glowing background, a card and an accent button. */
@Composable
private fun TemplateCard(template: StylePreset, selected: Boolean, onClick: () -> Unit) {
    val shape = appCorner(18)
    val accent = Color(template.accent)
    Box(
        Modifier
            .width(132.dp)
            .height(176.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else AppColors.PanelBorder, shape),
    ) {
        template.base.ambience?.let { AmbienceBackground(template.base, it, Modifier.matchParentSize()) }
            ?: Box(Modifier.matchParentSize().background(Color(template.base.background)))
        Column(Modifier.matchParentSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // A tiny "continue reading" card
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.18f).compositeOver(Color(template.base.field)))
                    .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 18.dp, height = 26.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFE0A548)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.width(48.dp).height(5.dp).clip(CircleShape).background(Color(0xCCFFFFFF)))
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(accent))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) {
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(template.base.field))
                            .border(1.dp, Color(template.base.border), RoundedCornerShape(8.dp)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(template.title, color = Color(0xFFF1F2F5), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 16.sp)
            Box(Modifier.width(40.dp).height(6.dp).clip(CircleShape).background(accent))
        }
    }
}
