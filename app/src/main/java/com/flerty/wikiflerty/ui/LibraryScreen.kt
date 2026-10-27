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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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

/** Filter chips: everything, statuses, favorites, then the user's shelves. */
private data class LibraryFilter(val id: String, val title: String, val matches: (Book) -> Boolean)

private fun baseFilters(): List<LibraryFilter> = listOf(
    LibraryFilter("all", "Все") { true },
    LibraryFilter(ReadStatus.Reading.id, "Читаю") { it.status == ReadStatus.Reading },
    LibraryFilter(ReadStatus.Planned.id, "В планах") { it.status == ReadStatus.Planned },
    LibraryFilter(ReadStatus.Finished.id, "Прочитано") { it.status == ReadStatus.Finished },
    LibraryFilter("favorite", "Избранное") { it.favorite },
    LibraryFilter(ReadStatus.New.id, "Не начаты") { it.status == ReadStatus.New },
)

/** All shelves: the ones made by hand plus any that came with books from a backup. */
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

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onAddBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as App
    val books by viewModel.books.collectAsStateWithLifecycle()
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var menuBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var showViewSheet by rememberSaveable { mutableStateOf(false) }
    var shelfDialog by remember { mutableStateOf<ShelfDialog?>(null) }

    if (books.isEmpty()) {
        EmptyLibrary(onAddBook, modifier)
        return
    }

    val shelves = allShelves(prefs, books)
    val filters = baseFilters() + shelves.map { name -> LibraryFilter(SHELF + name, name) { name in it.shelves } }
    val active = filters.firstOrNull { it.id == prefs.filter } ?: filters.first()
    val q = query.trim()
    val shown = sorted(
        books.filter { active.matches(it) && (q.isEmpty() || it.title.contains(q, true) || it.author.contains(q, true)) },
        prefs,
    )
    val open = { book: Book -> viewModel.open(book) }
    val longPress = { book: Book -> menuBookId = book.id }

    Column(modifier) {
        // Search, list/grid switch, sorting.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SearchField(query, { query = it }, Modifier.weight(1f))
            ToolButton(if (prefs.view == LibraryView.Grid) AppIcons.ListView else AppIcons.Grid, "Вид") {
                app.libraryPrefs.update { it.copy(view = if (it.view == LibraryView.Grid) LibraryView.List else LibraryView.Grid) }
            }
            ToolButton(AppIcons.Sort, "Сортировка") { showViewSheet = true }
        }

        // Filter chips with counts.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            items(filters, key = { it.id }) { filter ->
                val count = books.count(filter.matches)
                val isShelf = filter.id.startsWith(SHELF)
                FilterChip(
                    title = filter.title,
                    count = count,
                    selected = filter.id == active.id,
                    onLongClick = if (isShelf) ({ shelfDialog = ShelfDialog.Edit(filter.title) }) else null,
                ) { app.libraryPrefs.update { it.copy(filter = filter.id) } }
            }
            item(key = "new-shelf") {
                val shape = appCorner(18)
                Box(
                    Modifier.height(36.dp).liveClick(shape, sound = FeedbackSound.Open) { shelfDialog = ShelfDialog.Create(null) }
                        .border(1.dp, AppColors.Line, shape).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(AppIcons.Plus, null, tint = AppColors.Muted, modifier = Modifier.size(16.dp))
                        Text("Полка", color = AppColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            Text(
                if (q.isNotEmpty()) "Ничего не нашлось" else "Здесь пока пусто. Долгое нажатие на книгу — статус, избранное и полки.",
                color = AppColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
            )
        } else if (prefs.view == LibraryView.Grid) {
            val minSize = when (prefs.coverSize) {
                0 -> 88.dp
                2 -> 150.dp
                else -> 110.dp
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minSize),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { book ->
                    BookCard(book, viewModel.coverFile(book), small = prefs.coverSize == 0, onClick = { open(book) }, onLongClick = { longPress(book) })
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { book ->
                    BookRow(book, viewModel.coverFile(book), onClick = { open(book) }, onLongClick = { longPress(book) })
                }
            }
        }
    }

    books.firstOrNull { it.id == menuBookId }?.let { book ->
        BookSheet(
            book = book,
            shelves = shelves,
            coverFile = viewModel.coverFile(book),
            onNewShelf = { shelfDialog = ShelfDialog.Create(book.id) },
            onDelete = {
                menuBookId = null
                viewModel.delete(book)
            },
            onDismiss = { menuBookId = null },
        )
    }

    if (showViewSheet) LibraryViewSheet(prefs, onDismiss = { showViewSheet = false })

    shelfDialog?.let { dialog ->
        ShelfDialogView(dialog, shelves, onDismiss = { shelfDialog = null })
    }
}

// ---------------------------------------------------------------- toolbar

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
private fun FilterChip(title: String, count: Int, selected: Boolean, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    val shape = appCorner(18)
    Row(
        Modifier
            .height(36.dp)
            .liveClick(shape, sound = FeedbackSound.Tap, onLongClick = onLongClick, onClick = onClick)
            .then(if (selected) Modifier.accentHighlight(shape) else Modifier.fieldSurface(shape))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = if (selected) accentContent else AppColors.Text, fontSize = 14.sp, maxLines = 1)
        Text(
            count.toString(),
            color = if (selected) accentContent.copy(alpha = 0.7f) else AppColors.Faint,
            fontSize = 12.sp,
        )
    }
}

// ---------------------------------------------------------------- books

/** A thin, even progress line (no gaps or dots). */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).clip(CircleShape).background(AppColors.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(CircleShape).background(AppColors.Accent))
    }
}

/** "19%", "Прочитано", "В планах" or "Новая". */
private fun statusLine(book: Book): String = when (book.status) {
    ReadStatus.Finished -> "Прочитано"
    ReadStatus.Planned -> if (book.progress > 0) "В планах · ${percent(book)}" else "В планах"
    ReadStatus.New -> "Новая"
    ReadStatus.Reading -> percent(book)
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
            ) { Icon(AppIcons.HeartFilled, "Избранное", tint = Color(0xFFF28B9B), modifier = Modifier.size(if (small) 12.dp else 14.dp)) }
        }
        if (book.status == ReadStatus.Finished) {
            Box(
                Modifier.align(Alignment.TopStart).padding(5.dp).size(if (small) 20.dp else 24.dp).clip(CircleShape)
                    .background(AppColors.Accent),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Check, "Прочитано", tint = AppColors.OnAccent, modifier = Modifier.size(if (small) 12.dp else 14.dp)) }
        }
    }
}

@Composable
private fun BookCard(book: Book, coverFile: File?, small: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = appCorner(10)
    Column(
        Modifier.liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onLongClick = onLongClick, onClick = onClick),
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
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        val details = listOfNotNull(book.author.takeIf { it.isNotBlank() && !small }, statusLine(book)).joinToString(" · ")
        Text(details, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BookRow(book: Book, coverFile: File?, onClick: () -> Unit, onLongClick: () -> Unit) {
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
            val details = listOfNotNull(statusLine(book), book.lastReadAt?.let { relativeDay(it) }).joinToString(" · ")
            Text(details, color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (book.status == ReadStatus.Reading || (book.status == ReadStatus.Planned && book.progress > 0)) {
                ProgressLine(book.progress.toFloat(), Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp))
            }
        }
    }
}

/** "сегодня", "вчера", "3 дня назад" or a date. */
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

// ---------------------------------------------------------------- empty library

@Composable
private fun EmptyLibrary(onAddBook: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Здесь будут твои книги", color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Добавь файл EPUB из памяти телефона. Ещё можно открыть книгу из файлового менеджера через «Открыть с помощью».",
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
        ) { Text("Добавить EPUB", color = accentContent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}
