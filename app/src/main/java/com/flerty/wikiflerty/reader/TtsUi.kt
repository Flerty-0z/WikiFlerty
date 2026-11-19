package com.flerty.wikiflerty.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.tts.ReadAloud
import com.flerty.wikiflerty.tts.SleepTimer
import com.flerty.wikiflerty.tts.TtsVoiceInfo
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.PickerOption
import com.flerty.wikiflerty.ui.PickerRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.tts.NeuralVoices
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import java.util.Locale
import kotlinx.coroutines.launch

val TtsColors = listOf(0xFFF2C46B, 0xFF7ED3A0, 0xFF8AB4F8, 0xFFF28B9B, 0xFFC3A6F5, 0xFFB0B4BC)

@Composable
private fun DotRow(count: Int, content: @Composable (androidx.compose.ui.unit.Dp) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val dot = minOf(40.dp, maxWidth / count)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { content(dot) }
    }
}

@Composable
private fun ColorDot(color: Long, selected: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 40.dp, content: (@Composable () -> Unit)? = null) {
    val ring by androidx.compose.animation.animateColorAsState(if (selected) AppColors.Text else Color.Transparent, com.flerty.wikiflerty.ui.Motion.fade(200), label = "dot-ring")
    val grow by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0.86f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 520f), label = "dot-grow")
    Box(
        Modifier.size(size)
            .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.85f, onClick = onClick)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size * 0.7f).graphicsLayer { scaleX = grow; scaleY = grow }.clip(CircleShape).then(if (content == null) Modifier.background(Color(color)) else Modifier.border(1.5.dp, AppColors.Line, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { content?.invoke() }
    }
}

class TtsActions(
    val onStart: () -> Unit,
    val onToggle: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onStop: () -> Unit,
    val onSleep: (SleepTimer?) -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
fun TtsBar(playing: Boolean, speed: Double, actions: TtsActions, modifier: Modifier = Modifier, onHide: () -> Unit = {}) {
    Row(
        modifier
            .padding(start = 12.dp, end = 12.dp)
            .widthIn(max = PanelMaxWidth)
            .fillMaxWidth()
            .height(68.dp)
            .panelSurface(appCorner(22))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BarButton(AppIcons.Close, "Остановить", FeedbackSound.Close, actions.onStop)
        BarButton(AppIcons.EyeOff, "Скрыть панель", FeedbackSound.Close, onHide)
        BarButton(AppIcons.SkipPrev, "Предыдущее предложение", FeedbackSound.Tap, actions.onPrevious)
        val shape = CircleShape
        Box(
            Modifier.size(54.dp).liveClick(shape, sound = if (playing) FeedbackSound.Pause else FeedbackSound.Play, pressedScale = 0.9f, onClick = actions.onToggle).accentHighlight(shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) AppIcons.Pause else AppIcons.Play, if (playing) "Пауза" else "Продолжить", tint = accentContent, modifier = Modifier.size(24.dp))
        }
        BarButton(AppIcons.SkipNext, "Следующее предложение", FeedbackSound.Tap, actions.onNext)
        BarButton(AppIcons.Sliders, "Параметры чтения вслух", FeedbackSound.Open, actions.onOpenSettings)
    }
}

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, sound: FeedbackSound, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).liveClick(CircleShape, sound = sound, pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = AppColors.Text, modifier = Modifier.size(22.dp)) }
}

@Composable
fun TtsProblemPrompt(problem: ReadAloud.Problem, onInstall: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, onVoices: () -> Unit = {}) {
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) { detectTapGestures { } }
            .panelSurface(appCorner(20))
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(problem.message, color = AppColors.Text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        if (problem.openVoices) {
            val shape = appCorner(14)
            Box(
                Modifier.heightIn(min = 36.dp).liveClick(shape, sound = FeedbackSound.Open, onClick = onVoices).accentHighlight(shape).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Headphones, "Голоса", tint = accentContent, modifier = Modifier.size(18.dp)) }
        }
        if (problem.canInstallVoice) {
            val shape = appCorner(14)
            Box(
                Modifier.heightIn(min = 36.dp).liveClick(shape, sound = FeedbackSound.Open, onClick = onInstall).accentHighlight(shape).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Голоса", color = accentContent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        Box(
            Modifier.size(36.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Close, "Закрыть", tint = AppColors.Muted, modifier = Modifier.size(16.dp)) }
    }
}

private fun qualityName(q: Int): String = when {
    q >= android.speech.tts.Voice.QUALITY_VERY_HIGH -> "отличное качество"
    q >= android.speech.tts.Voice.QUALITY_HIGH -> "высокое качество"
    q >= android.speech.tts.Voice.QUALITY_NORMAL -> "обычное качество"
    else -> "низкое качество"
}

private fun languageName(voice: TtsVoiceInfo): String =
    voice.language.locale.getDisplayName(Locale("ru")).replaceFirstChar { it.uppercase() }

@Composable
private fun PiperVoiceList(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val installed by com.flerty.wikiflerty.tts.PiperVoices.installed.collectAsStateWithLifecycle()
    val progress by com.flerty.wikiflerty.tts.PiperVoices.progress.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.flerty.wikiflerty.tts.PiperVoices.refresh(context) }
    }
    val current = NeuralVoices.voiceOrDefault(NeuralVoices.PIPER, selected)
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Голос", color = AppColors.Muted, fontSize = 12.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        com.flerty.wikiflerty.tts.PiperVoices.all.forEachIndexed { index, v ->
            if (index > 0) com.flerty.wikiflerty.ui.ListDivider()
            val has = v.id in installed
            val p = progress[v.id]
            val isSel = v.id == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.98f) {
                        onSelect(v.id)
                        if (!has && p == null) scope.launch { com.flerty.wikiflerty.tts.PiperVoices.download(context, v.id) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.flerty.wikiflerty.ui.SettingIcon(AppIcons.Voice)
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    val titleColor by androidx.compose.animation.animateColorAsState(if (isSel) AppColors.Accent else AppColors.Text, com.flerty.wikiflerty.ui.Motion.fade(220), label = "voice-title")
                    Text(v.title, color = titleColor, fontSize = 15.sp, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    Text(
                        when {
                            p != null -> "Скачиваю… ${(p * 100).toInt()}%"
                            has -> v.hint + " · скачан"
                            else -> v.hint + " · ${v.sizeMb} МБ"
                        },
                        color = if (p != null) AppColors.Accent else AppColors.Faint,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    val shownP by androidx.compose.animation.core.animateFloatAsState(
                        p ?: 1f,
                        com.flerty.wikiflerty.ui.Motion.tween(420),
                        label = "dl",
                    )
                    com.flerty.wikiflerty.ui.Appear(p != null) {
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(AppColors.Line)) {
                            Box(Modifier.fillMaxWidth(shownP.coerceIn(0f, 1f)).height(3.dp).clip(CircleShape).background(AppColors.Accent))
                        }
                    }
                }
                val trail = when {
                    has && !isSel -> 1
                    !has && p == null -> 2
                    else -> 0
                }
                androidx.compose.animation.AnimatedContent(
                    trail,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(com.flerty.wikiflerty.ui.Motion.tween(200, 40)) + androidx.compose.animation.scaleIn(com.flerty.wikiflerty.ui.Motion.tween(220), initialScale = 0.7f)) togetherWith
                            (androidx.compose.animation.fadeOut(com.flerty.wikiflerty.ui.Motion.tween(120)) + androidx.compose.animation.scaleOut(com.flerty.wikiflerty.ui.Motion.tween(160), targetScale = 0.7f)) using
                            androidx.compose.animation.SizeTransform(clip = false) { _, _ -> com.flerty.wikiflerty.ui.Motion.tween(220) }
                    },
                    contentAlignment = Alignment.Center,
                    label = "voice-trail",
                ) { t ->
                    when (t) {
                        1 -> Box(
                            Modifier.size(36.dp).liveClick(CircleShape, sound = FeedbackSound.Close) { com.flerty.wikiflerty.tts.PiperVoices.delete(context, v.id) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Trash, "Удалить голос", tint = AppColors.Faint, modifier = Modifier.size(17.dp)) }
                        2 -> Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(AppIcons.Download, "Скачать", tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
                        else -> Spacer(Modifier.size(1.dp))
                    }
                }
                Box(Modifier.padding(start = 6.dp).size(22.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        isSel,
                        enter = androidx.compose.animation.fadeIn(com.flerty.wikiflerty.ui.Motion.tween(200)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 600f), initialScale = 0.4f),
                        exit = androidx.compose.animation.fadeOut(com.flerty.wikiflerty.ui.Motion.tween(140)) + androidx.compose.animation.scaleOut(com.flerty.wikiflerty.ui.Motion.tween(160), targetScale = 0.4f),
                    ) { Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TtsOptions(
    settings: ReaderSettings,
    bookLanguage: String?,
    active: Boolean,
    playing: Boolean,
    sleep: SleepTimer?,
    actions: TtsActions,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
) {
    val context = LocalContext.current

    val shape = appCorner(18)
    androidx.compose.animation.AnimatedContent(
        active,
        transitionSpec = {
            (androidx.compose.animation.fadeIn(com.flerty.wikiflerty.ui.Motion.tween(260, 60)) + androidx.compose.animation.scaleIn(com.flerty.wikiflerty.ui.Motion.tween(300), initialScale = 0.94f)) togetherWith
                (androidx.compose.animation.fadeOut(com.flerty.wikiflerty.ui.Motion.tween(140)) + androidx.compose.animation.scaleOut(com.flerty.wikiflerty.ui.Motion.tween(200), targetScale = 0.96f)) using
                androidx.compose.animation.SizeTransform(clip = false) { _, _ -> com.flerty.wikiflerty.ui.Motion.tween(300) }
        },
        label = "tts-start",
    ) { isActive ->
    if (!isActive) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = actions.onStart).accentHighlight(shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(AppIcons.Play, null, tint = accentContent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Чтение вслух", color = accentContent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).heightIn(min = 54.dp).liveClick(shape, sound = FeedbackSound.Toggle, onClick = actions.onToggle).accentHighlight(shape),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                androidx.compose.animation.AnimatedContent(
                    playing,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(com.flerty.wikiflerty.ui.Motion.tween(200, 40)) + androidx.compose.animation.scaleIn(com.flerty.wikiflerty.ui.Motion.tween(240), initialScale = 0.8f)) togetherWith
                            (androidx.compose.animation.fadeOut(com.flerty.wikiflerty.ui.Motion.tween(120)) + androidx.compose.animation.scaleOut(com.flerty.wikiflerty.ui.Motion.tween(160), targetScale = 0.8f)) using
                            androidx.compose.animation.SizeTransform(clip = false) { _, _ -> com.flerty.wikiflerty.ui.Motion.tween(220) }
                    },
                    contentAlignment = Alignment.Center,
                    label = "tts-play",
                ) { on ->
                    com.flerty.wikiflerty.ui.ActionLabel(if (on) "Пауза" else "Слушать", accentContent, fontSize = 14.sp, iconSize = 23.dp, icon = if (on) AppIcons.Pause else AppIcons.Play)
                }
            }
            Row(
                Modifier.weight(1f).heightIn(min = 54.dp).liveClick(shape, sound = FeedbackSound.Close, onClick = actions.onStop)
                    .border(1.dp, AppColors.Line, shape),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                com.flerty.wikiflerty.ui.ActionLabel("Стоп", AppColors.Text, fontSize = 14.sp, iconSize = 21.dp, icon = AppIcons.Stop)
            }
        }
    }
    }

    Column(Modifier.clip(appCorner(16)).background(AppColors.Field).animateContentSize(com.flerty.wikiflerty.ui.Motion.tween(300)).padding(horizontal = 14.dp, vertical = 4.dp)) {
        val engines by produceState(emptyList<com.flerty.wikiflerty.tts.TtsEngineInfo>()) { value = ReadAloud.engines(context) }
        androidx.compose.runtime.LaunchedEffect(settings.ttsEngine) {
            if (settings.ttsEngine == NeuralVoices.GEMINI || settings.ttsEngine == NeuralVoices.OPENAI) {
                onSettingsChange { it.copy(ttsEngine = "", ttsVoice = "", ttsVoiceLanguage = "") }
            }
        }
        val piperOk by produceState(false) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.flerty.wikiflerty.tts.PiperVoices.supported() } }
        val neuralOptions = if (piperOk) {
            listOf(PickerOption(NeuralVoices.PIPER, "Офлайн · нейроголос", "Без интернета, голос скачивается один раз", icon = AppIcons.Voice))
        } else {
            emptyList()
        }
        PickerRow(
            label = "Движок",
            icon = AppIcons.Engine,
            options = neuralOptions + listOf(PickerOption("", "Как в системе", engines.firstOrNull()?.label)) +
                engines.map { PickerOption(it.packageName, it.label, it.packageName) },
            selectedId = settings.ttsEngine,
        ) { id ->
            onSettingsChange { it.copy(ttsEngine = id, ttsVoice = if (NeuralVoices.isNeural(id)) NeuralVoices.voices(id).first().id else "", ttsVoiceLanguage = "") }
        }

        val voiceKind = when {
            settings.ttsEngine == NeuralVoices.PIPER -> 0
            NeuralVoices.isNeural(settings.ttsEngine) -> 1
            else -> 2
        }
        androidx.compose.animation.AnimatedContent(
            voiceKind,
            transitionSpec = {
                androidx.compose.animation.fadeIn(com.flerty.wikiflerty.ui.Motion.tween(240, 80)) togetherWith
                    androidx.compose.animation.fadeOut(com.flerty.wikiflerty.ui.Motion.tween(120)) using
                    androidx.compose.animation.SizeTransform(clip = true) { _, _ -> com.flerty.wikiflerty.ui.Motion.tween(300) }
            },
            label = "tts-voices",
        ) { kind ->
        Column {
        if (kind == 0) {
            PiperVoiceList(settings.ttsVoice) { id -> onSettingsChange { it.copy(ttsVoice = id, ttsVoiceLanguage = "") } }
        } else if (kind == 1) {
            val list = NeuralVoices.voices(settings.ttsEngine)
            PickerRow(
                label = "Голос",
                icon = AppIcons.Voice,
                options = list.map { PickerOption(it.id, it.title, it.hint) },
                selectedId = NeuralVoices.voiceOrDefault(settings.ttsEngine, settings.ttsVoice),
                searchable = list.size > 12,
            ) { id -> onSettingsChange { it.copy(ttsVoice = id, ttsVoiceLanguage = "") } }
        } else {
        val voices by produceState(emptyList<TtsVoiceInfo>(), settings.ttsEngine) {
            value = ReadAloud.voices(context, settings.ttsEngine.ifBlank { null })
        }
        val language = bookLanguage?.substringBefore('-')?.lowercase()
        val sorted = voices.sortedWith(
            compareByDescending<TtsVoiceInfo> { it.language.locale.language == language }
                .thenBy { it.needsNetwork }
                .thenByDescending { it.quality }
                .thenBy { it.name },
        )
        PickerRow(
            label = "Голос",
            icon = AppIcons.Voice,
            options = listOf(PickerOption("", "Автоматически", "По языку книги")) + sorted.map { v ->
                PickerOption(
                    v.name,
                    v.name,
                    listOfNotNull(languageName(v), qualityName(v.quality), if (v.needsNetwork) "нужен интернет" else null).joinToString(" · "),
                )
            },
            selectedId = settings.ttsVoice,
            searchable = sorted.size > 8,
        ) { id ->
            val voice = voices.firstOrNull { it.name == id }
            onSettingsChange { it.copy(ttsVoice = voice?.name.orEmpty(), ttsVoiceLanguage = voice?.language?.code.orEmpty()) }
        }
        }
        }
        }
    }

    StepSlider(
        label = "Скорость",
        icon = AppIcons.Speed,
        value = settings.ttsSpeed,
        range = 0.5..3.0,
        step = 0.1,
        format = { String.format(Locale.US, "%.1f×", it) },
        onCommit = { v -> onSettingsChange { it.copy(ttsSpeed = v) } },
    )
    StepSlider(
        label = "Тон голоса",
        icon = AppIcons.Sound,
        value = settings.ttsPitch,
        range = 0.5..2.0,
        step = 0.1,
        format = { String.format(Locale.US, "%.1f", it) },
        onCommit = { v -> onSettingsChange { it.copy(ttsPitch = v) } },
    )

    Group("Цвет подсветки", AppIcons.Palette) {
        DotRow(TtsColors.size + 1) { dot ->
            TtsColors.forEach { color ->
                ColorDot(color, settings.ttsColor == color, onClick = { onSettingsChange { it.copy(ttsColor = color) } }, size = dot)
            }
        }
    }

    Group("Выделение слова", AppIcons.TextSize) {
        val effective = settings.ttsWordColor
        DotRow(TtsColors.size + 1) { dot ->
            ColorDot(0L, effective == 0L, onClick = { onSettingsChange { it.copy(ttsWordColor = 0L) } }, size = dot) {
                Icon(AppIcons.Close, "Без выделения слова", tint = AppColors.Muted, modifier = Modifier.size(14.dp))
            }
            TtsColors.forEach { color ->
                androidx.compose.runtime.key(color) {
                    ColorDot(color, effective == color, onClick = { onSettingsChange { it.copy(ttsWordColor = color) } }, size = dot)
                }
            }
        }
    }

    StepSlider(
        label = "Насыщенность выделения",
        icon = AppIcons.Palette,
        value = settings.ttsStrength,
        range = 0.1..0.9,
        step = 0.05,
        format = { "${kotlin.math.round(it * 100).toInt()}%" },
        onCommit = { v -> onSettingsChange { it.copy(ttsStrength = kotlin.math.round(v * 20) / 20.0) } },
    )

    SwitchRow("Листать за голосом", settings.ttsFollow, icon = AppIcons.Pages) { v -> onSettingsChange { it.copy(ttsFollow = v) } }
    SwitchRow("Голос за листанием", settings.ttsJump, icon = AppIcons.Voice) { v -> onSettingsChange { it.copy(ttsJump = v) } }
    SwitchRow("Читать с места нажатия", settings.ttsTap, icon = AppIcons.Gesture) { v -> onSettingsChange { it.copy(ttsTap = v) } }

    com.flerty.wikiflerty.ui.Appear(active) {
        PickerRow(
            label = "Таймер сна",
            icon = AppIcons.Moon,
            options = listOf(
                PickerOption("off", "Выкл"),
                PickerOption("15", "15 мин"),
                PickerOption("30", "30 мин"),
                PickerOption("45", "45 мин"),
                PickerOption("60", "1 час"),
                PickerOption("chapter", "Конец главы"),
            ),
            selectedId = when (sleep) {
                null -> "off"
                is SleepTimer.Minutes -> sleep.minutes.toString()
                SleepTimer.EndOfChapter -> "chapter"
            },
        ) { id ->
            actions.onSleep(
                when (id) {
                    "off" -> null
                    "chapter" -> SleepTimer.EndOfChapter
                    else -> SleepTimer.Minutes(id.toInt())
                },
            )
        }
    }

    Text(
        "Скачать голоса ›",
        color = AppColors.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.liveClick(appCorner(8), sound = FeedbackSound.Open) { ReadAloud.installVoices(context) }.padding(vertical = 4.dp),
    )
}

@Composable
fun TtsMini(playing: Boolean, onExpand: () -> Unit) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ttsMini")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            com.flerty.wikiflerty.ui.Motion.tween<Float>(900),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(
        Modifier
            .size(36.dp)
            .panelSurface(CircleShape, elevation = 8.dp)
            .liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.85f, onClick = onExpand)
            .drawWithContent {
                drawContent()
                drawCircle(AppColors.Accent.copy(alpha = if (playing) pulse else 0.3f), size.minDimension / 2f - 0.75.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Eye, "Показать чтение вслух", tint = AppColors.Accent, modifier = Modifier.size(18.dp))
    }
}
