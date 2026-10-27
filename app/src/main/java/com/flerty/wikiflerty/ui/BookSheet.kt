package com.flerty.wikiflerty.ui

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

private val Danger = Color(0xFFF08A8A)

/** A pull-up sheet in the app's style. */
@Composable
fun AppSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // No dragging: swipes always scroll the content. Close with ✕, back or a tap outside.
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = AppColors.Panel,
        shape = RoundedCornerShape(topStart = (26 * AppStyle.current.corners).dp, topEnd = (26 * AppStyle.current.corners).dp),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(top = if (title == null) 16.dp else 0.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) SheetHeader(title, onDismiss)
            content()
        }
    }
}

/** Round icon button used in sheet headers. */
@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color, sound: FeedbackSound, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).liveClick(CircleShape, sound = sound, onClick = onClick).background(AppColors.Field),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp)) }
}

val StarYellow = Color(0xFFFFC94D)

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

// ---------------------------------------------------------------- one book

/** Long press on a book: status, favorite, shelves, statistics, delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookSheet(
    book: Book,
    shelves: List<String>,
    coverFile: File?,
    onNewShelf: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    var showStats by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppSheet(onDismiss) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SmallCover(coverFile, Modifier.size(width = 54.dp, height = 81.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.title, color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, color = AppColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${progressLabel(book, app.libraryPrefs.prefs.value.progressInPages)} · ${book.status.title}",
                    color = AppColors.Faint,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton(AppIcons.Close, "Закрыть", AppColors.Muted, FeedbackSound.Close, onDismiss)
                RoundButton(
                    if (book.favorite) AppIcons.StarFilled else AppIcons.Star,
                    "Избранное",
                    if (book.favorite) StarYellow else AppColors.Muted,
                    FeedbackSound.Toggle,
                ) { scope.launch { app.library.setFavorite(book.id, !book.favorite) } }
            }
        }

        if (showStats) {
            BookStatsContent(app, book, onBack = { showStats = false })
        } else {
            BookMainContent(app, book, shelves, onNewShelf, onStats = { showStats = true }, onDelete = { confirmDelete = true })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = AppColors.Panel,
            title = { Text("Удалить книгу?", color = AppColors.Text) },
            text = {
                Text(
                    "Книга и заметки к ней удалятся. Словарь и статистика останутся.",
                    color = AppColors.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Удалить", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = AppColors.Accent) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BookMainContent(
    app: App,
    book: Book,
    shelves: List<String>,
    onNewShelf: () -> Unit,
    onStats: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
        SettingsGroup(null) {
            PickerRow(
                label = "Статус",
                icon = AppIcons.Bookmark,
                options = listOf(ReadStatus.Reading, ReadStatus.Planned, ReadStatus.Finished, ReadStatus.New).map { status ->
                    PickerOption(
                        status.id,
                        status.title,
                        icon = when (status) {
                            ReadStatus.Reading -> AppIcons.Book
                            ReadStatus.Planned -> AppIcons.Clock
                            ReadStatus.Finished -> AppIcons.Check
                            ReadStatus.New -> AppIcons.Plus
                        },
                    )
                },
                selectedId = book.status.id,
            ) { id -> ReadStatus.of(id)?.let { status -> scope.launch { app.library.setStatus(book.id, status) } } }
        }

        SheetLabel("Полки")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shelves.forEach { shelf ->
                val on = shelf in book.shelves
                SmallChip(shelf, on, icon = if (on) AppIcons.Check else null) {
                    scope.launch { app.library.setShelves(book.id, if (on) book.shelves - shelf else book.shelves + shelf) }
                }
            }
            SmallChip("Новая полка", false, icon = AppIcons.Plus, onClick = onNewShelf)
        }

        Spacer(Modifier.height(2.dp))
        SettingsNavRow(AppIcons.Chart, "Статистика книги", "Время, скорость, остаток") { onStats() }

        val shape = appCorner(16)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .liveClick(shape, sound = FeedbackSound.Close) { onDelete() }
                .border(1.dp, Danger.copy(alpha = 0.45f), shape),
            contentAlignment = Alignment.Center,
        ) { Text("Удалить с телефона", color = Danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
}

@Composable
private fun BookStatsContent(app: App, book: Book, onBack: () -> Unit) {
    val days by app.stats.days.collectAsStateWithLifecycle()
    val notes by app.notes.notes.collectAsStateWithLifecycle()
    val words by app.dictionary.entries.collectAsStateWithLifecycle()
    val stats = remember(days, book.statsKey) { StatsCalc.book(days, book.statsKey) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Back, "Назад", tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
        Text("Статистика книги", color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }

    val speed = stats.pagesPerHour
    val remaining = if (speed != null && book.positions > 0 && book.status != ReadStatus.Finished) {
        val pagesLeft = (1.0 - book.progress).coerceAtLeast(0.0) * book.positions
        StatsCalc.duration((pagesLeft / speed * 3_600_000).toLong())
    } else {
        null
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Время чтения", StatsCalc.duration(stats.ms), Modifier.weight(1f))
        StatTile("Страниц", stats.pages.toString(), Modifier.weight(1f), hint = if (book.positions > 0) "из ${book.positions}" else null)
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Скорость", speed?.let { "${it.toInt()} стр/ч" } ?: "—", Modifier.weight(1f))
        StatTile(
            "Осталось",
            when {
                book.status == ReadStatus.Finished -> "дочитана"
                remaining != null -> "≈ $remaining"
                else -> "—"
            },
            Modifier.weight(1f),
        )
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Дней чтения", stats.days.toString(), Modifier.weight(1f))
        StatTile(
            "Заметки и слова",
            "${notes.count { it.bookId == book.id }} / ${words.count { it.bookId == book.id }}",
            Modifier.weight(1f),
        )
    }

    val perDay = remember(days, book.statsKey) {
        days.mapNotNull { (d, b) -> b[book.statsKey]?.let { e -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { it to e } } }.toMap()
    }
    ChartCard("Последние 14 дней", StatsCalc.series(perDay, 14))

    fun day(time: Long) = StatsCalc.date(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate())
    val dates = listOfNotNull(
        "Добавлена: ${day(book.addedAt)}",
        stats.firstDay?.let { "Начата: ${StatsCalc.date(it)}" },
        book.finishedAt?.takeIf { book.status == ReadStatus.Finished }?.let { "Прочитана: ${day(it)}" },
    )
    Text(dates.joinToString("\n"), color = AppColors.Faint, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
    if (speed == null) {
        Text(
            "Скорость появится после 5 минут чтения.",
            color = AppColors.Faint,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- sorting and view

@Composable
fun LibraryViewSheet(prefs: LibraryPrefs, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    AppSheet(onDismiss, title = "Вид и сортировка") {
        SettingsGroup("Сортировка") {
            LibrarySort.entries.forEach { sort ->
                val selected = sort == prefs.sort
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.98f) {
                            app.libraryPrefs.update {
                                // Dates and progress: newest first; names: А → Я.
                                val names = sort == LibrarySort.Title || sort == LibrarySort.Author
                                it.copy(sort = sort, descending = if (it.sort == sort) it.descending else !names)
                            }
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sort.title, color = if (selected) AppColors.Accent else AppColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (selected) Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
                }
            }
            val names = prefs.sort == LibrarySort.Title || prefs.sort == LibrarySort.Author
            val options = when {
                names -> listOf("А → Я", "Я → А")
                prefs.sort == LibrarySort.Progress -> listOf("Больше", "Меньше")
                else -> listOf("Сначала новые", "Сначала старые")
            }
            // Index 0 is the natural order: newest/biggest first, or А → Я.
            val first = if (names) !prefs.descending else prefs.descending
            SettingSegmented(null, options, if (first) 0 else 1) { i ->
                val wantFirst = i == 0
                app.libraryPrefs.update { it.copy(descending = if (names) !wantFirst else wantFirst) }
            }
        }
        SettingsGroup("Вид") {
            PickerRow(
                label = "Прогресс",
                icon = AppIcons.Percent,
                options = listOf(
                    PickerOption("percent", "Проценты", "30%"),
                    PickerOption("pages", "Страницы", "стр. 110 из 366"),
                ),
                selectedId = if (prefs.progressInPages) "pages" else "percent",
            ) { id -> app.libraryPrefs.update { it.copy(progressInPages = id == "pages") } }
            SettingSegmented(null, listOf("Сетка", "Список"), if (prefs.view == LibraryView.Grid) 0 else 1) { i ->
                app.libraryPrefs.update { it.copy(view = if (i == 0) LibraryView.Grid else LibraryView.List) }
            }
            if (prefs.view == LibraryView.Grid) {
                SettingSegmented("Размер обложек", listOf("Мелкие", "Средние", "Крупные"), prefs.coverSize) { i ->
                    app.libraryPrefs.update { it.copy(coverSize = i) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shelves

sealed class ShelfDialog {
    /** A new shelf; if [bookId] is set, the book goes on it right away. */
    data class Create(val bookId: String?) : ShelfDialog()
    data class Edit(val name: String) : ShelfDialog()
}

@Composable
fun ShelfDialogView(dialog: ShelfDialog, shelves: List<String>, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    val original = (dialog as? ShelfDialog.Edit)?.name.orEmpty()
    var name by remember(dialog) { mutableStateOf(original) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(dialog) { runCatching { focus.requestFocus() } }
    val clean = name.trim()
    val taken = clean != original && clean in shelves

    fun save() {
        if (clean.isEmpty() || taken) return
        when (dialog) {
            is ShelfDialog.Create -> {
                app.libraryPrefs.update { it.copy(shelves = (it.shelves + clean).distinct()) }
                dialog.bookId?.let { id ->
                    val book = app.library.books.value.firstOrNull { it.id == id }
                    if (book != null) scope.launch { app.library.setShelves(id, book.shelves + clean) }
                }
            }
            is ShelfDialog.Edit -> if (clean != original) {
                scope.launch { app.library.renameShelf(original, clean) }
                app.libraryPrefs.update { p ->
                    p.copy(
                        shelves = (p.shelves.map { if (it == original) clean else it } + clean).distinct(),
                        filter = if (p.filter == "shelf:$original") "shelf:$clean" else p.filter,
                    )
                }
            }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Panel,
        title = { Text(if (dialog is ShelfDialog.Create) "Новая полка" else "Полка", color = AppColors.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.Text, fontSize = 16.sp),
                    cursorBrush = SolidColor(AppColors.Accent),
                    modifier = Modifier.fillMaxWidth().height(48.dp).fieldSurface(appCorner(14)).padding(horizontal = 14.dp).focusRequester(focus),
                    decorationBox = { inner ->
                        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                if (name.isEmpty()) Text("Например, «Психология»", color = AppColors.Faint, fontSize = 16.sp, maxLines = 1)
                                inner()
                            }
                        }
                    },
                )
                if (taken) Text("Такая полка уже есть", color = Danger, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = clean.isNotEmpty() && !taken) {
                Text(if (dialog is ShelfDialog.Create) "Создать" else "Сохранить", color = AppColors.Accent)
            }
        },
        dismissButton = {
            if (dialog is ShelfDialog.Edit) {
                TextButton(onClick = {
                    scope.launch { app.library.renameShelf(original, null) }
                    app.libraryPrefs.update { p ->
                        p.copy(shelves = p.shelves - original, filter = if (p.filter == "shelf:$original") "all" else p.filter)
                    }
                    onDismiss()
                }) { Text("Удалить полку", color = Danger) }
            } else {
                TextButton(onClick = onDismiss) { Text("Отмена", color = AppColors.Muted) }
            }
        },
    )
}
