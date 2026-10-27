package com.flerty.wikiflerty.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
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
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import java.util.Locale

val TtsColors = listOf(0xFFF2C46B, 0xFF7ED3A0, 0xFF8AB4F8, 0xFFF28B9B, 0xFFC3A6F5, 0xFFB0B4BC)

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
fun TtsBar(playing: Boolean, speed: Double, actions: TtsActions, modifier: Modifier = Modifier) {
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
        BarButton(AppIcons.SkipPrev, "Предыдущее предложение", FeedbackSound.Tap, actions.onPrevious)
        val shape = CircleShape
        Box(
            Modifier.size(54.dp).liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.9f, onClick = actions.onToggle).accentHighlight(shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) AppIcons.Pause else AppIcons.Play, if (playing) "Пауза" else "Продолжить", tint = accentContent, modifier = Modifier.size(24.dp))
        }
        BarButton(AppIcons.SkipNext, "Следующее предложение", FeedbackSound.Tap, actions.onNext)
        Box(
            Modifier.size(48.dp).liveClick(CircleShape, sound = FeedbackSound.Open, onClick = actions.onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text(String.format(Locale.US, "%.1f×", speed), color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
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
fun TtsProblemPrompt(problem: ReadAloud.Problem, onInstall: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
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
    if (!active) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = actions.onStart).accentHighlight(shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(AppIcons.Play, null, tint = accentContent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Слушать с этой страницы", color = accentContent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).heightIn(min = 54.dp).liveClick(shape, sound = FeedbackSound.Toggle, onClick = actions.onToggle).accentHighlight(shape),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(if (playing) AppIcons.Pause else AppIcons.Play, null, tint = accentContent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (playing) "Пауза" else "Продолжить", color = accentContent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Row(
                Modifier.weight(1f).heightIn(min = 54.dp).liveClick(shape, sound = FeedbackSound.Close, onClick = actions.onStop)
                    .border(1.dp, AppColors.Line, shape),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(AppIcons.Stop, null, tint = AppColors.Text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Остановить", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }

    Column(Modifier.clip(appCorner(16)).background(AppColors.Field).padding(horizontal = 14.dp, vertical = 4.dp)) {
        val engines by produceState(emptyList<com.flerty.wikiflerty.tts.TtsEngineInfo>()) { value = ReadAloud.engines(context) }
        PickerRow(
            label = "Движок",
            icon = AppIcons.Engine,
            options = listOf(PickerOption("", "Как в системе", engines.firstOrNull()?.label)) +
                engines.map { PickerOption(it.packageName, it.label, it.packageName) },
            selectedId = settings.ttsEngine,
        ) { id -> onSettingsChange { it.copy(ttsEngine = id, ttsVoice = "", ttsVoiceLanguage = "") } }

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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TtsColors.forEach { color ->
                val selected = settings.ttsColor == color
                Box(
                    Modifier.size(40.dp)
                        .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.85f) { onSettingsChange { it.copy(ttsColor = color) } }
                        .border(2.dp, if (selected) AppColors.Text else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color))) }
            }
        }
    }

    SwitchRow("Листать за голосом", settings.ttsFollow, icon = AppIcons.Pages) { v -> onSettingsChange { it.copy(ttsFollow = v) } }

    if (active) {
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
    Hint("Новые движки ставятся из Play Маркета: RHVoice, Sherpa TTS — и сразу появляются здесь.")
}
