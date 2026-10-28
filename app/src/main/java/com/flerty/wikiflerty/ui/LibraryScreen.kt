package com.flerty.wikiflerty.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SHELF = "shelf:"

data class LibraryFilter(val id: String, val title: String, val icon: ImageVector, val matches: (Book) -> Boolean) {
    val isShelf: Boolean get() = id.startsWith(SHELF)
    val sectionTitle: String get() = if (id == "all") "Мои книги" else title
}

private fun baseFilters(): List<LibraryFilter> = listOf(
    LibraryFilter("all", "Все книги", AppIcons.Grid) { true },
    LibraryFilter(ReadStatus.Reading.id, "Читаю", AppIcons.Book) { it.status == ReadStatus.Reading },
    LibraryFilter(ReadStatus.Planned.id, "В планах", AppIcons.Clock) { it.status == ReadStatus.Planned },
    LibraryFilter(ReadStatus.Finished.id, "Прочитано", AppIcons.Check) { it.status == ReadStatus.Finished },
    LibraryFilter("favorite", "Избранное", AppIcons.StarFilled) { it.favorite },
    LibraryFilter(ReadStatus.New.id, "Не начаты", AppIcons.Plus) { it.status == ReadStatus.New },
)

fun progressLabel(book: Book, inPages: Boolean): String =
    if (inPages && book.positions > 0) {
        "стр. ${(book.progress * book.positions).toInt().coerceIn(1, book.positions)} из ${book.positions}"
    } else {
        "${(book.progress * 100).toInt()}%"
    }

fun statusColor(status: ReadStatus): Color = when (status) {
    ReadStatus.Reading -> AppColors.Accent
    ReadStatus.Finished -> Color(0xFF7ED3A0)
    ReadStatus.Planned -> Color(0xFFF2C46B)
    ReadStatus.New -> AppColors.Muted
}

fun statusIcon(status: ReadStatus): ImageVector = when (status) {
    ReadStatus.Reading -> AppIcons.Book
    ReadStatus.Planned -> AppIcons.Clock
    ReadStatus.Finished -> AppIcons.Check
    ReadStatus.New -> AppIcons.Plus
}

fun allShelves(prefs: LibraryPrefs, books: List<Book>): List<String> =
    (prefs.shelves + books.flatMap { it.shelves }).distinct()

private fun sorted(books: List<Book>, prefs: LibraryPrefs): List<Book> {
    val comparator: Comparator<Book> = when (prefs.sort) {
        LibrarySort.Recent -> compareBy { it.lastReadAt ?: it.addedAt }
        LibrarySort.Added -> compareBy { it.addedAt }
        LibrarySort.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LibrarySort.Author -> compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.author.ifBlank { "￿" } }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LibrarySort.Progress -> compareBy<Book> { if (it.status == ReadStatus.Finished) 1.0 else it.progress }
    }
    return books.sortedWith(if (prefs.descending) comparator.reversed() else comparator)
}

class LibraryControls {
    var showViewSheet by mutableStateOf(false)
    var shelfDialog by mutableStateOf<ShelfDialog?>(null)
    var scanFolder by mutableStateOf(false)
}

@Composable
fun LibraryTopActions(controls: LibraryControls) {
    val app = LocalContext.current.applicationContext as App
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolButton(AppIcons.ShelfAdd, "Новая полка") { controls.shelfDialog = ShelfDialog.Create(null) }
        ToolButton(if (prefs.view == LibraryView.Grid) AppIcons.ListView else AppIcons.Grid, "Вид") {
            app.libraryPrefs.update { it.copy(view = if (it.view == LibraryView.Grid) LibraryView.List else LibraryView.Grid) }
        }
        ToolButton(AppIcons.Sort, "Вид и сортировка") { controls.showViewSheet = true }
    }
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onAddBook: () -> Unit,
    controls: LibraryControls,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as App
    val books by viewModel.books.collectAsStateWithLifecycle()
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var menuBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    if (books.isEmpty()) {
        EmptyLibrary(onAddBook, { controls.scanFolder = true }, modifier)
        if (controls.scanFolder) FolderScanSheet(onDismiss = { controls.scanFolder = false }, onImport = viewModel::importAll)
        return
    }

    val shelves = allShelves(prefs, books)
    val filters = baseFilters() + shelves.map { name -> LibraryFilter(SHELF + name, name, AppIcons.Shelf) { name in it.shelves } }
    val active = filters.firstOrNull { it.id == prefs.filter } ?: filters.first()
    val q = query.trim()
    val shown = sorted(
        books.filter { active.matches(it) && (q.isEmpty() || it.title.contains(q, true) || it.author.contains(q, true)) },
        prefs,
    )
    val open = { book: Book -> viewModel.open(book) }
    val current = if (active.id == "all" && q.isEmpty()) {
        books.filter { it.status == ReadStatus.Reading && it.lastReadAt != null }.maxByOrNull { it.lastReadAt ?: 0L }
    } else {
        null
    }
    val stats by app.stats.days.collectAsStateWithLifecycle()
    val todayMs = stats[LocalDate.now().toString()]?.values?.sumOf { it.ms } ?: 0L
    val longPress = { book: Book -> menuBookId = book.id }
    val goalText = if (prefs.dailyGoalMinutes > 0) "Сегодня ${todayMs / 60_000} из ${prefs.dailyGoalMinutes} мин" else null

    Column(modifier) {
        SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp))

        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterButton(active, books.count(active.matches)) { showFilterSheet = true }
        }

        if (shown.isEmpty()) {
            Text(
                if (q.isNotEmpty()) "Ничего не нашлось" else "Здесь пока пусто",
                color = AppColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
            )
        } else if (prefs.view == LibraryView.Grid) {
            val tablet = LocalConfiguration.current.screenWidthDp >= 600
            val scale = if (tablet) 1.35f else 1f
            val minSize = when (prefs.coverSize) {
                0 -> 88.dp
                2 -> 150.dp
                else -> 110.dp
            } * scale
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minSize),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(if (tablet) 16.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (tablet) 16.dp else 10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (current != null) {
                    item(key = "continue-title", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Продолжить", icon = AppIcons.Book, action = if (goalText != null) { { HeaderAction(goalText, AppIcons.Target) } } else null) }
                    item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                        ContinueCard(current, viewModel.coverFile(current), todayMs, prefs.dailyGoalMinutes, prefs.progressInPages) { open(current) }
                    }
                }
                item(key = "books-title", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(active.sectionTitle, icon = AppIcons.Shelf, count = shown.size, action = { HeaderAction(prefs.sort.title, AppIcons.Sort) { controls.showViewSheet = true } }) }
                items(shown, key = { it.id }) { book ->
                    Box(motionItem()) {
                        BookCard(book, viewModel.coverFile(book), small = prefs.coverSize == 0, inPages = prefs.progressInPages, onClick = { open(book) }, onLongClick = { longPress(book) })
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (current != null) {
                    item(key = "continue-title") { SectionHeader("Продолжить", icon = AppIcons.Book, action = if (goalText != null) { { HeaderAction(goalText, AppIcons.Target) } } else null) }
                    item(key = "continue") {
                        ContinueCard(current, viewModel.coverFile(current), todayMs, prefs.dailyGoalMinutes, prefs.progressInPages) { open(current) }
                    }
                }
                item(key = "books-title") { SectionHeader(active.sectionTitle, icon = AppIcons.Shelf, count = shown.size, action = { HeaderAction(prefs.sort.title, AppIcons.Sort) { controls.showViewSheet = true } }) }
                items(shown, key = { it.id }) { book ->
                    Box(motionItem()) {
                        BookRow(book, viewModel.coverFile(book), inPages = prefs.progressInPages, onClick = { open(book) }, onLongClick = { longPress(book) })
                    }
                }
            }
        }
    }

    books.firstOrNull { it.id == menuBookId }?.let { book ->
        BookSheet(
            book = book,
            shelves = shelves,
            coverFile = viewModel.coverFile(book),
            onNewShelf = { controls.shelfDialog = ShelfDialog.Create(book.id) },
            onDelete = {
                menuBookId = null
                viewModel.delete(book)
            },
            onDismiss = { menuBookId = null },
        )
    }

    if (controls.scanFolder) FolderScanSheet(onDismiss = { controls.scanFolder = false }, onImport = viewModel::importAll)
    if (controls.showViewSheet) LibraryViewSheet(prefs, onDismiss = { controls.showViewSheet = false })

    if (showFilterSheet) {
        LibraryFilterSheet(
            filters = filters,
            counts = filters.associate { f -> f.id to books.count(f.matches) },
            selectedId = active.id,
            onSelect = { id ->
                app.libraryPrefs.update { it.copy(filter = id) }
                showFilterSheet = false
            },
            onEditShelf = { name -> controls.shelfDialog = ShelfDialog.Edit(name) },
            onDismiss = { showFilterSheet = false },
        )
    }

    controls.shelfDialog?.let { dialog ->
        ShelfDialogView(dialog, shelves, onDismiss = { controls.shelfDialog = null })
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp),
        cursorBrush = SolidColor(AppColors.Accent),
        modifier = modifier.height(44.dp).fieldSurface(appCorner(22)).padding(start = 14.dp, end = 6.dp),
        decorationBox = { inner ->
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Search, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
                Box(Modifier.padding(start = 10.dp).weight(1f)) {
                    if (value.isEmpty()) Text("Поиск по книгам", color = AppColors.Faint, fontSize = 15.sp, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier.size(32.dp).liveClick(CircleShape, sound = FeedbackSound.Close) { onChange("") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Close, "Очистить", tint = AppColors.Muted, modifier = Modifier.size(16.dp)) }
                }
            }
        },
    )
}

@Composable
private fun ToolButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).liveClick(CircleShape, sound = FeedbackSound.Tap, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = AppColors.Text, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun FilterButton(filter: LibraryFilter, count: Int, onClick: () -> Unit) {
    val shape = appCorner(22)
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
            .padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(filter.icon, null, tint = if (filter.id == "favorite") StarYellow else AppColors.Accent, modifier = Modifier.size(18.dp))
        Text(
            filter.title,
            color = AppColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            color = AppColors.Muted,
            fontSize = 13.sp,
            modifier = Modifier.clip(appCorner(10)).background(AppColors.Line).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp).rotate(90f))
    }
}

@Composable
private fun LibraryFilterSheet(
    filters: List<LibraryFilter>,
    counts: Map<String, Int>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onEditShelf: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss, title = "Показать") {
        SettingsGroup(null) {
            filters.filterNot { it.isShelf }.forEach { f ->
                FilterRow(f, counts[f.id] ?: 0, f.id == selectedId, onEdit = null) { onSelect(f.id) }
            }
        }
        val shelves = filters.filter { it.isShelf }
        if (shelves.isNotEmpty()) {
            SettingsGroup("Полки", AppIcons.Shelf) {
                shelves.forEach { f ->
                    FilterRow(f, counts[f.id] ?: 0, f.id == selectedId, onEdit = { onEditShelf(f.title) }) { onSelect(f.id) }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(filter: LibraryFilter, count: Int, selected: Boolean, onEdit: (() -> Unit)?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(filter.icon)
        Text(
            filter.title,
            color = if (selected) AppColors.Accent else AppColors.Text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
        )
        Text(count.toString(), color = AppColors.Faint, fontSize = 14.sp)
        if (onEdit != null) {
            Box(
                Modifier.padding(start = 4.dp).size(36.dp).liveClick(CircleShape, sound = FeedbackSound.Open, onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Pencil, "Изменить", tint = AppColors.Muted, modifier = Modifier.size(17.dp)) }
        }
        Box(Modifier.padding(start = 6.dp).size(22.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).clip(CircleShape).background(AppColors.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(CircleShape).background(AppColors.Accent))
    }
}

@Composable
private fun StatusBadge(book: Book, inPages: Boolean, compact: Boolean = false) {
    val color = statusColor(book.status)
    val text = when (book.status) {
        ReadStatus.Reading -> if (compact) progressLabel(book, false) else progressLabel(book, inPages)
        ReadStatus.Planned -> "В планах"
        ReadStatus.Finished -> "Прочитано"
        ReadStatus.New -> "Новая"
    }
    Row(
        Modifier.clip(appCorner(8)).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(statusIcon(book.status), null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun percent(book: Book) = "${(book.progress * 100).toInt()}%"

@Composable
private fun rememberCover(file: File?): ImageBitmap? {
    val cover by produceState<ImageBitmap?>(null, file) {
        value = file?.takeIf { it.exists() }?.let { withContext(Dispatchers.IO) { CoverCache.load(it) } }
    }
    return cover
}

@Composable
private fun CoverBox(book: Book, file: File?, modifier: Modifier, small: Boolean = false) {
    val cover = rememberCover(file)
    Box(modifier.background(AppColors.Field), contentAlignment = Alignment.Center) {
        if (cover != null) {
            Image(cover, book.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                book.title,
                color = AppColors.Muted,
                fontSize = if (small) 11.sp else 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
        if (book.favorite) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(if (small) 20.dp else 24.dp).clip(CircleShape)
                    .background(Color(0xB0101114)),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.StarFilled, "Избранное", tint = StarYellow, modifier = Modifier.size(if (small) 12.dp else 15.dp)) }
        }
    }
}

@Composable
private fun BookCard(book: Book, coverFile: File?, small: Boolean, inPages: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = appCorner(14)
    Column(
        Modifier
            .fillMaxHeight()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onLongClick = onLongClick, onClick = onClick)
            .fieldSurface(shape)
            .padding(if (small) 6.dp else 8.dp),
    ) {
        CoverBox(book, coverFile, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(appCorner(8)), small)
        if (book.status == ReadStatus.Reading || (book.status == ReadStatus.Planned && book.progress > 0)) {
            ProgressLine(book.progress.toFloat(), Modifier.fillMaxWidth().padding(top = 7.dp))
        }
        Text(
            book.title,
            color = AppColors.Text,
            fontSize = if (small) 12.sp else 14.sp,
            lineHeight = if (small) 15.sp else 18.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (!small) {
            Text(book.author.ifBlank { " " }, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        StatusBadge(book, inPages, compact = small)
    }
}

@Composable
private fun BookRow(book: Book, coverFile: File?, inPages: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = appCorner(16)
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onLongClick = onLongClick, onClick = onClick)
            .fieldSurface(shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverBox(book, coverFile, Modifier.size(width = 56.dp, height = 84.dp).clip(appCorner(6)), small = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(book.title, color = AppColors.Text, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (book.author.isNotBlank()) {
                Text(book.author, color = AppColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                StatusBadge(book, inPages)
                book.lastReadAt?.let { Text(relativeDay(it), color = AppColors.Faint, fontSize = 12.sp, maxLines = 1) }
            }
            if (book.status == ReadStatus.Reading || (book.status == ReadStatus.Planned && book.progress > 0)) {
                ProgressLine(book.progress.toFloat(), Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp))
            }
        }
    }
}

fun relativeDay(time: Long): String {
    val day = java.time.Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val diff = today.toEpochDay() - day.toEpochDay()
    return when {
        diff <= 0L -> "сегодня"
        diff == 1L -> "вчера"
        diff < 7L -> "${diff} " + StatsCalc.plural(diff.toInt(), "день", "дня", "дней") + " назад"
        else -> StatsCalc.date(day)
    }
}

@Composable
private fun ContinueCard(book: Book, coverFile: File?, todayMs: Long, goalMinutes: Int, inPages: Boolean, onClick: () -> Unit) {
    val shape = appCorner(20)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .background(Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.16f).compositeOver(AppColors.Field), AppColors.Field)))
            .border(1.dp, AppColors.Accent.copy(alpha = 0.22f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CoverBox(book, coverFile, Modifier.size(width = 66.dp, height = 99.dp).clip(appCorner(8)), small = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(AppIcons.Book, null, tint = AppColors.Accent, modifier = Modifier.size(14.dp))
                Text("Продолжить", color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(book.title, color = AppColors.Text, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (book.author.isNotBlank()) {
                Text(book.author, color = AppColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ProgressLine(book.progress.toFloat(), Modifier.fillMaxWidth().padding(top = 6.dp))
            Text(
                listOfNotNull(progressLabel(book, inPages), book.lastReadAt?.let { relativeDay(it) }).joinToString(" · "),
                color = AppColors.Faint,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        if (goalMinutes > 0) GoalRing(todayMs, goalMinutes, 54.dp)
    }
}

@Composable
private fun EmptyLibrary(onAddBook: () -> Unit, onScan: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Здесь будут твои книги", color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Добавь книгу в формате EPUB, FB2 или TXT из памяти телефона.",
            color = AppColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        val shape = appCorner(24)
        Box(
            Modifier.heightIn(min = 48.dp).liveClick(shape, sound = FeedbackSound.Open, onClick = onAddBook)
                .accentHighlight(shape).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Добавить книгу", color = accentContent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.heightIn(min = 44.dp).liveClick(shape, sound = FeedbackSound.Open, onClick = onScan)
                .border(1.dp, AppColors.Accent.copy(alpha = 0.35f), shape).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(AppIcons.FolderSearch, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            Text("Найти на телефоне", color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            color = AppColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) Text(hint, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1)
    }
}
