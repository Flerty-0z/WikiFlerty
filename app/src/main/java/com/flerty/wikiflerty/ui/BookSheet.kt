package com.flerty.wikiflerty.ui

import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.LibraryPrefs
import com.flerty.wikiflerty.data.LibrarySort
import com.flerty.wikiflerty.data.LibraryView
import com.flerty.wikiflerty.data.ReadStatus
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith

private val Danger get() = AppColors.Bad

val LocalSheetClose = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun sheetClose(fallback: () -> Unit): () -> Unit = LocalSheetClose.current ?: fallback

@Composable
fun KeepHostBars() {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        var host: android.content.Context? = context
        while (host is android.content.ContextWrapper && host !is android.app.Activity) host = host.baseContext
        val hostWindow = (host as? android.app.Activity)?.window
        var parent: android.view.ViewParent? = view.parent
        var window: android.view.Window? = null
        while (parent != null) {
            if (parent is androidx.compose.ui.window.DialogWindowProvider) {
                window = parent.window
                break
            }
            parent = parent.parent
        }
        if (window != null && hostWindow != null) {
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(hostWindow.decorView)
            if (insets != null && !insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars())) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        onDispose { }
    }
}

@Composable
fun sheetHeight(): androidx.compose.ui.unit.Dp = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.57f).dp

@Composable
fun AppSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val latest by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val close: () -> Unit = remember {
        {
            if (!Motion.enabled) {
                latest()
            } else {
                scope.launch { runCatching { state.hide() } }.invokeOnCompletion { latest() }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        sheetGesturesEnabled = false,
        dragHandle = null,
        properties = androidx.compose.material3.ModalBottomSheetProperties(
            isAppearanceLightStatusBars = AppStyle.light,
            isAppearanceLightNavigationBars = AppStyle.light,
        ),
        containerColor = AppColors.Panel,
        shape = RoundedCornerShape(topStart = (26 * AppStyle.current.corners).dp, topEnd = (26 * AppStyle.current.corners).dp),
    ) {
        KeepHostBars()
        val enter = remember { androidx.compose.animation.core.Animatable(if (Motion.enabled) 0f else 1f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, Motion.tween(340, 60)) }
        val viewport = remember { RevealViewport() }
        androidx.compose.runtime.CompositionLocalProvider(LocalSheetClose provides close, LocalRevealViewport provides viewport) {
            Column(
                Modifier.height(sheetHeight()).graphicsLayer {
                    alpha = 0.35f + 0.65f * enter.value
                    translationY = (1f - enter.value) * 22f * density
                },
            ) {
                if (title != null) Box(Modifier.padding(horizontal = 16.dp)) { SheetHeader(title, close) }
                Column(
                    Modifier.weight(1f).revealViewport(viewport).padding(horizontal = 16.dp).navigationBarsPadding().revealScroll().padding(top = if (title == null) 16.dp else 14.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ConfirmSheet(
    title: String,
    text: String,
    confirm: String,
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss, title = title) {
        val close = sheetClose(onDismiss)
        Text(
            text,
            color = AppColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().fieldSurface(appCorner(16)).padding(horizontal = 14.dp, vertical = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = appCorner(12)
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .liveClick(shape, sound = if (danger) FeedbackSound.Close else FeedbackSound.Toggle) {
                        onConfirm()
                        close()
                    }
                    .background(if (danger) Danger.copy(alpha = 0.16f) else AppColors.Accent)
                    .border(1.dp, if (danger) Danger.copy(alpha = 0.5f) else AppColors.Accent, shape),
                contentAlignment = Alignment.Center,
            ) {
                FitText(confirm, if (danger) Danger else AppColors.OnAccent, 15.sp, Modifier.padding(horizontal = 10.dp), weight = FontWeight.SemiBold)
            }
            AppButton("Отмена", primary = false, modifier = Modifier.weight(1f), onClick = close)
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color, sound: FeedbackSound, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).liveClick(CircleShape, sound = sound, onClick = onClick).background(AppColors.Field),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp)) }
}

val StarYellow = Color(0xFFFFC94D)

@Composable
private fun FavoriteButton(on: Boolean, onClick: () -> Unit) {
    val feedback = LocalFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    var burst by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    var last by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on == last) return@LaunchedEffect
        last = on
        if (!Motion.enabled) return@LaunchedEffect
        if (on) {
            burst++
            pop.snapTo(0.6f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 420f))
        } else {
            pop.snapTo(0.85f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 600f))
        }
    }
    val tint by androidx.compose.animation.animateColorAsState(if (on) StarYellow else AppColors.Muted, Motion.fade(220), label = "fav")
    Box(
        Modifier
            .size(40.dp)
            .sparkBurst(burst, listOf(StarYellow, AppColors.Accent, Color.White), count = 14, reach = 0.9f)
            .liveClick(CircleShape, sound = FeedbackSound.Toggle) {
                if (!on) feedback?.play(FeedbackSound.Star, view, 1.25f)
                onClick()
            }
            .background(AppColors.Field),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) AppIcons.StarFilled else AppIcons.Star,
            "Избранное",
            tint = tint,
            modifier = Modifier.size(20.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = (1f - pop.value) * 70f
            },
        )
    }
}

@Composable
private fun SmallChip(title: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val shape = appCorner(18)
    Row(
        Modifier
            .heightIn(min = 38.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, onClick = onClick)
            .then(if (selected) Modifier.accentHighlight(shape) else Modifier.fieldSurface(shape))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) accentContent else AppColors.Muted, modifier = Modifier.size(16.dp))
        Text(title, color = if (selected) accentContent else AppColors.Text, fontSize = 14.sp)
    }
}

@Composable
fun BookSheet(
    book: Book,
    coverFile: File?,
    onRead: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    var showStats by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(false) }
    var editPaper by remember { mutableStateOf(false) }
    val inPages = book.paper || app.libraryPrefs.prefs.value.progressInPages

    AppSheet(onDismiss) {
        val close = sheetClose(onDismiss)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val pickCover = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) app.appScope.launch { app.library.setCover(book.id, uri) }
            }
            Box(Modifier.size(width = 84.dp, height = 126.dp)) {
                SmallCover(coverFile, Modifier.matchParentSize())
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(28.dp)
                        .liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.85f) { pickCover.launch("image/*") }
                        .background(Color(0xD9100D0B))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Pencil, "Сменить обложку", tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.title, color = AppColors.Text, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, color = AppColors.Muted, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                book.rating?.takeIf { it > 0f }?.let { r ->
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StarRating(r, 14.dp, gap = 2.dp)
                        Text(ratingText(r), color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton(AppIcons.Close, "Закрыть", AppColors.Muted, FeedbackSound.Close, close)
                FavoriteButton(book.favorite) { scope.launch { app.library.setFavorite(book.id, !book.favorite) } }
            }
        }

        Row(Modifier.height(IntrinsicSize.Min).sheetReveal(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(AppIcons.Pages, if (book.progress > 0 || inPages) progressLabel(book, inPages) else "Не начата", Modifier.weight(1f))
            InfoTile(statusIcon(book.status), book.status.title, Modifier.weight(1f))
        }

        androidx.compose.animation.AnimatedContent(
            targetState = showStats,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (androidx.compose.animation.slideInHorizontally(Motion.tween(260)) { dir * it / 5 } + androidx.compose.animation.fadeIn(Motion.fade(220)))
                    .togetherWith(androidx.compose.animation.slideOutHorizontally(Motion.tween(220)) { -dir * it / 5 } + androidx.compose.animation.fadeOut(Motion.fade(160)))
            },
            label = "book-sheet",
        ) { stats ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stats) {
                    BookStatsContent(app, book, onBack = { showStats = false })
                } else {
                    BookMainContent(app, book, onRead = {
                        close()
                        onRead()
                    }, onStats = { showStats = true }, onRate = { rating = true }, onDelete = { confirmDelete = true }, onEdit = { editPaper = true })
                }
            }
        }
    }

    if (rating) RatingSheet(book.id, celebrate = false) { rating = false }
    if (editPaper) PaperBookSheet(book) { editPaper = false }
    if (confirmDelete) {
        ConfirmSheet(
            title = "Удалить книгу?",
            text = if (book.paper) "Книга и её сеансы удалятся. Статистика останется." else "Книга и заметки к ней удалятся. Словарь и статистика останутся.",
            confirm = "Удалить",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun InfoTile(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier, tint: Color = AppColors.Accent) {
    val shape = appCorner(16)
    Row(
        modifier.fillMaxHeight().fieldSurface(shape).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(appCorner(10))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.08f))))
                .border(1.dp, tint.copy(alpha = 0.18f), appCorner(10)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp)) }
        Text(
            text,
            color = AppColors.Text,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BookMainContent(
    app: App,
    book: Book,
    onRead: () -> Unit,
    onStats: () -> Unit,
    onRate: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (book.paper) {
        PaperMain(book, onEdit)
        BookStatusRow(app, book)
        RateRow(book, onRate)
        SettingsNavRow(AppIcons.Chart, "Статистика книги", "Время по дням, вклад в цели") { onStats() }
        DeleteRow("Удалить книгу", onDelete)
        return
    }
    val watching = app.readingTimer.watch.collectAsStateWithLifecycle().value?.let { it.bookId == book.id && it.running } == true
    val readShape = appCorner(18)
    Row(
        Modifier
            .fillMaxWidth()
            .sheetReveal()
            .heightIn(min = 56.dp)
            .liveClick(readShape, sound = FeedbackSound.Open, pressedScale = 0.97f) { onRead() }
            .accentHighlight(readShape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Play, null, tint = accentContent, modifier = Modifier.size(20.dp))
        Text(
            if (book.progress > 0.0) "Продолжить" else "Читать",
            color = accentContent,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
        if (watching) Icon(AppIcons.Clock, null, tint = accentContent, modifier = Modifier.padding(start = 8.dp).size(18.dp))
    }

    ReadingWatchCard(book)

    BookStatusRow(app, book)

    RateRow(book, onRate)
    BookAbout(app, book)

    SettingsNavRow(AppIcons.Chart, "Статистика книги", "Время, скорость, остаток") { onStats() }

    DeleteRow("Удалить с телефона", onDelete)
}

@Composable
private fun BookStatusRow(app: App, book: Book) {
    val scope = rememberCoroutineScope()
    SettingsGroup(null) {
        PickerRow(
            label = "Статус",
            icon = AppIcons.Bookmark,
            options = listOf(ReadStatus.Reading, ReadStatus.Planned, ReadStatus.Finished, ReadStatus.New).map { status ->
                PickerOption(status.id, status.title, icon = statusIcon(status))
            },
            selectedId = book.status.id,
        ) { id -> ReadStatus.of(id)?.let { status -> scope.launch { app.library.setStatus(book.id, status) } } }
    }
}

@Composable
private fun DeleteRow(label: String, onDelete: () -> Unit) {
    val shape = appCorner(16)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).sheetReveal()
            .liveClick(shape, sound = FeedbackSound.Close) { onDelete() }
            .border(1.dp, Danger.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.Trash, label, tint = Danger, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun BookAbout(app: App, book: Book) {
    val info by androidx.compose.runtime.produceState<com.flerty.wikiflerty.data.BookInfo?>(null, book.id) { value = app.library.info(book) }
    val data = info ?: return
    val facts = listOfNotNull(
        data.publisher.takeIf { it.isNotBlank() },
        data.year.takeIf { it.isNotBlank() },
        data.language.takeIf { it.isNotBlank() },
        book.positions.takeIf { it > 0 }?.let { "$it стр." },
    )
    if (data.description.isBlank() && facts.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    SettingsGroup("О книге") {
        Column(Modifier.padding(vertical = 10.dp).animateContentSize(Motion.tween(260)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (data.description.isNotBlank()) {
                Text(
                    data.description,
                    color = AppColors.Text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (data.description.length > 260) {
                    Text(
                        if (expanded) "Свернуть" else "Читать полностью",
                        color = AppColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.liveClick(appCorner(8), sound = FeedbackSound.Toggle) { expanded = !expanded }.padding(vertical = 2.dp),
                    )
                }
            }
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), color = AppColors.Faint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RateRow(book: Book, onClick: () -> Unit) {
    val shape = appCorner(16)
    Row(
        Modifier
            .fillMaxWidth()
            .sheetReveal()
            .heightIn(min = 56.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Оценка", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                book.review.ifBlank { if (book.rating != null) "Без отзыва" else "Оценить и написать отзыв" },
                color = AppColors.Faint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StarRating(book.rating ?: 0f, 18.dp, gap = 3.dp)
    }
}

@Composable
private fun BookStatsContent(app: App, book: Book, onBack: () -> Unit) {
    val days by app.stats.days.collectAsStateWithLifecycle()
    val notes by app.notes.notes.collectAsStateWithLifecycle()
    val words by app.dictionary.entries.collectAsStateWithLifecycle()
    val stats = remember(days, book.statsKey) { StatsCalc.book(days, book.statsKey) }

    PageBar("Статистика книги", onBack, icon = AppIcons.Chart)

    val speed = stats.pagesPerHour
    val remaining = if (speed != null && book.positions > 0 && book.status != ReadStatus.Finished) {
        val pagesLeft = (1.0 - book.progress).coerceAtLeast(0.0) * book.positions
        StatsCalc.duration((pagesLeft / speed * 3_600_000).toLong())
    } else {
        null
    }
    StatPanel(
        "Показатели",
        listOf(
            StatCell("Время чтения", StatsCalc.duration(stats.ms), icon = AppIcons.Clock),
            StatCell("Страниц", if (book.positions > 0) "${stats.pages}/${book.positions}" else stats.pages.toString(), icon = AppIcons.Pages),
            StatCell("Скорость", speed?.let { "${it.toInt()} стр/ч" } ?: "—", icon = AppIcons.Speed),
            StatCell(
                "Осталось",
                when {
                    book.status == ReadStatus.Finished -> "Прочитано"
                    remaining != null -> "≈ $remaining"
                    else -> "—"
                },
                icon = AppIcons.Calendar,
                content = if (book.status == ReadStatus.Finished) {
                    { StatusPill(ReadStatus.Finished, small = false, framed = true) }
                } else {
                    null
                },
            ),
            StatCell("Дней чтения", stats.days.toString(), icon = AppIcons.Fire),
            StatCell("Заметки и слова", "${notes.count { it.bookId == book.id }} / ${words.count { it.bookId == book.id }}", icon = AppIcons.Note),
        ),
        icon = AppIcons.Chart,
    )

    val perDay = remember(days, book.statsKey) {
        days.mapNotNull { (d, b) -> b[book.statsKey]?.let { e -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { it to e } } }.toMap()
    }
    MinutesCard(perDay, LocalDate.now())

    fun day(time: Long) = StatsCalc.date(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate())
    val dates = listOfNotNull(
        "Добавлена: ${day(book.addedAt)}",
        stats.firstDay?.let { "Начата: ${StatsCalc.date(it)}" },
        book.finishedAt?.takeIf { book.status == ReadStatus.Finished }?.let { "Прочитана: ${day(it)}" },
    )
    Text(dates.joinToString("\n"), color = AppColors.Faint, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
fun LibraryViewSheet(prefs: LibraryPrefs, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    AppSheet(onDismiss, title = "Вид и сортировка") {
        SettingsGroup(null) {
            LibrarySort.entries.forEachIndexed { index, sort ->
                if (index > 0) ListDivider()
                val icon = when (sort) {
                    LibrarySort.Recent -> AppIcons.Clock
                    LibrarySort.Added -> AppIcons.Plus
                    LibrarySort.Title -> AppIcons.TextSize
                    LibrarySort.Author -> AppIcons.Pencil
                    LibrarySort.Progress -> AppIcons.Chart
                }
                SheetOptionRow(PickerOption(sort.id, sort.title, icon = icon), sort == prefs.sort) {
                    app.libraryPrefs.update {
                        val names = sort == LibrarySort.Title || sort == LibrarySort.Author
                        it.copy(sort = sort, descending = if (it.sort == sort) it.descending else !names)
                    }
                }
            }
            val names = prefs.sort == LibrarySort.Title || prefs.sort == LibrarySort.Author
            val options = when {
                names -> listOf("А → Я", "Я → А")
                prefs.sort == LibrarySort.Progress -> listOf("Больше", "Меньше")
                else -> listOf("Сначала новые", "Сначала старые")
            }
            val first = if (names) !prefs.descending else prefs.descending
            SettingSegmented(null, options, if (first) 0 else 1) { i ->
                val wantFirst = i == 0
                app.libraryPrefs.update { it.copy(descending = if (names) !wantFirst else wantFirst) }
            }
        }
        SettingsGroup(null) {
            PickerRow(
                label = "Прогресс",
                icon = AppIcons.Percent,
                options = listOf(
                    PickerOption("percent", "Проценты", "30%"),
                    PickerOption("pages", "Страницы", "110/366"),
                ),
                selectedId = if (prefs.progressInPages) "pages" else "percent",
            ) { id -> app.libraryPrefs.update { it.copy(progressInPages = id == "pages") } }
            SettingSegmented(null, listOf("Полки", "Сетка", "Список"), LibraryView.entries.indexOf(prefs.view)) { i ->
                app.libraryPrefs.update { it.copy(view = LibraryView.entries[i]) }
            }
            if (prefs.view == LibraryView.Grid) {
                SettingSegmented("Размер обложек", listOf("Мелкие", "Средние", "Крупные"), prefs.coverSize) { i ->
                    app.libraryPrefs.update { it.copy(coverSize = i) }
                }
            }
        }
    }
}

sealed class ShelfDialog {
    data class Create(val bookId: String?) : ShelfDialog()
    data class Edit(val name: String) : ShelfDialog()
}

@Composable
fun ShelfDialogView(dialog: ShelfDialog, shelves: List<String>, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val original = (dialog as? ShelfDialog.Edit)?.name.orEmpty()
    var name by remember(dialog) { mutableStateOf(original) }
    val initial = remember(dialog) {
        when (dialog) {
            is ShelfDialog.Edit -> books.filter { original in it.shelves }.map { it.id }.toSet()
            is ShelfDialog.Create -> setOfNotNull(dialog.bookId)
        }
    }
    var chosen by remember(dialog) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clean = name.trim()
    val taken = clean != original && clean in shelves
    val ready = clean.isNotEmpty() && !taken

    fun save() {
        if (!ready) return
        app.appScope.launch {
            if (dialog is ShelfDialog.Edit && clean != original) app.library.renameShelf(original, clean)
            app.library.books.value.forEach { b ->
                val has = clean in b.shelves
                val want = b.id in chosen
                if (has != want) app.library.setShelves(b.id, if (want) b.shelves + clean else b.shelves - clean)
            }
        }
        app.libraryPrefs.update { p ->
            p.copy(
                shelves = (p.shelves.map { if (it == original && original.isNotEmpty()) clean else it } + clean).distinct(),
                filter = if (original.isNotEmpty() && p.filter == "shelf:$original") "shelf:$clean" else p.filter,
            )
        }
    }

    AppSheet(onDismiss, title = if (dialog is ShelfDialog.Create) "Новая полка" else "Полка") {
        val close = sheetClose(onDismiss)
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            singleLine = true,
            textStyle = TextStyle(color = AppColors.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(AppColors.Accent),
            modifier = Modifier.fillMaxWidth().height(50.dp).fieldSurface(appCorner(16)).padding(horizontal = 16.dp),
            decorationBox = { inner ->
                Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Shelf, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f).padding(start = 10.dp)) {
                        if (name.isEmpty()) Text("Название, например «Психология»", color = AppColors.Faint, fontSize = 16.sp, maxLines = 1)
                        inner()
                    }
                }
            },
        )
        if (taken) Text("Такая полка уже есть", color = Danger, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        SettingsGroup("Книги на полке · ${chosen.size}/${books.size}") {
            Column(Modifier.padding(vertical = 6.dp)) {
                books.sortedBy { it.title.lowercase() }.forEachIndexed { index, b ->
                    if (index > 0) ListDivider()
                    val on = b.id in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.98f) { chosen = if (on) chosen - b.id else chosen + b.id }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BookCover(b.id, 32.dp)
                        Text(b.title, color = if (on) AppColors.Accent else AppColors.Text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(appCorner(7))
                                .background(if (on) AppColors.Accent else Color.Transparent)
                                .border(1.5.dp, if (on) AppColors.Accent else AppColors.Line, appCorner(7)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(15.dp)) }
                    }
                }
                if (books.isEmpty()) Text("Сначала добавь книги", color = AppColors.Faint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        AppButton(if (dialog is ShelfDialog.Create) "Создать полку" else "Сохранить", primary = true, modifier = Modifier.fillMaxWidth()) {
            if (ready) {
                save()
                close()
            }
        }
        if (dialog is ShelfDialog.Edit) {
            Text(
                "Удалить полку",
                color = Danger,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally).liveClick(appCorner(8), sound = FeedbackSound.Close) { confirmDelete = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
    if (confirmDelete) {
        ConfirmSheet(
            title = "Удалить полку?",
            text = "Книги останутся в библиотеке, удалится только полка «$original».",
            confirm = "Удалить",
            onConfirm = {
                app.appScope.launch { app.library.renameShelf(original, null) }
                app.libraryPrefs.update { p ->
                    p.copy(shelves = p.shelves - original, filter = if (p.filter == "shelf:$original") "all" else p.filter)
                }
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
