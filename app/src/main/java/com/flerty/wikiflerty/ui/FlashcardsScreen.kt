package com.flerty.wikiflerty.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.DictionaryEntry
import com.flerty.wikiflerty.data.FlashcardPrefs
import com.flerty.wikiflerty.data.Grade
import com.flerty.wikiflerty.data.Srs
import com.flerty.wikiflerty.data.SrsState
import java.util.Locale
import kotlinx.coroutines.launch

private const val LEARN_AHEAD = 20 * 60_000L

data class StudyCounts(val new: Int, val learning: Int, val review: Int) {
    val total: Int get() = new + learning + review
}

object StudyQueue {
    fun build(entries: List<DictionaryEntry>, prefs: FlashcardPrefs, extraNew: Int = 0, now: Long = System.currentTimeMillis()): List<String> {
        val learning = entries.filter { it.srs.isLearning && it.srs.due <= now + LEARN_AHEAD }.sortedBy { it.srs.due }
        val review = entries.filter { it.srs.isReview && it.srs.due <= now }.sortedBy { it.srs.due }
        val fresh = entries.filter { it.srs.isNew }.sortedBy { it.createdAt }.take(prefs.newLeftToday() + extraNew)
        val queue = learning.map { it.id }.toMutableList()
        val reviews = review.map { it.id }.toMutableList()
        val news = fresh.map { it.id }.toMutableList()
        var i = 0
        while (reviews.isNotEmpty() || news.isNotEmpty()) {
            if (news.isNotEmpty() && (i % 4 == 3 || reviews.isEmpty())) queue += news.removeAt(0) else queue += reviews.removeAt(0)
            i++
        }
        return queue
    }

    fun counts(entries: List<DictionaryEntry>, prefs: FlashcardPrefs, now: Long = System.currentTimeMillis()) = StudyCounts(
        new = minOf(prefs.newLeftToday(), entries.count { it.srs.isNew }),
        learning = entries.count { it.srs.isLearning && it.srs.due <= now + LEARN_AHEAD },
        review = entries.count { it.srs.isReview && it.srs.due <= now },
    )

    fun nextDue(entries: List<DictionaryEntry>, now: Long = System.currentTimeMillis()): Long? =
        entries.filter { !it.srs.isNew && it.srs.due > now }.minOfOrNull { it.srs.due }
}

fun srsBadge(s: SrsState, now: Long = System.currentTimeMillis()): Pair<String, Color> = when {
    s.isNew -> "Новое" to AppColors.Info
    s.isLearning -> "Учу" to AppColors.Warn
    s.isMastered -> "Выучено" to AppColors.Good
    s.due <= now -> "Повторить" to AppColors.Warn
    else -> "через ${Srs.wait(s.due - now)}" to AppColors.Muted
}

private fun maskWord(text: String, word: String): String {
    val w = word.trim()
    if (w.isEmpty()) return text
    val stem = if (w.length > 5 && !w.contains(' ')) w.dropLast(2) else w
    return Regex("(?i)" + Regex.escape(stem) + "\\p{L}*").replace(text, "＿＿＿")
}

private fun highlightWord(text: String, word: String, color: Color): AnnotatedString {
    val w = word.trim()
    val stem = if (w.length > 5 && !w.contains(' ')) w.dropLast(2) else w
    val match = if (stem.isEmpty()) null else Regex("(?i)" + Regex.escape(stem) + "\\p{L}*").find(text)
    return buildAnnotatedString {
        if (match == null) {
            append(text)
        } else {
            append(text.substring(0, match.range.first))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(match.value) }
            append(text.substring(match.range.last + 1))
        }
    }
}

private class WordSpeaker(context: Context, engine: String) {
    private var ready = false
    private val tts: TextToSpeech = if (engine.isBlank()) {
        TextToSpeech(context) { ready = it == TextToSpeech.SUCCESS }
    } else {
        TextToSpeech(context, { ready = it == TextToSpeech.SUCCESS }, engine)
    }

    fun speak(text: String) {
        if (!ready) return
        val cyrillic = text.any { it in 'Ѐ'..'ӿ' }
        runCatching { tts.language = if (cyrillic) Locale("ru") else Locale.ENGLISH }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word")
    }

    fun shutdown() = runCatching { tts.shutdown() }
}

private class Undo(val id: String, val before: SrsState, val queue: List<String>, val answered: Int, val correct: Int, val wasNew: Boolean)

@Composable
fun FlashcardSession(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val entries by app.dictionary.entries.collectAsStateWithLifecycle()
    val prefs by app.flashcards.prefs.collectAsStateWithLifecycle()
    val readerSettings by app.readerSettings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val queue = remember { mutableStateListOf<String>().apply { addAll(StudyQueue.build(app.dictionary.entries.value, app.flashcards.prefs.value)) } }
    var flippedId by remember { mutableStateOf<String?>(null) }
    var answered by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var undo by remember { mutableStateOf<Undo?>(null) }
    var extraNew by remember { mutableIntStateOf(0) }
    val speaker = remember { WordSpeaker(context, readerSettings.ttsEngine) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    BackHandler(onBack = onClose)

    val byId = remember(entries) { entries.associateBy { it.id } }
    LaunchedEffect(byId) { queue.removeAll { it !in byId } }
    val current = queue.firstOrNull()?.let { byId[it] }

    fun answer(entry: DictionaryEntry, grade: Grade) {
        val now = System.currentTimeMillis()
        val before = entry.srs
        val after = Srs.next(before, grade, now)
        undo = Undo(entry.id, before, queue.toList(), answered, correct, before.isNew)
        scope.launch { app.dictionary.saveReview(entry.id, after) }
        val ok = grade != Grade.Again
        app.flashcards.recordAnswer(ok, wasNew = before.isNew)
        answered++
        if (ok) correct++
        queue.removeAt(0)
        if (after.step >= 0 && after.due - now <= LEARN_AHEAD) {
            val position = if (after.due - now <= 2 * 60_000L) 3 else 7
            queue.add(minOf(position, queue.size), entry.id)
        }
        flippedId = null
    }

    fun undoLast() {
        val u = undo ?: return
        scope.launch { app.dictionary.saveReview(u.id, u.before) }
        app.flashcards.update { p ->
            val today = java.time.LocalDate.now().toString()
            val (n, okCount) = p.history[today] ?: (0 to 0)
            val wasCorrect = correct > u.correct
            p.copy(
                history = p.history + (today to (maxOf(0, n - 1) to maxOf(0, okCount - if (wasCorrect) 1 else 0))),
                newCount = if (u.wasNew && p.newDay == today) maxOf(0, p.newCount - 1) else p.newCount,
            )
        }
        queue.clear()
        queue.addAll(u.queue)
        answered = u.answered
        correct = u.correct
        flippedId = u.id
        undo = null
    }

    val now = System.currentTimeMillis()
    val newLeft = queue.count { byId[it]?.srs?.isNew == true }
    val learnLeft = queue.count { byId[it]?.srs?.isLearning == true }
    val reviewLeft = queue.count { byId[it]?.srs?.isReview == true }

    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(AppIcons.Close, "Закончить", onClose)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Карточки", color = AppColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (queue.isEmpty()) "Готово" else "Осталось ${queue.size}",
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                )
            }
            AnimatedVisibility(visible = undo != null) {
                RoundButton(AppIcons.Back, "Отменить последний ответ") { undoLast() }
            }
        }

        if (current == null) {
            FinishedView(
                answered = answered,
                correct = correct,
                entries = entries,
                moreNewAvailable = entries.count { it.srs.isNew } > 0,
                onMoreNew = {
                    extraNew += 10
                    queue.addAll(StudyQueue.build(app.dictionary.entries.value, app.flashcards.prefs.value, extraNew).filterNot { it in queue })
                },
                onClose = onClose,
                modifier = Modifier.weight(1f),
            )
        } else {
            StudyBody(
                current = current,
                byId = byId,
                queue = queue,
                answered = answered,
                prefs = prefs,
                flippedId = flippedId,
                speaker = speaker,
                onFlip = { flippedId = it },
                onAnswer = { entry, grade -> answer(entry, grade) },
                newLeft = newLeft,
                learnLeft = learnLeft,
                reviewLeft = reviewLeft,
                now = now,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StudyBody(
    current: DictionaryEntry,
    byId: Map<String, DictionaryEntry>,
    queue: List<String>,
    answered: Int,
    prefs: FlashcardPrefs,
    flippedId: String?,
    speaker: WordSpeaker,
    onFlip: (String?) -> Unit,
    onAnswer: (DictionaryEntry, Grade) -> Unit,
    newLeft: Int,
    learnLeft: Int,
    reviewLeft: Int,
    now: Long,
) {
    run {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CountChip("Новые", newLeft, AppColors.Info)
            CountChip("Учу", learnLeft, AppColors.Warn)
            CountChip("Повтор", reviewLeft, AppColors.Good)
        }
        val total = answered + queue.size
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AppColors.Line)) {
            Box(Modifier.fillMaxWidth(if (total == 0) 0f else answered.toFloat() / total).fillMaxHeight().clip(CircleShape).background(AppColors.Accent))
        }

        val reverse = when (prefs.direction) {
            "meaning" -> true
            "mixed" -> (current.id.hashCode() + current.srs.reps) % 2 != 0
            else -> false
        }
        AnimatedContent(
            targetState = current.id,
            transitionSpec = {
                (slideInHorizontally(Motion.tween(280)) { it / 3 } + fadeIn(Motion.fade(220)))
                    .togetherWith(slideOutHorizontally(Motion.tween(240)) { -it / 3 } + fadeOut(Motion.fade(180)))
            },
            label = "card",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { id ->
            byId[id]?.let { entry ->
            LaunchedEffect(id) { if (prefs.autoSpeak && !reverse) speaker.speak(entry.word) }
            FlipCard(
                entry = entry,
                flipped = flippedId == id,
                reverse = reverse,
                onFlip = {
                    onFlip(if (flippedId == id) null else id)
                    if (flippedId != id && reverse && prefs.autoSpeak) speaker.speak(entry.word)
                },
                onSpeak = { speaker.speak(entry.word) },
            )
            }
        }

        if (flippedId != current.id) {
            val shape = appCorner(18)
            Box(
                Modifier.fillMaxWidth().padding(bottom = 40.dp).heightIn(min = 56.dp)
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f) { onFlip(current.id) }
                    .accentHighlight(shape),
                contentAlignment = Alignment.Center,
            ) { Text("Показать ответ", color = accentContent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradeButton("Снова", Srs.label(current.srs, Grade.Again, now), AppColors.Bad, Modifier.weight(1f)) { onAnswer(current, Grade.Again) }
                GradeButton("Трудно", Srs.label(current.srs, Grade.Hard, now), AppColors.Warn, Modifier.weight(1f)) { onAnswer(current, Grade.Hard) }
                GradeButton("Хорошо", Srs.label(current.srs, Grade.Good, now), AppColors.Good, Modifier.weight(1f)) { onAnswer(current, Grade.Good) }
                GradeButton("Легко", Srs.label(current.srs, Grade.Easy, now), AppColors.Accent, Modifier.weight(1f), filled = true) { onAnswer(current, Grade.Easy) }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onClick).background(AppColors.Shade.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = AppColors.Text, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun CountChip(label: String, count: Int, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.13f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(count.toString(), color = AppColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GradeButton(title: String, wait: String, color: Color, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val shape = appCorner(18)
    Column(
        modifier
            .heightIn(min = 60.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.93f, onClick = onClick)
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            color = if (filled) AppColors.OnAccent else color,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
        )
        Text(wait, color = if (filled) AppColors.OnAccent.copy(alpha = 0.8f) else AppColors.Muted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun FlipCard(entry: DictionaryEntry, flipped: Boolean, reverse: Boolean, onFlip: () -> Unit, onSpeak: () -> Unit) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, Motion.tween(420), label = "flip")
    val shape = appCorner(26)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .liveClick(shape, sound = FeedbackSound.Tap, pressedScale = 0.99f, onClick = onFlip)
            .fieldSurface(shape),
    ) {
        if (rotation <= 90f) {
            CardFront(entry, reverse, onSpeak)
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { CardBack(entry, onSpeak) }
        }
    }
}

@Composable
private fun SpeakButton(onSpeak: () -> Unit) {
    Box(
        Modifier.size(44.dp).liveClick(CircleShape, sound = FeedbackSound.Tap, onClick = onSpeak).background(AppColors.Accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.Sound, "Произнести", tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun CardFront(entry: DictionaryEntry, reverse: Boolean, onSpeak: () -> Unit) {
    var hint by remember(entry.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (reverse) "Какое это слово?" else "Что это значит?",
            color = AppColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (reverse) {
            val plain = markdown(entry.definition).text
            Text(
                maskWord(plain, entry.word).take(320),
                color = AppColors.Text,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                maxLines = 9,
                overflow = TextOverflow.Ellipsis,
            )
            if (hint) {
                Text(
                    "${entry.word.trim().take(1)}… · букв: ${entry.word.trim().length}",
                    color = AppColors.Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Text(
                entry.word,
                color = AppColors.Text,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            SpeakButton(onSpeak)
            if (hint && entry.context.isNotBlank()) {
                Text(
                    highlightWord("«${entry.context.take(260)}»", entry.word, AppColors.Accent),
                    color = AppColors.Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        val canHint = reverse || entry.context.isNotBlank()
        if (canHint && !hint) {
            Row(
                Modifier.liveClick(appCorner(12), sound = FeedbackSound.Tap) { hint = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(if (reverse) AppIcons.Bulb else AppIcons.Eye, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                Text(
                    if (reverse) "Подсказка" else "Показать в книге",
                    color = AppColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        if (entry.bookId != null || entry.bookTitle != null) BookTag(entry.bookId, entry.bookTitle, coverWidth = 28.dp)
    }
}

@Composable
private fun CardBack(entry: DictionaryEntry, onSpeak: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.word,
                color = AppColors.Text,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            SpeakButton(onSpeak)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Line))
        Text(markdown(entry.definition), color = AppColors.Text, fontSize = 15.sp, lineHeight = 22.sp)
        if (entry.context.isNotBlank()) {
            Column(
                Modifier.fillMaxWidth().clip(appCorner(14)).background(AppColors.Shade.copy(alpha = 0.05f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    highlightWord("«${entry.context}»", entry.word, AppColors.Accent),
                    color = AppColors.Text.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        if (entry.bookId != null || entry.bookTitle != null) BookTag(entry.bookId, entry.bookTitle, coverWidth = 28.dp)
    }
}

@Composable
private fun FinishedView(
    answered: Int,
    correct: Int,
    entries: List<DictionaryEntry>,
    moreNewAvailable: Boolean,
    onMoreNew: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val mastered = entries.count { it.srs.isMastered }
    val next = StudyQueue.nextDue(entries)
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(AppColors.Good.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Check, null, tint = AppColors.Good, modifier = Modifier.size(40.dp)) }
        Text(
            if (answered > 0) "На сегодня всё!" else "Повторять пока нечего",
            color = AppColors.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            next?.let { "Следующие слова — через ${Srs.wait(it - System.currentTimeMillis())}" } ?: "Добавляй слова из книг — они появятся здесь",
            color = AppColors.Muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Ответов", answered.toString(), Modifier.weight(1f))
            StatTile("Верно", if (answered > 0) "${correct * 100 / answered}%" else "—", Modifier.weight(1f))
            StatTile("Выучено", mastered.toString(), Modifier.weight(1f))
        }
        if (moreNewAvailable) {
            val shape = appCorner(16)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    .liveClick(shape, sound = FeedbackSound.Open, onClick = onMoreNew)
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape),
                contentAlignment = Alignment.Center,
            ) { Text("Ещё 10 новых слов", color = AppColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
        val shape = appCorner(16)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .liveClick(shape, sound = FeedbackSound.Close, onClick = onClose)
                .accentHighlight(shape),
            contentAlignment = Alignment.Center,
        ) { Text("К словарю", color = accentContent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun StudyCard(entries: List<DictionaryEntry>, onStart: () -> Unit, onSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val prefs by app.flashcards.prefs.collectAsStateWithLifecycle()
    val counts = StudyQueue.counts(entries, prefs)
    val mastered = entries.count { it.srs.isMastered }
    val today = prefs.history[java.time.LocalDate.now().toString()]?.first ?: 0
    val shape = appCorner(22)
    Column(
        Modifier
            .fillMaxWidth()
            .fieldSurface(shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Сегодня повторить", color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Выучено $mastered из ${entries.size}" + if (today > 0) " · сегодня $today" else "",
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Box(
                Modifier.size(40.dp).liveClick(CircleShape, sound = FeedbackSound.Open, onClick = onSettings).background(AppColors.Shade.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Options, "Настройки карточек", tint = AppColors.Text, modifier = Modifier.size(18.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountChip("Новые", counts.new, AppColors.Info)
            CountChip("Учу", counts.learning, AppColors.Warn)
            CountChip("Повтор", counts.review, AppColors.Good)
        }
        val button = appCorner(16)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .liveClick(button, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = onStart)
                .accentHighlight(button),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(AppIcons.Play, null, tint = accentContent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (counts.total > 0) "Учить · ${counts.total}" else "Повторять пока нечего",
                color = accentContent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun Color.compositeOverField(): Color = androidx.compose.ui.graphics.Color(
    red = red * alpha + AppColors.Field.red * (1 - alpha),
    green = green * alpha + AppColors.Field.green * (1 - alpha),
    blue = blue * alpha + AppColors.Field.blue * (1 - alpha),
)

@Composable
fun FlashcardSettingsSheet(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val prefs by app.flashcards.prefs.collectAsStateWithLifecycle()
    AppSheet(onDismiss, title = "Карточки") {
        SettingsGroup(null) {
            PickerRow(
                label = "Новых слов в день",
                icon = AppIcons.Plus,
                options = listOf(5, 10, 15, 20, 30, 50).map { PickerOption(it.toString(), it.toString()) },
                selectedId = prefs.newPerDay.toString(),
            ) { id -> app.flashcards.update { it.copy(newPerDay = id.toInt()) } }
            PickerRow(
                label = "Направление",
                icon = AppIcons.Translate,
                options = listOf(
                    PickerOption("word", "Слово → значение", "Видишь слово, вспоминаешь смысл"),
                    PickerOption("meaning", "Значение → слово", "Видишь объяснение, вспоминаешь слово"),
                    PickerOption("mixed", "Вперемешку", "Оба способа по очереди"),
                ),
                selectedId = prefs.direction,
            ) { id -> app.flashcards.update { it.copy(direction = id) } }
            SettingSwitch("Произносить слово", prefs.autoSpeak, hint = "Голосом из «Чтения вслух»", icon = AppIcons.Sound) { v ->
                app.flashcards.update { it.copy(autoSpeak = v) }
            }
        }
        Text(
            "«Снова» — слово вернётся через минуту. «Хорошо» — через всё более длинные промежутки: день, неделя, месяц. Так слова запоминаются надолго.",
            color = AppColors.Faint,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
