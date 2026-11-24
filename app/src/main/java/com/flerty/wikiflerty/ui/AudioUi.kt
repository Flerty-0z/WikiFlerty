package com.flerty.wikiflerty.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.audio.Audiobook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AudioNav {
    var player by mutableStateOf(false)
    var importing by mutableStateOf(false)
    var busy by mutableStateOf(false)
}

fun audioTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun audioLeft(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    val h = m / 60
    return when {
        h > 0 -> "$h ч ${m % 60} мин"
        m > 0 -> "$m мин"
        else -> "меньше минуты"
    }
}

@Composable
private fun AudioCover(id: String, title: String, modifier: Modifier, corner: Dp = 14.dp) {
    val app = LocalContext.current.applicationContext as App
    val bitmap by produceState<android.graphics.Bitmap?>(null, id) {
        value = withContext(Dispatchers.IO) {
            val f = app.audiobooks.cover(id)
            if (!f.exists()) return@withContext null
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, o)
                var sample = 1
                while (o.outWidth / sample > 900) sample *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    val shape = appCorner(corner.value.toInt())
    Box(modifier.clip(shape).background(AppColors.Field), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.55f), AppColors.Accent.copy(alpha = 0.18f))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(8.dp)) {
                    Icon(AppIcons.Headphones, null, tint = AppColors.Text, modifier = Modifier.size(26.dp))
                    Text(title, color = AppColors.Text, fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AudioShelf(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val books by app.audiobooks.books.collectAsStateWithLifecycle()
    val state by app.audio.state.collectAsStateWithLifecycle()
    if (books.isEmpty()) return
    val sorted = remember(books) { books.sortedByDescending { it.lastPlayedAt ?: it.addedAt } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Headphones, null, tint = AppColors.Accent, modifier = Modifier.size(17.dp))
            Text("Аудиокниги", color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${books.size}", color = AppColors.Faint, fontSize = 12.sp)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sorted.size, key = { sorted[it].id }) { i ->
                val b = sorted[i]
                AudioCard(b, playing = state.bookId == b.id && state.playing) {
                    app.audio.open(b.id, play = false)
                    AudioNav.player = true
                }
            }
            item(key = "add") { AudioAddCard { AudioNav.importing = true } }
        }
    }
}

@Composable
private fun AudioCard(book: Audiobook, playing: Boolean, onClick: () -> Unit) {
    val shape = appCorner(16)
    val pulse = rememberInfiniteTransition(label = "audio-card")
    val glow by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "audio-glow")
    Column(
        Modifier
            .width(124.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.95f, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            AudioCover(book.id, book.title, Modifier.fillMaxWidth().aspectRatio(1f), 16.dp)
            if (playing) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).background(AppColors.Accent.copy(alpha = glow), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Headphones, null, tint = AppColors.OnAccent, modifier = Modifier.size(14.dp)) }
            }
        }
        Text(book.title, color = AppColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AppColors.Line)) {
            Box(Modifier.fillMaxWidth(book.progress.coerceAtLeast(0.02f)).height(4.dp).background(AppColors.Accent, CircleShape))
        }
        Text("осталось " + audioLeft(book.totalMs - book.elapsedMs), color = AppColors.Faint, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun AudioAddCard(onClick: () -> Unit) {
    val shape = appCorner(16)
    Box(
        Modifier
            .width(124.dp)
            .aspectRatio(1f)
            .liveClick(shape, sound = FeedbackSound.Add, pressedScale = 0.94f, onClick = onClick)
            .border(1.5.dp, AppColors.Accent.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(AppIcons.Plus, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
            Text("Аудиокнига", color = AppColors.Accent, fontSize = 12.sp)
        }
    }
}

@Composable
fun AudioImportHost() {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun done(b: Audiobook?) {
        AudioNav.busy = false
        if (b == null) {
            android.widget.Toast.makeText(context, "Не нашлось аудиофайлов", android.widget.Toast.LENGTH_LONG).show()
        } else {
            app.audio.open(b.id, play = false)
            AudioNav.player = true
        }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            AudioNav.busy = true
            scope.launch { done(runCatching { app.audiobooks.importFolder(uri) }.getOrNull()) }
        }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            AudioNav.busy = true
            scope.launch { done(runCatching { app.audiobooks.importFiles(uris) }.getOrNull()) }
        }
    }
    if (AudioNav.importing) {
        AppSheet(onDismiss = { AudioNav.importing = false }, title = "Аудиокнига") {
            val close = sheetClose { AudioNav.importing = false }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AudioChoice(AppIcons.Folder, "Папка", "Все аудиофайлы из папки — по порядку") {
                    close()
                    runCatching { folder.launch(null) }
                }
                AudioChoice(AppIcons.Headphones, "Файлы", "MP3, M4B, M4A, OGG, FLAC — один или несколько") {
                    close()
                    runCatching { files.launch(arrayOf("audio/*")) }
                }
            }
        }
    }
    if (AudioNav.busy) {
        AppSheet(onDismiss = {}, title = "Добавляю аудиокнигу") {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                KitSpinner(Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("Читаю главы и обложку…", color = AppColors.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AudioChoice(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    val shape = appCorner(16)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = onClick).fieldSurface(shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = AppColors.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun AudioMiniPlayer(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val state by app.audio.state.collectAsStateWithLifecycle()
    val books by app.audiobooks.books.collectAsStateWithLifecycle()
    val book = remember(books, state.bookId) { books.firstOrNull { it.id == state.bookId } }
    AnimatedVisibility(
        visible = book != null && !AudioNav.player,
        enter = slideInVertically(Motion.spring()) { it } + fadeIn(Motion.fade(200)),
        exit = slideOutVertically(Motion.tween(200)) { it } + fadeOut(Motion.fade(160)),
        modifier = modifier,
    ) {
        val b = book ?: return@AnimatedVisibility
        val shape = appCorner(18)
        val chapter = b.chapters.getOrNull(b.chapterAt(state.track, state.positionMs))
        val progress = if (b.totalMs > 0) ((b.offsetOf(state.track) + state.positionMs).toFloat() / b.totalMs).coerceIn(0f, 1f) else 0f
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
                .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { AudioNav.player = true }
                .panelSurface(shape, elevation = 8.dp),
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AudioCover(b.id, b.title, Modifier.size(44.dp), 10.dp)
                Column(Modifier.weight(1f)) {
                    Text(b.title, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chapter?.title.orEmpty(), color = AppColors.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlayButton(state.playing, 42.dp) { app.audio.toggle() }
                Box(
                    Modifier.size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.88f) { app.audio.close() },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Close, "Закрыть плеер", tint = AppColors.Faint, modifier = Modifier.size(17.dp)) }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(AppColors.Line)) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(AppColors.Accent))
            }
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .liveClick(CircleShape, sound = if (playing) FeedbackSound.Pause else FeedbackSound.Play, pressedScale = 0.88f, onClick = onClick)
            .background(AppColors.Accent, CircleShape)
            .semantics { contentDescription = if (playing) "Пауза" else "Слушать" },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            playing,
            transitionSpec = { (fadeIn(Motion.tween(160)) + scaleIn(Motion.tween(200), initialScale = 0.6f)) togetherWith (fadeOut(Motion.tween(120)) + scaleOut(Motion.tween(160), targetScale = 0.6f)) },
            label = "audio-play",
        ) { on ->
            Icon(if (on) AppIcons.Pause else AppIcons.Play, null, tint = AppColors.OnAccent, modifier = Modifier.size(size * 0.46f))
        }
    }
}

private enum class AudioPanel { Main, Chapters, Speed, Sleep, Link, Delete }

@Composable
fun AudioPlayerHost() {
    if (!AudioNav.player) return
    val app = LocalContext.current.applicationContext as App
    val state by app.audio.state.collectAsStateWithLifecycle()
    val books by app.audiobooks.books.collectAsStateWithLifecycle()
    val book = remember(books, state.bookId) { books.firstOrNull { it.id == state.bookId } }
    if (book == null) {
        LaunchedEffect(Unit) { AudioNav.player = false }
        return
    }
    var panel by remember { mutableStateOf(AudioPanel.Main) }
    AppSheet(onDismiss = { AudioNav.player = false }, title = null) {
        AudioMain(book) { panel = it }
        if (panel != AudioPanel.Main) {
            val title = when (panel) {
                AudioPanel.Chapters -> "Главы"
                AudioPanel.Speed -> "Скорость"
                AudioPanel.Sleep -> "Таймер сна"
                AudioPanel.Link -> "Текст этой книги"
                AudioPanel.Delete -> "Убрать аудиокнигу?"
                AudioPanel.Main -> ""
            }
            AppSheet(onDismiss = { panel = AudioPanel.Main }, title = title) {
                val back = sheetClose { panel = AudioPanel.Main }
                when (panel) {
                    AudioPanel.Chapters -> AudioChapters(book, back)
                    AudioPanel.Speed -> AudioSpeed(state.speed, back)
                    AudioPanel.Sleep -> AudioSleep(back)
                    AudioPanel.Link -> AudioLink(book, back)
                    AudioPanel.Delete -> AudioDelete(book, back)
                    AudioPanel.Main -> Unit
                }
            }
        }
    }
}

@Composable
private fun AudioMain(book: Audiobook, open: (AudioPanel) -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val state by app.audio.state.collectAsStateWithLifecycle()
    val ch = book.chapterAt(state.track, state.positionMs)
    val chapter = book.chapters.getOrNull(ch)
    val global = book.offsetOf(state.track) + state.positionMs
    val start = book.chapterStart(ch)
    val end = book.chapterEnd(ch)
    var drag by remember { mutableStateOf<Float?>(null) }
    val breathe by animateFloatAsState(if (state.playing) 1f else 0.94f, Motion.spring(), label = "audio-cover")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AudioCover(book.id, book.title, Modifier.padding(top = 4.dp).size(176.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }, 18.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(book.title, color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val length = (end - start).coerceAtLeast(1)
            Text(
                (chapter?.title.orEmpty()) + " · " + audioTime(global - start) + " из " + audioTime(length),
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.liveClick(appCorner(10), sound = FeedbackSound.Open) { open(AudioPanel.Chapters) }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        ChapterSpines(book, ch) { app.audio.chapter(it) }
        val length = (end - start).coerceAtLeast(1)
        val shown = drag ?: ((global - start).toFloat() / length).coerceIn(0f, 1f)
        KitSlider(
            value = shown,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { app.audio.seekGlobal(start + (it * length).toLong()) }
                drag = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(audioTime((shown * length).toLong()), color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("осталось " + audioLeft(((book.totalMs - global) / state.speed).toLong()), color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("-" + audioTime(((1f - shown) * length).toLong()), color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundTool(AppIcons.SkipPrev, "Предыдущая глава", FeedbackSound.SkipBack) { app.audio.prevChapter() }
            RoundTool(AppIcons.Rewind, "Назад на 15 секунд", FeedbackSound.SkipBack, label = "15") { app.audio.skip(-15_000) }
            PlayButton(state.playing, 72.dp) { app.audio.toggle() }
            RoundTool(AppIcons.FastForward, "Вперёд на 30 секунд", FeedbackSound.SkipNext, label = "30") { app.audio.skip(30_000) }
            RoundTool(AppIcons.SkipNext, "Следующая глава", FeedbackSound.SkipNext) { app.audio.nextChapter() }
        }
        state.error?.let { HintNote(it, AppIcons.Info) }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            item { FrameTool(AppIcons.Speed, "Скорость", "%.2f×".format(state.speed).replace(".00", "").replace(",00", "")) { open(AudioPanel.Speed) } }
            item {
                val left = if (state.sleepAt > 0) audioLeft(state.sleepAt - System.currentTimeMillis()) else if (state.sleepChapter) "глава" else null
                FrameTool(AppIcons.Moon, "Таймер сна", left, active = left != null) { open(AudioPanel.Sleep) }
            }
            item {
                val near = app.audio.markNear(book, state.track, state.positionMs) != null
                MarkTool(near) {
                    val added = app.audio.toggleMark()
                    android.widget.Toast.makeText(app, if (added) "Закладка на " + audioTime(app.audio.positionNow()) else "Закладка убрана", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            item { FrameTool(AppIcons.Toc, "Главы", null) { open(AudioPanel.Chapters) } }
            item { FrameTool(AppIcons.Link, "Книга", null, active = book.bookId != null) { open(AudioPanel.Link) } }
            item { FrameTool(AppIcons.Trash, "Удалить", null, sound = FeedbackSound.Trash) { open(AudioPanel.Delete) } }
        }
    }
}

private fun spineHeights(book: Audiobook): FloatArray {
    val offsets = LongArray(book.tracks.size + 1)
    book.tracks.forEachIndexed { i, t -> offsets[i + 1] = offsets[i] + t.durationMs }
    val starts = book.chapters.map { offsets[it.track.coerceIn(0, book.tracks.size)] + it.startMs }
    val lens = starts.mapIndexed { i, st -> ((if (i + 1 < starts.size) starts[i + 1] else offsets.last()) - st).coerceAtLeast(1) }
    val max = lens.maxOrNull()?.toFloat() ?: 1f
    return FloatArray(lens.size) { 0.38f + 0.62f * (lens[it] / max) }
}

@Composable
private fun ChapterSpines(book: Audiobook, current: Int, onPick: (Int) -> Unit) {
    val palette = spinePalette()
    val heights = remember(book.id, book.chapters, book.tracks) { spineHeights(book) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current) { runCatching { list.animateScrollToItem((current - 3).coerceAtLeast(0)) } }
    val many = book.chapters.size > 12
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.lazy.LazyRow(
            state = list,
            modifier = Modifier.fillMaxWidth().height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp, if (many) Alignment.Start else Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            items(book.chapters.size) { i ->
                val on = i == current
                val lift by animateFloatAsState(if (on) 1f else 0f, Motion.spring(), label = "spine-lift")
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
                Box(
                    Modifier
                        .width(if (many) 22.dp else 30.dp)
                        .height((100 * heights[i]).dp)
                        .graphicsLayer { translationY = -lift * 9.dp.toPx() }
                        .liveClick(shape, sound = FeedbackSound.Pick, pressedScale = 0.92f) { onPick(i) }
                        .background(palette[i % palette.size], shape)
                        .then(if (on) Modifier.border(2.dp, AppColors.Accent, shape) else Modifier)
                        .semantics { contentDescription = book.chapters[i].title },
                )
            }
        }
        Plank(null, false, null)
    }
}

@Composable
private fun MarkTool(on: Boolean, onClick: () -> Unit) {
    val shape = appCorner(14)
    val fill by androidx.compose.animation.animateColorAsState(if (on) AppColors.Accent else AppColors.Accent.copy(alpha = 0f), Motion.fade(240), label = "mark-fill")
    val tint by androidx.compose.animation.animateColorAsState(if (on) AppColors.OnAccent else AppColors.Accent, Motion.fade(240), label = "mark-tint")
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(on) {
        pop.snapTo(0.82f)
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f))
    }
    Box(
        Modifier
            .size(width = 52.dp, height = 48.dp)
            .liveClick(shape, sound = FeedbackSound.Bookmark, pressedScale = 0.9f, onClick = onClick)
            .fieldSurface(shape, selected = on)
            .background(fill, shape)
            .semantics { contentDescription = if (on) "Убрать закладку" else "Поставить закладку" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Bookmark, null, tint = tint, modifier = Modifier.size(19.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value })
    }
}

@Composable
private fun RoundTool(icon: ImageVector, description: String, sound: FeedbackSound, label: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).liveClick(CircleShape, sound = sound, pressedScale = 0.86f, onClick = onClick).fieldSurface(CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = AppColors.Text, modifier = Modifier.size(if (label != null) 18.dp else 21.dp).graphicsLayer { translationY = if (label != null) -5.dp.toPx() else 0f })
        if (label != null) Text(label, color = AppColors.Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
}

@Composable
private fun FrameTool(icon: ImageVector, description: String, value: String?, active: Boolean = false, sound: FeedbackSound = FeedbackSound.Open, onClick: () -> Unit) {
    val shape = appCorner(14)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .liveClick(shape, sound = sound, pressedScale = 0.92f, onLongClick = null, onClick = onClick)
            .fieldSurface(shape, selected = active)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(19.dp))
        if (value != null) Text(value, color = AppColors.Text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun PanelHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).liveClick(CircleShape, sound = FeedbackSound.Back, pressedScale = 0.88f, onClick = onBack).fieldSurface(CircleShape), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Back, "Назад", tint = AppColors.Text, modifier = Modifier.size(18.dp))
        }
        Text(title, color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AudioChapters(book: Audiobook, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val state by app.audio.state.collectAsStateWithLifecycle()
    val current = book.chapterAt(state.track, state.positionMs)
    val palette = spinePalette()
    val heights = remember(book.id, book.chapters, book.tracks) { spineHeights(book) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (book.marks.isNotEmpty()) {
            Text("Закладки", color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            book.marks.forEach { m ->
                val at = book.offsetOf(m.track) + m.ms
                val title = book.chapters.getOrNull(book.chapterAt(m.track, m.ms))?.title.orEmpty()
                ChapterRow(AppIcons.Bookmark, title, audioTime(at), false) {
                    app.audio.seekTo(m.track, m.ms)
                    onBack()
                }
            }
            Text("Главы", color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        book.chapters.forEachIndexed { i, c ->
            val on = i == current
            val shape = appCorner(12)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).liveClick(shape, sound = FeedbackSound.Go, pressedScale = 0.98f) {
                    app.audio.chapter(i)
                    onBack()
                }
                    .then(if (on) Modifier.background(AppColors.Accent.copy(alpha = 0.14f), shape) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(10.dp).height((16 + 24 * heights[i]).dp).background(palette[i % palette.size], androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).then(if (on) Modifier.border(1.5.dp, AppColors.Accent, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)) else Modifier))
                Text(c.title, color = if (on) AppColors.Accent else AppColors.Text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(audioTime(book.chapterEnd(i) - book.chapterStart(i)), color = AppColors.Faint, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChapterRow(icon: ImageVector?, title: String, time: String, active: Boolean, onClick: () -> Unit) {
    val shape = appCorner(12)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).liveClick(shape, sound = FeedbackSound.Go, pressedScale = 0.98f, onClick = onClick)
            .then(if (active) Modifier.background(AppColors.Accent.copy(alpha = 0.14f), shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
        if (active) Box(Modifier.width(3.dp).height(18.dp).background(AppColors.Accent, CircleShape))
        Text(title, color = if (active) AppColors.Accent else AppColors.Text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(time, color = AppColors.Faint, fontSize = 12.sp)
    }
}

private val SpeedOptions = listOf(0.75f, 1f, 1.15f, 1.25f, 1.5f, 1.75f, 2f, 2.5f)

private fun speedLabel(v: Float) = "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.') + "×"

@Composable
private fun AudioSpeed(speed: Float, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val feedback = LocalFeedback.current
    var pick by remember { mutableStateOf(SpeedOptions.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }.coerceAtLeast(1)) }
    val sweep = 270f
    val startAngle = 135f
    fun angleOf(i: Int) = startAngle + sweep * i / (SpeedOptions.size - 1)
    val needle by animateFloatAsState(angleOf(pick), Motion.spring(), label = "speed-needle")
    val accent = AppColors.Accent
    val track = AppColors.Line
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .size(280.dp)
                .pointerInput(Unit) {
                    fun select(p: androidx.compose.ui.geometry.Offset) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        var a = Math.toDegrees(kotlin.math.atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()
                        if (a < 0) a += 360f
                        var rel = a - startAngle
                        if (rel < 0) rel += 360f
                        if (rel > sweep) rel = if (rel - sweep < (360f - sweep) / 2) sweep else 0f
                        val i = kotlin.math.round(rel / sweep * (SpeedOptions.size - 1)).toInt().coerceIn(0, SpeedOptions.lastIndex)
                        if (i != pick) {
                            pick = i
                            feedback?.play(FeedbackSound.Tick)
                            app.audio.setSpeed(SpeedOptions[i])
                        }
                    }
                    androidx.compose.foundation.gestures.detectDragGestures(onDragStart = { select(it) }) { change, _ -> select(change.position) }
                }
                .pointerInput(Unit) {
                    androidx.compose.foundation.gestures.detectTapGestures { p ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        var a = Math.toDegrees(kotlin.math.atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()
                        if (a < 0) a += 360f
                        var rel = a - startAngle
                        if (rel < 0) rel += 360f
                        if (rel <= sweep + 10f) {
                            val i = kotlin.math.round(rel.coerceAtMost(sweep) / sweep * (SpeedOptions.size - 1)).toInt().coerceIn(0, SpeedOptions.lastIndex)
                            pick = i
                            feedback?.play(FeedbackSound.Pick)
                            app.audio.setSpeed(SpeedOptions[i])
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val radius = maxWidth.value / 2f - 26f
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val r = size.minDimension / 2f - 26.dp.toPx()
                val tl = androidx.compose.ui.geometry.Offset(size.width / 2f - r, size.height / 2f - r)
                val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                drawArc(track, startAngle, sweep, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawArc(accent, startAngle, needle - startAngle, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                val rad = Math.toRadians(needle.toDouble())
                val c = androidx.compose.ui.geometry.Offset(size.width / 2f + (r * kotlin.math.cos(rad)).toFloat(), size.height / 2f + (r * kotlin.math.sin(rad)).toFloat())
                drawCircle(accent, 11.dp.toPx(), c)
                drawCircle(accent.copy(alpha = 0.25f), 18.dp.toPx(), c)
            }
            SpeedOptions.forEachIndexed { i, v ->
                val rad = Math.toRadians(angleOf(i).toDouble())
                val lr = radius - 34f
                Text(
                    speedLabel(v),
                    color = if (i == pick) AppColors.Accent else AppColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = if (i == pick) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.offset((lr * kotlin.math.cos(rad)).toFloat().dp, (lr * kotlin.math.sin(rad)).toFloat().dp),
                )
            }
            AnimatedContent(pick, transitionSpec = { (fadeIn(Motion.tween(160)) + scaleIn(Motion.tween(200), initialScale = 0.85f)) togetherWith fadeOut(Motion.tween(100)) }, label = "speed-value") { i ->
                Text(speedLabel(SpeedOptions[i]), color = AppColors.Accent, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
        AppButton("Готово", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Confirm) { onBack() }
    }
}

@Composable
private fun AudioSleep(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val state by app.audio.state.collectAsStateWithLifecycle()
    val options = listOf(15 to "15 минут", 30 to "30 минут", 45 to "45 минут", 60 to "1 час", -1 to "До конца главы", 0 to "Выключить")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (m, label) ->
            val on = when {
                m == -1 -> state.sleepChapter
                m == 0 -> state.sleepAt == 0L && !state.sleepChapter
                else -> false
            }
            ChapterRow(AppIcons.Moon, label, "", on) {
                app.audio.sleep(m)
                onBack()
            }
        }
    }
}

@Composable
private fun AudioLink(book: Audiobook, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val library by app.library.books.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val words = remember(book.title) { book.title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 } }
    val list = remember(library, query, words) {
        val q = query.trim()
        if (q.isNotEmpty()) {
            library.filter { it.title.contains(q, true) || it.author.contains(q, true) }
        } else {
            library.sortedByDescending { b -> words.count { b.title.lowercase().contains(it) } }
        }.take(40)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HintNote("Свяжи аудиокнигу с её текстом из библиотеки — по нему появится подсветка во время прослушивания.", AppIcons.Link)
        if (book.bookId != null) AudioSyncCard(book)
        InputField(query, { query = it }, "Поиск в библиотеке", icon = AppIcons.Search)
        if (book.bookId != null) {
            ChapterRow(AppIcons.Close, "Отвязать", "", false) {
                app.audiobooks.update(book.id) { it.copy(bookId = null) }
                onBack()
            }
        }
        list.forEach { b ->
            ChapterRow(AppIcons.Book, b.title, b.author.take(18), b.id == book.bookId) {
                app.audiobooks.update(book.id) { it.copy(bookId = b.id) }
                onBack()
            }
        }
    }
}

@Composable
private fun AudioDelete(book: Audiobook, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HintNote("Аудиокнига пропадёт из библиотеки. Сами файлы на телефоне останутся.", AppIcons.Trash)
        AppButton("Убрать", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Trash) {
            if (app.audio.state.value.bookId == book.id) app.audio.close()
            app.audiobooks.remove(book.id)
            AudioNav.player = false
        }
    }
}

@Composable
fun AudioPlank(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val books by app.audiobooks.books.collectAsStateWithLifecycle()
    val state by app.audio.state.collectAsStateWithLifecycle()
    if (books.isEmpty()) return
    val sorted = remember(books) { books.sortedByDescending { it.lastPlayedAt ?: it.addedAt } }
    Column(modifier.fillMaxWidth()) {
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items(sorted.size, key = { sorted[it].id }) { i ->
                val b = sorted[i]
                val playing = state.bookId == b.id && state.playing
                val shape = appCorner(10)
                val pulse = rememberInfiniteTransition(label = "audio-plank")
                val glow by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "audio-plank-glow")
                Box(
                    Modifier
                        .size(78.dp)
                        .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.94f) {
                            app.audio.open(b.id, play = false)
                            AudioNav.player = true
                        }
                        .semantics(mergeDescendants = true) { contentDescription = "Аудиокнига «${b.title}»" },
                ) {
                    AudioCover(b.id, b.title, Modifier.fillMaxSize(), 10.dp)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.35f))) {
                        Box(Modifier.fillMaxWidth(b.progress.coerceAtLeast(0.03f)).height(3.dp).background(AppColors.Accent))
                    }
                    if (playing) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).background(AppColors.Accent.copy(alpha = glow), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Headphones, null, tint = AppColors.OnAccent, modifier = Modifier.size(12.dp)) }
                    }
                }
            }
            item(key = "add") {
                val shape = appCorner(10)
                Box(
                    Modifier.size(78.dp).liveClick(shape, sound = FeedbackSound.Add, pressedScale = 0.92f) { AudioNav.importing = true }
                        .border(1.5.dp, AppColors.Accent.copy(alpha = 0.4f), shape)
                        .semantics { contentDescription = "Добавить аудиокнигу" },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Plus, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
            }
        }
        Spacer(Modifier.height(2.dp))
        Plank("Аудиокниги · ${books.size}", false, null)
    }
}

@Composable
fun AudioContinueStrip(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val state by app.audio.state.collectAsStateWithLifecycle()
    val books by app.audiobooks.books.collectAsStateWithLifecycle()
    val b = remember(books, state.bookId) { books.firstOrNull { it.id == state.bookId } } ?: return
    val shape = appCorner(16)
    val chapter = b.chapters.getOrNull(b.chapterAt(state.track, state.positionMs))
    val global = b.offsetOf(state.track) + state.positionMs
    val progress = if (b.totalMs > 0) (global.toFloat() / b.totalMs).coerceIn(0f, 1f) else 0f
    Row(
        modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { AudioNav.player = true }
            .fieldSurface(shape)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.3f), shape)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        AudioCover(b.id, b.title, Modifier.size(46.dp), 8.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(b.title, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(AppIcons.Headphones, null, tint = AppColors.Accent, modifier = Modifier.size(12.dp))
                Text((chapter?.title.orEmpty()) + " · осталось " + audioLeft(((b.totalMs - global) / state.speed).toLong()), color = AppColors.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(AppColors.Accent.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(AppColors.Accent, CircleShape))
            }
        }
        PlayButton(state.playing, 40.dp) { app.audio.toggle() }
        Box(
            Modifier.size(32.dp).liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.86f) { app.audio.close() },
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Close, "Закрыть плеер", tint = AppColors.Faint, modifier = Modifier.size(15.dp)) }
    }
}

@Composable
private fun AudioSyncCard(book: Audiobook) {
    val app = LocalContext.current.applicationContext as App
    val states by app.audioSync.state.collectAsStateWithLifecycle()
    val st = states[book.id]
    val has = remember(st) { app.audioSync.has(book.id) }
    val shape = appCorner(16)
    Column(
        Modifier.fillMaxWidth().fieldSurface(shape).animateContentSize(Motion.tween(260)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingIcon(AppIcons.Headphones)
            Column(Modifier.weight(1f)) {
                Text("Сопоставление с текстом", color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val line = when (st?.stage) {
                    "model" -> "Скачиваю распознавание речи · ${(st.progress * 100).toInt()}%"
                    "listen" -> "Слушаю аудио · ${(st.progress * 100).toInt()}%"
                    "match" -> "Ищу совпадения в тексте…"
                    "error" -> st.message
                    else -> if (has) "Готово · совпало ${(app.audioSync.coverage(book.id) * 100).toInt()}% слов" else "Ещё не сделано"
                }
                Text(line, color = if (st?.stage == "error") AppColors.Accent else AppColors.Muted, fontSize = 12.sp)
            }
        }
        val running = st != null && st.stage in setOf("model", "listen", "match")
        if (running) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(AppColors.Accent.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(if (st?.stage == "match") 1f else st?.progress?.coerceAtLeast(0.02f) ?: 0.02f).height(5.dp).background(AppColors.Accent, CircleShape))
            }
            AppButton("Остановить", primary = false, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Close) { app.audioSync.cancel(book.id) }
        } else {
            if (!has) {
                HintNote(
                    if (com.flerty.wikiflerty.audio.AsrModel.ready(app)) "Телефон прослушает аудиокнигу без интернета и найдёт, где какое предложение. Это делается один раз."
                    else "Телефон прослушает аудиокнигу и найдёт, где какое предложение. Один раз скачается распознавание речи — ${com.flerty.wikiflerty.audio.AsrModel.SIZE_MB} МБ, дальше всё без интернета.",
                    AppIcons.Info,
                )
            }
            AppButton(if (has) "Сопоставить заново" else "Сопоставить с текстом", primary = !has, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Go) {
                if (has) app.audioSync.clear(book.id)
                app.audioSync.start(book.id)
            }
        }
    }
}
