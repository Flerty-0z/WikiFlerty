package com.flerty.wikiflerty.reader

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import kotlinx.coroutines.delay
import org.readium.r2.shared.publication.Locator

class SpeedSession(
    val words: List<String>,
    val owners: IntArray,
    val locators: List<Locator>,
    val start: Int,
    val chapterOf: IntArray,
    val chapterTitles: List<String>,
) {
    fun locatorAt(index: Int): Locator? = locators.getOrNull(owners.getOrNull(index.coerceIn(0, (owners.size - 1).coerceAtLeast(0))) ?: -1)
}

object SpeedText {
    private val splitter = Regex("\\s+")

    fun split(text: String): List<String> = text.split(splitter).map { it.trim() }.filter { it.isNotEmpty() }

    fun pivot(word: String): Int {
        val letters = word.count { it.isLetterOrDigit() }
        val n = when {
            letters <= 1 -> 0
            letters <= 5 -> 1
            letters <= 9 -> 2
            letters <= 13 -> 3
            else -> 4
        }
        var seen = -1
        for (i in word.indices) {
            if (word[i].isLetterOrDigit()) {
                seen++
                if (seen == n) return i
            }
        }
        return 0
    }

    fun delayFor(word: String, wpm: Int): Long {
        val base = 60_000.0 / wpm.coerceIn(60, 1500)
        val last = word.trimEnd('»', '"', '”', ')', ']', '\'').lastOrNull()
        val k = when {
            last != null && last in ".!?…" -> 2.2
            last != null && last in ",;:—–" -> 1.5
            word.length > 12 -> 1.5
            word.length > 8 -> 1.25
            else -> 1.0
        }
        return (base * k).toLong()
    }
}

private fun prefs(c: Context) = c.getSharedPreferences("speed_reading", Context.MODE_PRIVATE)

@Composable
fun SpeedReader(session: SpeedSession, onClose: (Int) -> Unit) {
    val context = LocalContext.current
    var wpm by remember { mutableIntStateOf(prefs(context).getInt("wpm", 300)) }
    var index by remember { mutableIntStateOf(session.start.coerceIn(0, (session.words.size - 1).coerceAtLeast(0))) }
    var playing by remember { mutableStateOf(false) }
    var context2 by remember { mutableStateOf(prefs(context).getBoolean("context", true)) }
    var picker by remember { mutableStateOf(false) }
    val total = session.words.size
    BackHandler { onClose(index) }

    LaunchedEffect(playing, wpm) {
        if (!playing) return@LaunchedEffect
        delay(400)
        while (playing && index < total - 1) {
            delay(SpeedText.delayFor(session.words[index], wpm))
            if (!playing) break
            index++
        }
        if (index >= total - 1) playing = false
    }

    fun setWpm(v: Int) {
        wpm = v.coerceIn(100, 1000)
        prefs(context).edit().putInt("wpm", wpm).apply()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(AppIcons.Close, "Закрыть", FeedbackSound.Close) { onClose(index) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Скорочтение", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val chapter = session.chapterOf.getOrNull(index)?.let { session.chapterTitles.getOrNull(it) }.orEmpty()
                    if (chapter.isNotBlank()) Text(chapter, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier
                        .border(1.dp, AppColors.PanelBorder, appCorner(12))
                        .liveClick(appCorner(12), sound = FeedbackSound.Expand) { picker = !picker }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("$wpm сл/мин", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            AnimatedVisibility(picker, enter = fadeIn(Motion.fade(180)), exit = fadeOut(Motion.fade(140))) {
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().border(1.dp, AppColors.PanelBorder, appCorner(14)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RoundButton(AppIcons.Minus, "Медленнее", FeedbackSound.Tick) { setWpm(wpm - 25) }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(200, 300, 400, 500, 600).forEach { v ->
                            val on = v == wpm
                            Box(
                                Modifier
                                    .background(if (on) AppColors.Accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent, appCorner(10))
                                    .liveClick(appCorner(10), sound = FeedbackSound.Segment) { setWpm(v) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) { Text("$v", color = if (on) AppColors.Accent else AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    RoundButton(AppIcons.Plus, "Быстрее", FeedbackSound.Tick) { setWpm(wpm + 25) }
                }
            }

            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .border(1.dp, AppColors.PanelBorder, appCorner(16))
                    .liveClick(appCorner(16), sound = if (context2) FeedbackSound.Collapse else FeedbackSound.Expand, pressedScale = 1f) {
                        context2 = !context2
                        prefs(context).edit().putBoolean("context", context2).apply()
                    }
                    .animateContentSize(Motion.tween(240))
                    .padding(12.dp),
            ) {
                if (context2) {
                    val from = (index - 24).coerceAtLeast(0)
                    val to = (index + 36).coerceAtMost(total)
                    Text(
                        buildAnnotatedString {
                            for (i in from until to) {
                                if (i == index) withStyle(SpanStyle(color = AppColors.Accent, fontWeight = FontWeight.Bold)) { append(session.words[i]) }
                                else append(session.words[i])
                                append(' ')
                            }
                        },
                        color = AppColors.Muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text("Показать текст вокруг", color = AppColors.Faint, fontSize = 13.sp)
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .liveClick(appCorner(20), sound = if (playing) FeedbackSound.Pause else FeedbackSound.Play, pressedScale = 1f) { playing = !playing },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(2.dp).height(18.dp).background(AppColors.Accent.copy(alpha = 0.5f)))
                    val word = session.words.getOrNull(index).orEmpty()
                    val p = SpeedText.pivot(word)
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(word.take(p), color = AppColors.Text, fontSize = 40.sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                        Text(word.getOrNull(p)?.toString().orEmpty(), color = AppColors.Accent, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        Text(if (word.isEmpty()) "" else word.substring((p + 1).coerceAtMost(word.length)), color = AppColors.Text, fontSize = 40.sp, textAlign = TextAlign.Start, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                    }
                    Box(Modifier.width(2.dp).height(18.dp).background(AppColors.Accent.copy(alpha = 0.5f)))
                    Spacer(Modifier.height(18.dp))
                    Text(if (playing) "Нажми, чтобы остановить" else "Нажми, чтобы начать", color = AppColors.Faint, fontSize = 12.sp)
                }
            }

            val left = ((total - 1 - index).coerceAtLeast(0) * 60.0 / wpm).toInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1} из $total", color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(if (left >= 60) "≈ ${left / 60} мин" else "≈ $left сек", color = AppColors.Faint, fontSize = 12.sp)
            }
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).background(AppColors.Line, CircleShape)) {
                Box(Modifier.fillMaxWidth(if (total > 1) index / (total - 1f) else 0f).fillMaxHeight().background(AppColors.Accent, CircleShape))
            }
            Row(
                Modifier.padding(top = 14.dp, bottom = 6.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(AppIcons.SkipPrev, "Назад на 15 слов", FeedbackSound.SkipBack) { index = (index - 15).coerceAtLeast(0) }
                RoundButton(AppIcons.Back, "Предыдущее слово", FeedbackSound.Tick) { playing = false; index = (index - 1).coerceAtLeast(0) }
                Box(
                    Modifier.size(64.dp).background(AppColors.Accent, CircleShape).liveClick(CircleShape, sound = if (playing) FeedbackSound.Pause else FeedbackSound.Play) { playing = !playing },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (playing) AppIcons.Pause else AppIcons.Play, if (playing) "Пауза" else "Читать", tint = AppColors.Background, modifier = Modifier.size(28.dp)) }
                RoundButton(AppIcons.Forward, "Следующее слово", FeedbackSound.Tick) { playing = false; index = (index + 1).coerceAtMost(total - 1) }
                RoundButton(AppIcons.SkipNext, "Вперёд на 15 слов", FeedbackSound.SkipNext) { index = (index + 15).coerceAtMost(total - 1) }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, sound: FeedbackSound, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).border(1.dp, AppColors.PanelBorder, CircleShape).liveClick(CircleShape, sound = sound, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
}
