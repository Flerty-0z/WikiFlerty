package com.flerty.wikiflerty.ui

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.drawOutline

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth

import androidx.compose.ui.draw.drawBehind

import androidx.compose.ui.draw.shadow

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
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.togetherWith
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.LibraryPrefs
import com.flerty.wikiflerty.data.LibrarySort
import com.flerty.wikiflerty.data.LibraryView
import com.flerty.wikiflerty.data.ReadStatus
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
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
    if (book.paper && book.positions > 0) {
        "${Math.round(book.progress * book.positions).toInt().coerceIn(0, book.positions)}/${book.positions}"
    } else if (inPages && book.positions > 0) {
        "${(book.progress * book.positions).toInt().coerceIn(1, book.positions)}/${book.positions}"
    } else {
        "${(book.progress * 100).toInt()}%"
    }

fun statusColor(status: ReadStatus): Color = when (status) {
    ReadStatus.Reading -> AppColors.Accent
    ReadStatus.Finished -> AppColors.Good
    ReadStatus.Planned -> AppColors.Warn
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
    var searchOpen by mutableStateOf(false)
}

@Composable
fun LibraryTopActions(controls: LibraryControls) {
    val app = LocalContext.current.applicationContext as App
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolButton(AppIcons.ShelfAdd, "Новая полка") { controls.shelfDialog = ShelfDialog.Create(null) }
        val next = LibraryView.entries[(prefs.view.ordinal + 1) % LibraryView.entries.size]
        ToolButton(when (next) { LibraryView.Shelves -> AppIcons.Shelf; LibraryView.Grid -> AppIcons.Grid; LibraryView.List -> AppIcons.ListView }, "Вид") {
            app.libraryPrefs.update { it.copy(view = next) }
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
    val goalText = if (prefs.dailyGoalMinutes > 0) "Сегодня ${todayMs / 60_000}/${prefs.dailyGoalMinutes} мин" else null

    val counts = filters.associate { f -> f.id to books.count(f.matches) }
    val recent = books.sortedByDescending { it.lastReadAt ?: it.addedAt }
    val coversFor = filters.associate { f -> f.id to recent.filter(f.matches).map { it.id } }
    val tablet = LocalConfiguration.current.screenWidthDp >= 600
    val grid = prefs.view == LibraryView.Grid
    val columns = when (prefs.coverSize) {
        0 -> 4
        2 -> 2
        else -> 3
    } + if (tablet) 2 else 0

    Column(modifier) {
        androidx.compose.animation.AnimatedVisibility(visible = controls.searchOpen || query.isNotEmpty()) {
            SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                FilterButton(active, coversFor[active.id].orEmpty(), counts[active.id] ?: 0) { showFilterSheet = true }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .liveClick(CircleShape, sound = FeedbackSound.Open) { controls.showViewSheet = true }
                    .fieldSurface(CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Sort, "Вид и сортировка", tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
        }

        androidx.compose.animation.Crossfade(
            targetState = LibraryLayout(prefs.view, columns, prefs.coverSize < 2),
            animationSpec = Motion.tween(320),
            label = "library-view",
        ) { layout ->
            val lgrid = layout.view == LibraryView.Grid
                if (layout.view == LibraryView.Shelves) {
                val planks = if (active.id == "all" && q.isEmpty()) {
                    val named = shelves.map { name ->
                        PlankSpec("shelf:$name", name, books.filter { name in it.shelves }, { controls.shelfDialog = ShelfDialog.Edit(name) }, { controls.shelfDialog = ShelfDialog.Edit(name) }, wrap = false)
                    }
                    val rest = books.filter { b -> b.shelves.none { it in shelves } }
                    named + if (rest.isNotEmpty() || named.isEmpty()) listOf(PlankSpec("rest", if (named.isEmpty()) null else "Без полки", rest, onAddBook, null, wrap = named.isEmpty())) else emptyList()
                } else {
                    val edit = if (active.isShelf) ({ controls.shelfDialog = ShelfDialog.Edit(active.title) }) else null
                    listOf(PlankSpec(active.id, if (active.isShelf) active.title else null, shown, edit ?: if (q.isEmpty()) onAddBook else null, edit, wrap = true))
                }
                BookcaseView(
                    planks = planks,
                    onBook = { longPress(it) },
                    modifier = Modifier.fillMaxSize(),
                    animationKey = active.id + "|" + q,
                    live = prefs.liveShelf,
                    behave = prefs.shelfBehavior,
                    onMove = if (active.id == "all" && q.isEmpty() && shelves.isNotEmpty()) { book, from, to ->
                        val src = from.removePrefix("shelf:").takeIf { from.startsWith("shelf:") }
                        val dst = to.removePrefix("shelf:").takeIf { to.startsWith("shelf:") }
                        val next = (book.shelves.filter { it != src } + listOfNotNull(dst)).distinct()
                        app.appScope.launch { app.library.setShelves(book.id, next) }
                    } else null,
                    footer = {
                        if (shown.isEmpty() && q.isNotEmpty()) {
                            Text("Ничего не нашлось", color = AppColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        } else if (active.id == "all" && q.isEmpty()) {
                            NewShelfButton { controls.shelfDialog = ShelfDialog.Create(null) }
                        }
                    },
                )
            } else LazyVerticalGrid(
                columns = if (lgrid) GridCells.Fixed(layout.columns) else GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(if (lgrid) 12.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(if (lgrid) 16.dp else 10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (shown.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (q.isNotEmpty()) "Ничего не нашлось" else "Здесь пока пусто",
                            color = AppColors.Muted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                        )
                    }
                }
                itemsIndexed(shown, key = { _, b -> b.id }) { index, book ->
                    Box(motionItem().riseIn(active.id + "|" + q.isEmpty(), index)) {
                        if (lgrid) {
                            BookCard(book, viewModel.coverFile(book), small = layout.small, inPages = prefs.progressInPages, onClick = { longPress(book) }, onLongClick = { longPress(book) })
                        } else {
                            BookRow(book, viewModel.coverFile(book), inPages = prefs.progressInPages, onClick = { longPress(book) }, onLongClick = { longPress(book) })
                        }
                    }
                }
                if (grid && q.isEmpty()) {
                    item(key = "add-tile") {
                        AddCoverTile(Modifier.fillMaxWidth().aspectRatio(2f / 3f), onAddBook)
                    }
                }
            }
        }
    }

    books.firstOrNull { it.id == menuBookId }?.let { book ->
        BookSheet(
            book = book,
            coverFile = viewModel.coverFile(book),
            onRead = { viewModel.open(book) },
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
            coversFor = coversFor,
            selectedId = active.id,
            onSelect = { id ->
                app.libraryPrefs.update { it.copy(filter = id) }
                showFilterSheet = false
            },
            onEditShelf = { name -> controls.shelfDialog = ShelfDialog.Edit(name) },
            onReorder = { order -> app.libraryPrefs.update { it.copy(shelves = order) } },
            onNewShelf = {
                showFilterSheet = false
                controls.shelfDialog = ShelfDialog.Create(null)
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    controls.shelfDialog?.let { dialog ->
        ShelfDialogView(dialog, shelves, onDismiss = { controls.shelfDialog = null })
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier, placeholder: String = "Поиск по книгам") {
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
                    if (value.isEmpty()) Text(placeholder, color = AppColors.Faint, fontSize = 15.sp, maxLines = 1)
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
private fun EmptyCover(width: androidx.compose.ui.unit.Dp) {
    val shape = appCorner(6)
    Box(
        Modifier
            .size(width = width, height = width * 1.5f)
            .clip(shape)
            .background(AppColors.Line.copy(alpha = 0.35f))
            .border(1.dp, AppColors.PanelBorder, shape),
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.Close, "Пусто", tint = AppColors.Faint, modifier = Modifier.size(width * 0.5f)) }
}

@Composable
private fun FilterCovers(ids: List<String>, width: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(width = width * 2.3f + 4.dp, height = width * 1.5f + 6.dp), contentAlignment = Alignment.CenterStart) {
        if (ids.isEmpty()) EmptyCover(width) else CoverStack(ids, width)
    }
}

@Composable
private fun FilterButton(filter: LibraryFilter, ids: List<String>, count: Int, onClick: () -> Unit) {
    val shape = appCorner(16)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
            .padding(start = 10.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ids.isEmpty()) EmptyCover(18.dp) else CoverStack(ids, 18.dp)
        Icon(filter.icon, null, tint = if (filter.id == "favorite") StarYellow else AppColors.Accent, modifier = Modifier.size(16.dp))
        Text(
            filter.title,
            color = AppColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CountBadge(count, AppColors.Muted, AppColors.Line)
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp).rotate(90f))
    }
}

@Composable
private fun LibraryFilterSheet(
    filters: List<LibraryFilter>,
    counts: Map<String, Int>,
    coversFor: Map<String, List<String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onEditShelf: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onNewShelf: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss, title = "Показать") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.filterNot { it.isShelf }.forEach { f ->
                FilterRow(f, coversFor[f.id].orEmpty(), counts[f.id] ?: 0, f.id == selectedId, onEdit = null) { onSelect(f.id) }
            }
        }
        val shelves = filters.filter { it.isShelf }
        if (shelves.isNotEmpty()) {
            TabbedFrame("Полки", icon = AppIcons.Shelf, trailing = { CountBadge(shelves.size) }) {
                ShelfOrderList(shelves, onReorder) { f, mod ->
                    FilterRow(f, coversFor[f.id].orEmpty(), counts[f.id] ?: 0, f.id == selectedId, onEdit = { onEditShelf(f.title) }, modifier = mod) { onSelect(f.id) }
                }
            }
        }
        val shape = appCorner(14)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onNewShelf)
                .border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingIcon(AppIcons.ShelfAdd)
            Text("Новая полка", color = AppColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun FilterRow(filter: LibraryFilter, ids: List<String>, count: Int, selected: Boolean, onEdit: (() -> Unit)?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = appCorner(14)
    Row(
        modifier
            .fillMaxWidth()
            .sheetReveal()
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.98f, onClick = onClick)
            .background(androidx.compose.animation.animateColorAsState(if (selected) AppColors.Accent.copy(alpha = 0.14f) else AppColors.Line.copy(alpha = 0.45f), Motion.fade(240), label = "row-bg").value, shape)
            .border(1.dp, androidx.compose.animation.animateColorAsState(if (selected) AppColors.Accent.copy(alpha = 0.45f) else AppColors.PanelBorder, Motion.fade(240), label = "row-edge").value, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 3.dp, end = 4.dp)) {
            FilterCovers(ids, 24.dp)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-3).dp)
                    .heightIn(min = 12.dp)
                    .widthIn(min = 12.dp)
                    .background(AppColors.Accent, CircleShape)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", color = AppColors.OnAccent, fontSize = 7.5.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                Icon(filter.icon, null, tint = if (filter.id == "favorite") StarYellow else AppColors.Accent, modifier = Modifier.size(16.dp))
            }
            Text(
                filter.title,
                color = if (selected) AppColors.Accent else AppColors.Text,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onEdit != null) {
            Box(
                Modifier.size(34.dp).liveClick(CircleShape, sound = FeedbackSound.Open, onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Pencil, "Изменить", tint = AppColors.Muted, modifier = Modifier.size(17.dp)) }
        }
    }
}

@Composable
private fun ShelfOrderList(
    shelves: List<LibraryFilter>,
    onReorder: (List<String>) -> Unit,
    row: @Composable (LibraryFilter, Modifier) -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = with(density) { 8.dp.toPx() }
    val feedback = LocalFeedback.current
    val order = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val heights = remember { HashMap<String, Int>() }
    val byId = shelves.associateBy { it.id }
    if (dragging == null) {
        val ids = shelves.map { it.id }
        if (order.toList() != ids) {
            order.clear()
            order.addAll(ids)
        }
    }
    val scope = rememberCoroutineScope()
    val settle = remember { androidx.compose.animation.core.Animatable(0f) }
    var settling by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEachIndexed { index, id ->
            val f = byId[id] ?: return@forEachIndexed
            androidx.compose.runtime.key(id) {
                val shift = remember { androidx.compose.animation.core.Animatable(0f) }
                val lastIndex = remember { intArrayOf(index) }
                if (lastIndex[0] != index) {
                    val delta = (lastIndex[0] - index) * ((heights[id] ?: 0) + gap)
                    lastIndex[0] = index
                    if (dragging != id) {
                        androidx.compose.runtime.SideEffect {
                            scope.launch {
                                shift.snapTo(shift.value + delta)
                                shift.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 520f))
                            }
                        }
                    }
                }
                val active = dragging == id
                val lift by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, Motion.tween(160), label = "shelf-lift")
                val mod = Modifier
                    .zIndex(if (active || settling == id) 2f else 0f)
                    .onGloballyPositioned { heights[id] = it.size.height }
                    .graphicsLayer {
                        translationY = when {
                            active -> offset
                            settling == id -> settle.value
                            else -> shift.value
                        }
                        val sc = 1f + 0.035f * lift
                        scaleX = sc
                        scaleY = sc
                        shadowElevation = 18f * lift
                        shape = appCorner(14)
                        clip = false
                    }
                    .pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = id
                                offset = 0f
                                feedback?.play(FeedbackSound.Toggle)
                            },
                            onDragEnd = {
                                val rest = offset
                                dragging = null
                                settling = id
                                onReorder(order.map { byId[it]?.title ?: it.removePrefix("shelf:") })
                                scope.launch {
                                    settle.snapTo(rest)
                                    settle.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 420f))
                                    if (settling == id) settling = null
                                }
                            },
                            onDragCancel = {
                                val rest = offset
                                dragging = null
                                settling = id
                                scope.launch {
                                    settle.snapTo(rest)
                                    settle.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 420f))
                                    if (settling == id) settling = null
                                }
                            },
                        ) { change, amount ->
                            change.consume()
                            offset += amount.y
                            val i = order.indexOf(id)
                            if (offset > 0 && i < order.lastIndex) {
                                val next = order[i + 1]
                                val step = (heights[next] ?: 0) + gap
                                if (step > 0 && offset > step / 2) {
                                    order.removeAt(i)
                                    order.add(i + 1, id)
                                    offset -= step
                                    feedback?.play(FeedbackSound.Toggle)
                                }
                            } else if (offset < 0 && i > 0) {
                                val prev = order[i - 1]
                                val step = (heights[prev] ?: 0) + gap
                                if (step > 0 && -offset > step / 2) {
                                    order.removeAt(i)
                                    order.add(i - 1, id)
                                    offset += step
                                    feedback?.play(FeedbackSound.Toggle)
                                }
                            }
                        }
                    }
                row(f, mod)
            }
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
        ReadStatus.Planned -> "Хочу"
        ReadStatus.Finished -> "Прочитано"
        ReadStatus.New -> "Новая"
    }
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.13f)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
private fun rememberCover(file: File?): ImageBitmap? {
    val cover by produceState(CoverCache.peek(file), file) {
        value = file?.let { withContext(Dispatchers.IO) { CoverCache.load(it) } }
    }
    return cover
}

@Composable
private fun CoverBox(book: Book, file: File?, modifier: Modifier, small: Boolean = false, showStatus: Boolean = true, plaque: Boolean = false, inPages: Boolean = false, bare: Boolean = false) {
    val cover = rememberCover(file)
    Box(modifier.background(AppColors.Field), contentAlignment = Alignment.Center) {
        if (cover != null) {
            Image(cover, book.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (androidx.compose.runtime.remember(file) { file == null || !file.exists() }) {
            GeneratedCover(book.title, book.author, Modifier.fillMaxSize())
        }
        val rating = book.rating?.takeIf { it > 0f }
        val chipBg = Color(0xD9100D0B)
        if (plaque) {
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(if (small) 5.dp else 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (rating != null) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(chipBg)
                            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                            .padding(horizontal = if (small) 6.dp else 8.dp, vertical = if (small) 2.dp else 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(AppIcons.StarFilled, "Оценка", tint = AppColors.Accent, modifier = Modifier.size(if (small) 10.dp else 12.dp))
                        Text(ratingText(rating), color = AppColors.Accent, fontSize = if (small) 10.sp else 11.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                    }
                }
                if (book.paper) {
                    Box(
                        Modifier.size(if (small) 20.dp else 24.dp).clip(CircleShape).background(chipBg),
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Pages, "Бумажная книга", tint = Color.White, modifier = Modifier.size(if (small) 11.dp else 13.dp)) }
                }
                Spacer(Modifier.weight(1f))
                androidx.compose.animation.AnimatedVisibility(
                    visible = book.favorite,
                    enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 500f), initialScale = 0.2f) + androidx.compose.animation.fadeIn(Motion.fade(160)),
                    exit = androidx.compose.animation.scaleOut(Motion.tween(160), targetScale = 0.4f) + androidx.compose.animation.fadeOut(Motion.fade(140)),
                ) {
                    Box(
                        Modifier.size(if (small) 22.dp else 26.dp).clip(CircleShape).background(chipBg).border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.StarFilled, "Избранное", tint = StarYellow, modifier = Modifier.size(if (small) 12.dp else 14.dp)) }
                }
            }
            CoverEdgeProgress(book, inPages, small, Modifier.align(Alignment.BottomCenter))
        } else {
            if (book.paper) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(if (showStatus) 5.dp else 3.dp).size(if (small) 20.dp else 24.dp).clip(CircleShape)
                        .background(Color(0xB0101114)),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Pages, "Бумажная книга", tint = Color.White, modifier = Modifier.size(if (small) 12.dp else 14.dp)) }
            }
            if (!bare) {
                rating?.let { RatingBadge(it, Modifier.align(Alignment.BottomStart).padding(5.dp), small) }
                if (book.favorite) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(5.dp).size(if (small) 20.dp else 24.dp).clip(CircleShape)
                            .background(Color(0xB0101114)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.StarFilled, "Избранное", tint = StarYellow, modifier = Modifier.size(if (small) 12.dp else 15.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BookCard(book: Book, coverFile: File?, small: Boolean, inPages: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = appCorner(16)
    Column(
        Modifier
            .fillMaxHeight()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.96f, onLongClick = onLongClick, onClick = onClick)
            .fieldSurface(shape)
            .padding(if (small) 6.dp else 8.dp),
    ) {
        CoverBox(
            book,
            coverFile,
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).coverShadow(appCorner(10)).kitCover(appCorner(10)).clip(appCorner(10)),
            small,
            plaque = true,
            inPages = inPages,
        )
        Text(
            book.title,
            color = AppColors.Text,
            fontSize = if (small) 12.sp else 13.sp,
            lineHeight = if (small) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        StatusPill(book.status, small, Modifier.fillMaxWidth().padding(top = 6.dp), framed = true, wide = true)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BookRow(book: Book, coverFile: File?, inPages: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = appCorner(18)
    val (label, color) = progressBadge(book, inPages)
    val finished = book.status == ReadStatus.Finished
    val fraction by animateFloatAsState(if (finished) 1f else book.progress.toFloat().coerceIn(0f, 1f), Motion.tween(700), label = "row-progress")
    Box(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onLongClick = onLongClick, onClick = onClick)
            .fieldSurface(shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CoverBox(book, coverFile, Modifier.size(width = 52.dp, height = 78.dp).coverShadow(appCorner(6)).clip(appCorner(6)), small = true, showStatus = false, bare = true)
                FitText(label, color, 11.5.sp, Modifier.width(56.dp).wrapContentWidth(Alignment.CenterHorizontally), weight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.title, color = AppColors.Text, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(book.author, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusPill(book.status, true, framed = true)
                    book.rating?.takeIf { it > 0f }?.let { r ->
                        RowChip {
                            Icon(AppIcons.StarFilled, "Оценка", tint = AppColors.Accent, modifier = Modifier.size(12.dp))
                            Text(ratingText(r), color = AppColors.Accent, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                    if (book.favorite) {
                        RowChip { Icon(AppIcons.StarFilled, "Избранное", tint = StarYellow, modifier = Modifier.size(13.dp)) }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(AppColors.Line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color))
        }
    }
}

@Composable
private fun RowChip(content: @Composable () -> Unit) {
    val shape = appCorner(10)
    Row(
        Modifier
            .height(30.dp)
            .clip(shape)
            .background(AppColors.Line.copy(alpha = 0.35f))
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun ProgressWithPercent(book: Book, inPages: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(AppColors.Line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(book.progress.toFloat().coerceIn(0f, 1f)).clip(CircleShape).background(AppColors.Accent))
        }
        Text(progressLabel(book, inPages), color = AppColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
    }
}

private fun Modifier.coverShadow(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.shadow(6.dp, shape, ambientColor = Color(0x66503010), spotColor = Color(0x66503010))

@Composable
private fun AddCoverTile(modifier: Modifier, onClick: () -> Unit) {
    val shape = appCorner(10)
    Box(
        modifier
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.95f, onClick = onClick)
            .dashedBorder(AppColors.PanelBorder, shape),
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.Plus, "Добавить книгу", tint = AppColors.Faint, modifier = Modifier.size(26.dp)) }
}

private fun Modifier.dashedBorder(color: Color, shape: androidx.compose.ui.graphics.Shape): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline,
        color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        ),
    )
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
            "Добавь книгу в формате EPUB, FB2, TXT или PDF из памяти телефона.",
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
private fun CoverEdgeProgress(book: Book, inPages: Boolean, small: Boolean, modifier: Modifier = Modifier) {
    val (label, color) = progressBadge(book, inPages)
    val finished = book.status == ReadStatus.Finished
    val fraction by animateFloatAsState(if (finished) 1f else book.progress.toFloat().coerceIn(0f, 1f), Motion.tween(700), label = "cover-edge")
    val text = if (small) 10.sp else 11.5.sp
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = color,
            fontSize = text,
            lineHeight = text,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .padding(bottom = if (small) 6.dp else 8.dp)
                .clip(CircleShape)
                .background(Color(0xD9100D0B))
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .padding(horizontal = if (small) 7.dp else 9.dp, vertical = if (small) 2.dp else 3.dp),
        )
        Box(Modifier.fillMaxWidth().height(if (small) 4.dp else 5.dp).background(Color(0x66000000))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color))
        }
    }
}

@Composable
private fun CoverProgress(book: Book, inPages: Boolean, small: Boolean, modifier: Modifier = Modifier) {
    val (label, color) = progressBadge(book, inPages)
    val finished = book.status == ReadStatus.Finished
    val fraction by animateFloatAsState(if (finished) 1f else book.progress.toFloat().coerceIn(0f, 1f), Motion.tween(700), label = "cover-progress")
    val text = if (small) 9.5.sp else 10.5.sp
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = if (small) 4.dp else 6.dp, vertical = if (small) 4.dp else 6.dp)
            .clip(CircleShape)
            .background(Color(0xD9100D0B))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .heightIn(min = if (small) 18.dp else 22.dp)
            .padding(horizontal = if (small) 6.dp else 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(if (small) 4.dp else 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(CircleShape).background(color))
        }
        Text(
            label,
            color = Color.White,
            fontSize = text,
            lineHeight = text,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF16120F))
                .border(1.dp, color.copy(alpha = 0.8f), CircleShape)
                .padding(horizontal = if (small) 5.dp else 7.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ProgressLineWithLabel(book: Book, inPages: Boolean, small: Boolean, modifier: Modifier = Modifier) {
    val (label, color) = progressBadge(book, inPages)
    val finished = book.status == ReadStatus.Finished
    val fraction by animateFloatAsState(if (finished) 1f else book.progress.toFloat().coerceIn(0f, 1f), Motion.tween(700), label = "card-progress")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f).height(if (small) 5.dp else 6.dp).clip(CircleShape).background(AppColors.Line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(CircleShape).background(color))
        }
        Text(label, color = color, fontSize = if (small) 9.5.sp else 10.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
    }
}

@Composable
fun StatusPill(status: ReadStatus, small: Boolean, modifier: Modifier = Modifier, framed: Boolean = false, wide: Boolean = false) {
    val label = when (status) {
        ReadStatus.Reading -> "Читаю"
        ReadStatus.Planned -> "В планах"
        ReadStatus.Finished -> "Прочитано"
        ReadStatus.New -> "Новая"
    }
    val tint = AppColors.Accent
    val shape = appCorner(if (small) 10 else 12)
    val box = if (small) 20.dp else 24.dp
    Row(
        modifier
            .clip(shape)
            .background(AppColors.Line.copy(alpha = 0.35f))
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(start = 4.dp, end = if (small) 6.dp else 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (small) 5.dp else 7.dp, if (wide) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        Box(
            Modifier
                .size(box)
                .clip(appCorner(7))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.08f))))
                .border(1.dp, tint.copy(alpha = 0.18f), appCorner(7)),
            contentAlignment = Alignment.Center,
        ) { Icon(statusIcon(status), null, tint = tint, modifier = Modifier.size(box * 0.55f)) }
        if (wide || framed || !small) {
            Text(
                label,
                color = AppColors.Text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = if (small) 11.sp else 12.5.sp, stepSize = 0.5.sp),
            )
        }
    }
}

private fun progressBadge(book: Book, inPages: Boolean): Pair<String, Color> {
    val finished = book.status == ReadStatus.Finished
    val label = when {
        finished && book.positions > 0 && inPages -> "${book.positions}/${book.positions}"
        finished -> "100%"
        else -> progressLabel(book, inPages)
    }
    return label to if (finished) AppColors.Good else AppColors.Accent
}

@Composable
fun ThickProgress(book: Book, inPages: Boolean, small: Boolean, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val finished = book.status == ReadStatus.Finished
    val fraction = if (finished) 1f else book.progress.toFloat().coerceIn(0f, 1f)
    val label = when {
        finished && book.positions > 0 && inPages -> "${book.positions}/${book.positions}"
        finished -> "100%"
        else -> progressLabel(book, inPages)
    }
    val fill = if (finished) AppColors.Good else AppColors.Accent
    Box(modifier.fillMaxWidth().height(if (!showLabel) 10.dp else if (small) 18.dp else 20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (small) 10.dp else 12.dp)
                .clip(CircleShape)
                .background(AppColors.Line),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(CircleShape).background(fill))
        }
        if (showLabel) Box(
            Modifier
                .clip(CircleShape)
                .background(AppColors.Panel)
                .border(1.dp, fill.copy(alpha = 0.6f), CircleShape)
                .padding(horizontal = if (small) 6.dp else 8.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = AppColors.Text, fontSize = if (small) 9.5.sp else 10.5.sp, lineHeight = if (small) 12.sp else 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        }
    }
}

private data class LibraryLayout(val view: LibraryView, val columns: Int, val small: Boolean)

@Composable
fun TagPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    count: Int? = null,
    small: Boolean = false,
    wide: Boolean = false,
) {
    val shape = appCorner(if (small) 10 else 12)
    val box = if (small) 20.dp else 24.dp
    Row(
        modifier
            .clip(shape)
            .background(AppColors.Line.copy(alpha = 0.35f))
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(start = 4.dp, end = if (small) 7.dp else 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (small) 5.dp else 7.dp, if (wide) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        Box(
            Modifier
                .size(box)
                .clip(appCorner(7))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.08f))))
                .border(1.dp, tint.copy(alpha = 0.18f), appCorner(7)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(box * 0.55f)) }
        Text(
            label,
            color = AppColors.Text,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = if (small) 11.sp else 12.5.sp, stepSize = 0.5.sp),
            modifier = Modifier.weight(1f, fill = false),
        )
        if (count != null) {
            Text(count.toString(), color = tint, fontSize = if (small) 11.sp else 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        }
    }
}
