package com.flerty.wikiflerty.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.key
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.PaperSession
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SessionFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))
private val FinishFormat = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

private fun pagesWord(n: Int) = StatsCalc.plural(n, "страница", "страницы", "страниц")

@Composable
fun PaperBookSheet(book: Book?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var title by remember { mutableStateOf(book?.title.orEmpty()) }
    var author by remember { mutableStateOf(book?.author.orEmpty()) }
    var pages by remember { mutableStateOf(book?.positions?.takeIf { it > 0 }?.toString().orEmpty()) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var shot by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) cover = uri }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cover = shot }
    val preview by produceState<ImageBitmap?>(null, cover) {
        value = cover?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) }?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val total = pages.toIntOrNull()
    val ready = title.isNotBlank() && total != null && total in 1..20000

    AppSheet(onDismiss, title = if (book == null) "Бумажная книга" else "Изменить книгу") {
        val close = sheetClose(onDismiss)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(width = 84.dp, height = 126.dp)
                    .clip(appCorner(10))
                    .background(Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.35f), AppColors.Accent.copy(alpha = 0.12f))))
                    .border(1.dp, AppColors.PanelBorder, appCorner(10)),
                contentAlignment = Alignment.Center,
            ) {
                val img = preview
                when {
                    img != null -> androidx.compose.foundation.Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSizeCompat())
                    book != null && book.coverFileName != null -> SmallCover(app.library.coverFile(book), Modifier.matchParentSizeCompat())
                    else -> Icon(AppIcons.Book, null, tint = AppColors.Accent, modifier = Modifier.size(30.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Обложка", color = AppColors.Muted, fontSize = 13.sp)
                CoverButton(AppIcons.Image, "Из галереи") { pick.launch("image/*") }
                CoverButton(AppIcons.Camera, "Сфотографировать") {
                    val dir = File(context.cacheDir, "share").apply { mkdirs() }
                    val file = File(dir, "paper-cover-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    shot = uri
                    camera.launch(uri)
                }
            }
        }
        PaperField(title, { title = it.take(200) }, "Название", AppIcons.Book)
        PaperField(author, { author = it.take(120) }, "Автор", AppIcons.Pencil)
        PaperField(pages, { v -> pages = v.filter { it.isDigit() }.take(5) }, "Сколько страниц в книге", AppIcons.Pages, KeyboardType.Number)
        AppButton(if (book == null) "Добавить на полку" else "Сохранить", primary = true, modifier = Modifier.fillMaxWidth()) {
            if (!ready) return@AppButton
            app.appScope.launch {
                if (book == null) app.library.addPaper(title, author, total!!, cover) else app.library.editPaper(book.id, title, author, total!!, cover)
            }
            close()
        }
        if (!ready && (title.isNotEmpty() || pages.isNotEmpty())) {
            Text("Нужны название и число страниц", color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().fillMaxHeight()

@Composable
private fun CoverButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val shape = appCorner(14)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).liveClick(shape, sound = FeedbackSound.Open, onClick = onClick).fieldSurface(shape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
        Text(label, color = AppColors.Text, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun PaperField(value: String, onChange: (String) -> Unit, hint: String, icon: androidx.compose.ui.graphics.vector.ImageVector, type: KeyboardType = KeyboardType.Text) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = AppColors.Text, fontSize = 16.sp),
        cursorBrush = SolidColor(AppColors.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().height(52.dp).fieldSurface(appCorner(16)).padding(horizontal = 14.dp),
        decorationBox = { inner ->
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (value.isEmpty()) Text(hint, color = AppColors.Faint, fontSize = 16.sp, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

@Composable
fun PaperMain(book: Book, onEdit: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val all by app.paper.sessions.collectAsStateWithLifecycle()
    val watch by app.paper.stopwatch.collectAsStateWithLifecycle()
    val sessions = remember(all, book.statsKey) { all.filter { it.book == book.statsKey }.sortedByDescending { it.startAt } }
    val pace = remember(all, book.positions) { app.paper.pace(book) }
    var form by remember { mutableStateOf<SessionForm?>(null) }
    var celebrate by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val current = sessions.maxOfOrNull { it.toPage } ?: 0
    val mine = watch?.takeIf { it.bookId == book.id }

    AnimatedContent(
        targetState = mine,
        contentKey = { it != null },
        transitionSpec = {
            val enter = fadeIn(Motion.fade(260)) + scaleIn(Motion.tween(320), initialScale = 0.9f)
            val exit = fadeOut(Motion.fade(160)) + scaleOut(Motion.tween(200), targetScale = 0.94f)
            (enter togetherWith exit).using(SizeTransform(clip = false) { _, _ -> Motion.tween(320) })
        },
        label = "paper-session",
    ) { shown ->
    if (shown != null) {
        StopwatchCard(
            shown,
            onToggle = { if (shown.running) app.paper.pauseStopwatch() else app.paper.startStopwatch(book.id) },
            onStop = {
                val elapsed = shown.elapsed()
                app.paper.pauseStopwatch()
                form = SessionForm(null, current, current, shown.startedAt, elapsed, fromStopwatch = true)
            },
            onCancel = { app.paper.stopStopwatch() },
        )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = appCorner(18)
            Row(
                Modifier
                    .weight(1.4f)
                    .heightIn(min = 56.dp)
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f) { app.paper.startStopwatch(book.id) }
                    .accentHighlight(shape),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Play, null, tint = accentContent, modifier = Modifier.size(20.dp))
                Text("Начать сеанс", color = accentContent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f) {
                        form = SessionForm(null, current, current, System.currentTimeMillis(), 0L, fromStopwatch = false)
                    }
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.5f), shape),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Plus, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                Text("Вручную", color = AppColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (watch != null) {
            Text("Сейчас идёт сеанс другой книги — новый сеанс его заменит", color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        }
      }
    }
    }

    if (sessions.isNotEmpty()) {
        SettingsGroup("Темп") {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Скорость", pace.pagesPerHour?.let { "${it.toInt()} стр/ч" } ?: "—", Modifier.weight(1f), icon = AppIcons.Speed)
                    StatTile("Всего", StatsCalc.duration(pace.totalMs), Modifier.weight(1f), hint = "${sessions.size} " + StatsCalc.plural(sessions.size, "сеанс", "сеанса", "сеансов"), icon = AppIcons.Clock)
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        "Осталось",
                        when {
                            pace.leftPages == 0 -> "Прочитано"
                            pace.leftMs != null -> "≈ " + StatsCalc.duration(pace.leftMs)
                            else -> "${pace.leftPages} стр."
                        },
                        Modifier.weight(1f),
                        hint = if (pace.leftPages > 0) "${pace.leftPages} ${pagesWord(pace.leftPages)}" else null,
                        icon = AppIcons.Pages,
                    )
                    StatTile(
                        "Финиш",
                        pace.finishDate?.let { "≈ " + FinishFormat.format(it) } ?: if (pace.leftPages == 0) "🎉" else "—",
                        Modifier.weight(1f),
                        hint = pace.leftDays?.takeIf { pace.leftPages > 0 }?.let { "через ${StatsCalc.days(it)}" } ?: if (pace.pagesPerHour == null && pace.leftPages > 0) "засеки время сеанса" else null,
                        icon = AppIcons.Calendar,
                    )
                }
            }
        }
    }

    SettingsGroup("Сеансы") {
        if (sessions.isEmpty()) {
            Text(
                "Пока пусто. Нажми «Начать сеанс», когда сядешь читать, или добавь прочитанное вручную.",
                color = AppColors.Faint,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            val total = book.positions.coerceAtLeast(1)
            val fraction by animateFloatAsState((current.toFloat() / total).coerceIn(0f, 1f), Motion.tween(800), label = "paper")
            Row(Modifier.padding(vertical = 10.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(34.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${(fraction * 100).toInt()}%", color = AppColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Box(Modifier.weight(1f).width(12.dp).clip(CircleShape).background(AppColors.Line), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(AppColors.AccentLight, AppColors.Accent))),
                        )
                    }
                    Icon(AppIcons.Book, null, tint = AppColors.Faint, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (showAll) sessions else sessions.take(6)).forEach { s ->
                        SessionCard(s, total) {
                            form = SessionForm(s, s.fromPage, s.toPage, s.startAt, s.durationMs, fromStopwatch = false)
                        }
                    }
                    if (sessions.size > 6) {
                        Text(
                            if (showAll) "Свернуть" else "Показать все · ${sessions.size}",
                            color = AppColors.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.liveClick(appCorner(8), sound = FeedbackSound.Toggle) { showAll = !showAll }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    SettingsNavRow(AppIcons.Pencil, "Изменить книгу", "Название, автор, страницы, обложка", onEdit)

    form?.let { f ->
        SessionSheet(book, f, onSaved = { reached ->
            if (f.fromStopwatch) app.paper.stopStopwatch()
            if (reached) celebrate = true
        }) {
            form = null
        }
    }
    if (celebrate) RatingSheet(book.id, celebrate = true) { celebrate = false }
}

data class SessionForm(
    val session: PaperSession?,
    val from: Int,
    val to: Int,
    val startAt: Long,
    val durationMs: Long,
    val fromStopwatch: Boolean,
)

@Composable
private fun StopwatchCard(watch: com.flerty.wikiflerty.data.Stopwatch, onToggle: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(watch) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val shape = appCorner(22)
    Column(
        Modifier.fillMaxWidth().background(AppColors.AccentTint, shape).border(1.dp, AppColors.Accent.copy(alpha = 0.45f), shape).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
            1f,
            0.25f,
            infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "dot",
        )
        AnimatedContent(
            targetState = watch.running,
            transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(140)) },
            label = "watch-state",
        ) { running ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (running) Box(Modifier.size(7.dp).graphicsLayer { alpha = pulse.value }.background(AppColors.Accent, CircleShape))
                Text(if (running) "Идёт сеанс чтения" else "Пауза", color = if (running) AppColors.Accent else AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        RollingClock(clock(watch.elapsed(now)), Modifier.graphicsLayer { alpha = if (watch.running) 1f else 0.6f + 0.4f * pulse.value })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onCancel).border(1.dp, AppColors.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, "Отменить сеанс", tint = AppColors.Muted, modifier = Modifier.size(18.dp)) }
            Box(
                Modifier.size(64.dp).liveClick(CircleShape, sound = FeedbackSound.Toggle, onClick = onToggle).accentHighlight(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = watch.running,
                    transitionSpec = { (fadeIn(Motion.fade(180)) + scaleIn(Motion.tween(220), initialScale = 0.5f)) togetherWith (fadeOut(Motion.fade(120)) + scaleOut(Motion.tween(160), targetScale = 0.5f)) },
                    label = "watch-icon",
                ) { running ->
                    Icon(if (running) AppIcons.Pause else AppIcons.Play, if (running) "Пауза" else "Продолжить", tint = accentContent, modifier = Modifier.size(28.dp))
                }
            }
            Box(
                Modifier.size(48.dp).liveClick(CircleShape, sound = FeedbackSound.Open, onClick = onStop).border(1.dp, AppColors.Accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Stop, "Завершить", tint = AppColors.Accent, modifier = Modifier.size(18.dp)) }
        }
        Text("Можно свернуть приложение — время идёт", color = AppColors.Faint, fontSize = 11.5.sp)
    }
}

@Composable
private fun RollingClock(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            key(text.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(Motion.tween(260)) { -it / 2 } + fadeIn(Motion.fade(200))) togetherWith
                            (slideOutVertically(Motion.tween(220)) { it / 2 } + fadeOut(Motion.fade(160)))
                    },
                    label = "digit",
                ) { c ->
                    Text(c.toString(), color = AppColors.Text, fontSize = 44.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SessionCard(s: PaperSession, total: Int, onClick: () -> Unit) {
    val shape = appCorner(16)
    Column(
        Modifier.fillMaxWidth().liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick).fieldSurface(shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("+${s.pages} ${pagesWord(s.pages)}", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
            if (s.toPage >= total) {
                Text("Прочитано", color = AppColors.Good, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(AppColors.Good.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Text("${s.toPage}/$total · ${(s.toPage * 100 / total.coerceAtLeast(1))}%", color = AppColors.Accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        val speed = if (s.durationMs >= 60_000 && s.pages > 0) " · ${(s.pages / (s.durationMs / 3_600_000.0)).toInt()} стр/ч" else ""
        Text(
            SessionFormat.format(Instant.ofEpochMilli(s.startAt).atZone(ZoneId.systemDefault())) +
                (if (s.durationMs >= 60_000) " · " + StatsCalc.duration(s.durationMs) else "") + speed,
            color = AppColors.Faint,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (s.note.isNotBlank()) Text("«${s.note}»", color = AppColors.Muted, fontSize = 13.sp, fontStyle = FontStyle.Italic, lineHeight = 18.sp)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SessionSheet(book: Book, form: SessionForm, onSaved: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val total = book.positions.coerceAtLeast(1)
    var from by remember { mutableIntStateOf(form.from.coerceIn(0, total)) }
    var to by remember { mutableIntStateOf(form.to.coerceIn(0, total)) }
    var minutes by remember { mutableIntStateOf((form.durationMs / 60_000).toInt()) }
    var start by remember { mutableLongStateOf(form.startAt) }
    var note by remember { mutableStateOf(form.session?.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ok = to > from
    AppSheet(onDismiss, title = if (form.session == null) "Сеанс чтения" else "Изменить сеанс") {
        val close = sheetClose(onDismiss)
        if (form.fromStopwatch) {
            Text("На какой странице остановился?", color = AppColors.Muted, fontSize = 14.sp, maxLines = 1)
        }
        SettingsGroup(null) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stepper("С", AppIcons.Bookmark, from, 0..total) { from = it; if (to < it) to = it }
                Stepper("До", AppIcons.Pages, to, 0..total) { to = it }
                Text(
                    if (ok) "+${to - from} ${pagesWord(to - from)} · ${to * 100 / total}% книги" else "В книге $total стр.",
                    color = if (ok) AppColors.Accent else AppColors.Faint,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 44.dp),
                )
            }
        }
        SettingsGroup(null) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stepper("Минут", AppIcons.Clock, minutes, 0..600) { minutes = it }
                val at = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault())
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingIcon(AppIcons.Calendar)
                    Text("Когда", color = AppColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    QuickChip(dayLabel(at.toLocalDate())) { pickDate = true }
                    QuickChip("%02d:%02d".format(at.hour, at.minute)) { pickTime = true }
                }
            }
        }
        BasicTextField(
            value = note,
            onValueChange = { note = it.take(500) },
            textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(AppColors.Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).fieldSurface(appCorner(16)).padding(14.dp),
            decorationBox = { inner ->
                Box {
                    if (note.isEmpty()) Text("Мысли о прочитанном (необязательно)", color = AppColors.Faint, fontSize = 15.sp)
                    inner()
                }
            },
        )
        AppButton("Сохранить", primary = true, modifier = Modifier.fillMaxWidth()) {
            if (!ok) return@AppButton
            val ms = minutes * 60_000L
            val existing = form.session
            if (existing == null) {
                app.paper.add(book, from, to, start, ms, note)
            } else {
                app.paper.update(book, existing.copy(fromPage = from, toPage = to, startAt = start, durationMs = ms, note = note.trim()))
            }
            onSaved(to >= total && (form.session == null || form.session.toPage < total))
            close()
        }
        if (form.session != null) {
            Text(
                "Удалить сеанс",
                color = AppColors.Bad,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().liveClick(appCorner(8), sound = FeedbackSound.Close) { confirmDelete = true }.padding(vertical = 8.dp),
            )
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start + ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(start)).totalSeconds * 1000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = Instant.ofEpochMilli(utc).atZone(ZoneId.of("UTC")).toLocalDate()
                        val time = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalTime()
                        start = LocalDateTime.of(day, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().coerceAtMost(System.currentTimeMillis())
                    }
                    pickDate = false
                }) { Text("Готово", color = AppColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена", color = AppColors.Muted) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val at = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault())
        val state = rememberTimePickerState(at.hour, at.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = AppColors.Panel,
            confirmButton = {
                TextButton(onClick = {
                    start = at.withHour(state.hour).withMinute(state.minute).toInstant().toEpochMilli()
                    pickTime = false
                }) { Text("Готово", color = AppColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Отмена", color = AppColors.Muted) } },
            text = { TimePicker(state) },
        )
    }
    if (confirmDelete) {
        ConfirmSheet(
            title = "Удалить сеанс?",
            text = "Страницы и время этого сеанса уберутся из прогресса и статистики.",
            confirm = "Удалить",
            onConfirm = {
                form.session?.let { app.paper.delete(book, it) }
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> DateTimeFormatter.ofPattern("d MMM", Locale("ru")).format(d)
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier.height(32.dp).liveClick(shape, sound = FeedbackSound.Tap, onClick = onClick).border(1.dp, AppColors.Line, shape).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun Stepper(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingIcon(icon)
        Text(label, color = AppColors.Text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
        StepButton("−") { onChange((value - step).coerceIn(range)) }
        BasicTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }.take(5)
                text.toIntOrNull()?.let { onChange(it.coerceIn(range)) }
            },
            singleLine = true,
            textStyle = TextStyle(color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(AppColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(64.dp).height(40.dp).fieldSurface(appCorner(12)).padding(horizontal = 6.dp, vertical = 9.dp),
        )
        StepButton("+") { onChange((value + step).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val action by androidx.compose.runtime.rememberUpdatedState(onClick)
    val feedback = LocalFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, Motion.tween(120), label = "step")
    Box(
        Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (pressed) AppColors.Accent.copy(alpha = 0.25f) else AppColors.Line)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    feedback?.play(FeedbackSound.Tap, view)
                    action()
                    val repeat = scope.launch {
                        delay(420)
                        var pause = 140L
                        while (true) {
                            action()
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            delay(pause)
                            pause = (pause * 0.88).toLong().coerceAtLeast(35L)
                        }
                    }
                    tryAwaitRelease()
                    repeat.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = AppColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}
