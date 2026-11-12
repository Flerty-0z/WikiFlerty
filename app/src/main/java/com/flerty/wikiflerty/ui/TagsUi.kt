package com.flerty.wikiflerty.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.BookSeries
import com.flerty.wikiflerty.data.ReadStatus
import java.io.File
import kotlinx.coroutines.launch

const val TagFilterPrefix = "tag:"

class TagCount(val name: String, val count: Int)

class SeriesInfo(val name: String, val books: List<Book>, val done: Int) {
    val complete: Boolean get() = books.size >= 2 && done == books.size
}

fun Book.hasTag(name: String): Boolean = tags.any { it.removePrefix(SpoilerMark).equals(name, true) }

fun tagCounts(books: List<Book>): List<TagCount> =
    books.flatMap { b -> b.tags.map { it.removePrefix(SpoilerMark) }.distinctBy { it.lowercase() } }
        .groupBy { it.lowercase() }
        .map { (_, same) -> TagCount(same.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: same.first(), same.size) }
        .sortedWith(compareByDescending<TagCount> { it.count }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

fun tagFilterNames(id: String): List<String> =
    if (id.startsWith(TagFilterPrefix)) id.removePrefix(TagFilterPrefix).split('|').map { it.trim() }.filter { it.isNotEmpty() } else emptyList()

fun tagFilterId(names: Collection<String>): String = TagFilterPrefix + names.joinToString("|")

fun seriesList(books: List<Book>): List<SeriesInfo> =
    books.flatMap { b -> b.series.map { it.name.trim() to b } }
        .filter { it.first.isNotEmpty() }
        .groupBy { it.first.lowercase() }
        .map { (_, same) ->
            val own = same.map { it.second }.distinctBy { it.id }
            SeriesInfo(same.first().first, own, own.count { it.status == ReadStatus.Finished })
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

fun nextInSeries(books: List<Book>, book: Book): Pair<Book, BookSeries>? {
    book.series.forEach { s ->
        val at = s.number ?: return@forEach
        val next = books
            .filter { it.id != book.id && it.status != ReadStatus.Finished }
            .mapNotNull { b -> b.series.firstOrNull { it.name.equals(s.name, true) }?.number?.takeIf { it > at }?.let { b to it } }
            .minByOrNull { it.second }
        if (next != null) return next.first to BookSeries(s.name, next.second)
    }
    return null
}

@Composable
private fun CloudChip(tag: TagCount, weight: Float, selected: Boolean, onClick: () -> Unit) {
    val shape = appCorner(12)
    val bg by animateColorAsState(if (selected) AppColors.Accent else AppColors.Field, Motion.fade(200), label = "cloud-bg")
    val fg by animateColorAsState(if (selected) AppColors.OnAccent else AppColors.Text, Motion.fade(200), label = "cloud-fg")
    val edge by animateColorAsState(if (selected) AppColors.Accent else AppColors.PanelBorder, Motion.fade(200), label = "cloud-edge")
    Row(
        Modifier
            .heightIn(min = 34.dp)
            .liveClick(shape, sound = if (selected) FeedbackSound.Uncheck else FeedbackSound.Check, pressedScale = 0.92f, onClick = onClick)
            .clip(shape)
            .background(bg)
            .border(1.dp, edge, shape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "#" + tag.name,
            color = fg,
            fontSize = (12.5f + 4f * weight).sp,
            fontWeight = if (weight > 0.6f || selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text("${tag.count}", color = fg.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun RowAction(icon: ImageVector, description: String, sound: FeedbackSound = FeedbackSound.Toggle, active: Boolean = false, onClick: () -> Unit) {
    val shape = appCorner(12)
    Box(
        Modifier
            .size(38.dp)
            .liveClick(shape, sound = sound, pressedScale = 0.88f, onClick = onClick)
            .then(if (active) Modifier.clip(shape).background(AppColors.AccentSoft) else Modifier)
            .border(1.dp, if (active) AppColors.Accent.copy(alpha = 0.4f) else AppColors.PanelBorder, shape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (active) AppColors.Accent else AppColors.Muted, modifier = Modifier.size(17.dp)) }
}

@Composable
private fun EmptyNote(icon: ImageVector, title: String, hint: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = AppColors.Faint, modifier = Modifier.size(30.dp))
        Text(title, color = AppColors.Muted, fontSize = 15.sp)
        Text(hint, color = AppColors.Faint, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagCloudSheet(active: List<String>, onApply: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val counts = remember(books) { tagCounts(books) }
    val picked = remember { mutableStateListOf<String>().apply { addAll(active) } }
    var manage by remember { mutableStateOf(false) }
    LaunchedEffect(counts) { picked.removeAll { p -> counts.none { it.name.equals(p, true) } } }
    val chosen = picked.toList()
    val matched = remember(books, chosen) { books.count { b -> chosen.all { b.hasTag(it) } } }
    val top = counts.maxOfOrNull { it.count } ?: 1
    AppSheet(onDismiss, title = "Теги") {
        val close = sheetClose(onDismiss)
        if (counts.isEmpty()) {
            EmptyNote(AppIcons.Hash, "Тегов пока нет", "Добавь их в карточке книги")
        } else {
            TabbedFrame("Облако", icon = AppIcons.Hash, trailing = { CountBadge(counts.size) }) {
                FlowRow(
                    Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    counts.forEach { t ->
                        val selected = chosen.any { it.equals(t.name, true) }
                        CloudChip(t, if (top <= 1) 0f else (t.count - 1f) / (top - 1f), selected) {
                            if (selected) picked.removeAll { it.equals(t.name, true) } else picked.add(t.name)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active.isNotEmpty()) {
                    AppButton("Сбросить", primary = false, modifier = Modifier.weight(1f), labeled = true) {
                        onApply(emptyList())
                        close()
                    }
                }
                AppButton(
                    if (chosen.isEmpty()) "Отметь теги" else "Показать · $matched",
                    primary = chosen.isNotEmpty(),
                    modifier = Modifier.weight(1.4f),
                    labeled = true,
                ) {
                    if (chosen.isNotEmpty()) {
                        onApply(chosen)
                        close()
                    }
                }
            }
            CardButton("Управление тегами", AppIcons.Pencil, Modifier.fillMaxWidth()) { manage = true }
        }
    }
    if (manage) TagManageSheet { manage = false }
}

@Composable
fun TagManageSheet(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val counts = remember(books) { tagCounts(books) }
    var rename by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf<String?>(null) }
    AppSheet(onDismiss, title = "Управление тегами") {
        if (counts.isEmpty()) EmptyNote(AppIcons.Hash, "Тегов пока нет", "Добавь их в карточке книги")
        counts.forEach { t ->
            val pinned = prefs.tagShelves.any { it.equals(t.name, true) }
            Row(
                Modifier.fillMaxWidth().sheetReveal().fieldSurface(appCorner(14)).padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("#" + t.name, color = AppColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                CountBadge(t.count, AppColors.Muted, AppColors.Line)
                RowAction(AppIcons.Shelf, "Полка в шкафу", if (pinned) FeedbackSound.Uncheck else FeedbackSound.Check, active = pinned) {
                    app.libraryPrefs.update { p ->
                        p.copy(tagShelves = if (pinned) p.tagShelves.filterNot { it.equals(t.name, true) } else p.tagShelves + t.name)
                    }
                }
                RowAction(AppIcons.Pencil, "Переименовать", FeedbackSound.Open) { rename = t.name }
                RowAction(AppIcons.Trash, "Удалить тег", FeedbackSound.Open) { delete = t.name }
            }
        }
        if (counts.isNotEmpty()) HintNote("Полка ставит тег в шкаф. Одинаковое имя объединяет теги", AppIcons.Shelf)
    }
    rename?.let { old ->
        TextInputSheet("Переименовать тег", old, "Название тега", onDismiss = { rename = null }, icon = AppIcons.Tag, maxLength = 40) { text ->
            val clean = text.trim().trimStart('#').replace('|', ' ').trim()
            if (clean.isNotEmpty() && clean != old) {
                app.appScope.launch { app.library.renameTag(old, clean) }
                app.libraryPrefs.update { p ->
                    p.copy(
                        tagShelves = p.tagShelves.map { if (it.equals(old, true)) clean else it }.distinctBy { it.lowercase() },
                        filter = if (p.filter.startsWith(TagFilterPrefix)) "all" else p.filter,
                    )
                }
            }
        }
    }
    delete?.let { name ->
        ConfirmSheet(
            title = "Удалить тег?",
            text = "#$name пропадёт у всех книг. Сами книги останутся.",
            confirm = "Удалить",
            onConfirm = {
                app.appScope.launch { app.library.renameTag(name, null) }
                app.libraryPrefs.update { p ->
                    p.copy(
                        tagShelves = p.tagShelves.filterNot { it.equals(name, true) },
                        filter = if (p.filter.startsWith(TagFilterPrefix)) "all" else p.filter,
                    )
                }
            },
            onDismiss = { delete = null },
        )
    }
}

@Composable
fun SeriesListSheet(onOpenBook: ((Book) -> Unit)?, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val list = remember(books) { seriesList(books) }
    var open by remember { mutableStateOf<String?>(null) }
    AppSheet(onDismiss, title = "Серии") {
        if (list.isEmpty()) EmptyNote(AppIcons.Series, "Серий пока нет", "Укажи серию в карточке книги")
        list.forEach { s ->
            val shape = appCorner(16)
            Row(
                Modifier
                    .fillMaxWidth()
                    .sheetReveal()
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { open = s.name }
                    .fieldSurface(shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverStack(s.books.sortedBy { b -> b.series.firstOrNull { it.name.equals(s.name, true) }?.number ?: Double.MAX_VALUE }.map { it.id }, 24.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.name, color = AppColors.Text, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(appCorner(3)).background(AppColors.Line)) {
                        Box(Modifier.fillMaxWidth(s.done.toFloat() / s.books.size.coerceAtLeast(1)).height(5.dp).background(if (s.complete) AppColors.Good else AppColors.Accent))
                    }
                }
                Text("${s.done}/${s.books.size}", color = if (s.complete) AppColors.Good else AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
    open?.let { name -> SeriesSheet(name, null, onOpenBook) { open = null } }
}

@Composable
fun NextInSeriesCard(book: Book, series: BookSeries, coverFile: File?, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    val shape = appCorner(16)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick) else Modifier)
            .fieldSurface(shape, selected = true)
            .padding(start = 8.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SmallCover(coverFile, Modifier.size(width = 28.dp, height = 42.dp).clip(appCorner(5)), title = book.title, author = book.author)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(AppIcons.Series, null, tint = AppColors.Accent, modifier = Modifier.size(13.dp))
                Text("Дальше в серии", color = AppColors.Accent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Text(book.title, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        series.numberLabel?.let { n ->
            Box(Modifier.size(32.dp).clip(appCorner(10)).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                Text(n, color = AppColors.Accent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun ThemesCard(books: List<Book>, yearStart: Long) {
    val series = remember(books) { seriesList(books) }
    val tags = remember(books, yearStart) {
        tagCounts(books.filter { (it.lastReadAt ?: 0L) >= yearStart || (it.finishedAt ?: 0L) >= yearStart }).take(5)
    }
    if (series.isEmpty() && tags.isEmpty()) return
    TabbedFrame("Серии и темы", icon = AppIcons.Series, inset = false) {
        if (series.isNotEmpty()) {
            StatGrid(
                listOf(
                    StatCell("Серий", "${series.size}", icon = AppIcons.Series),
                    StatCell("Завершено", "${series.count { it.complete }}", icon = AppIcons.Check),
                ),
            )
        }
        if (tags.isNotEmpty()) {
            if (series.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.PanelBorder))
            val top = tags.first().count.coerceAtLeast(1)
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Темы года", color = AppColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
                tags.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("#" + t.name, color = AppColors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(Modifier.weight(1f).height(6.dp).clip(appCorner(3)).background(AppColors.Line)) {
                            Box(Modifier.fillMaxWidth(t.count.toFloat() / top).height(6.dp).background(AppColors.Accent))
                        }
                        Text("${t.count}", color = AppColors.Accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
