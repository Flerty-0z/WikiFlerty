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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
            modifier = Modifier.widthIn(max = 240.dp),
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
        val back = sheetClose(onDismiss)
        val close = LocalSheetCloseAll.current ?: back
        if (counts.isEmpty()) {
            EmptyNote(AppIcons.Hash, "Тегов пока нет", "Добавь их в карточке книги")
        } else {
            TabbedFrame("Облако", icon = AppIcons.Hash, trailing = { CountBadge(counts.size) }) {
                ChipsFold(Modifier.padding(vertical = 10.dp), spacing = 7.dp) {
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
        if (manage) TagManageSheet { manage = false }
    }
}

private fun booksWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "книга"
        m10 in 2..4 && m100 !in 12..14 -> "книги"
        else -> "книг"
    }
}

@Composable
private fun TagRow(tag: TagCount, pinned: Boolean, onClick: () -> Unit) {
    val shape = appCorner(14)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .sheetReveal()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(appCorner(10)).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(if (pinned) AppIcons.Shelf else AppIcons.Hash, null, tint = AppColors.Accent, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("#" + tag.name, color = AppColors.Text, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${tag.count} ${booksWord(tag.count)}" + if (pinned) " · полка в шкафу" else "",
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(AppIcons.Chevron, null, tint = AppColors.Faint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TagActionsSheet(name: String, count: Int, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val pinned = prefs.tagShelves.any { it.equals(name, true) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    AppSheet(onDismiss, title = "Тег") {
        val close = sheetClose(onDismiss)
        Row(
            Modifier.fillMaxWidth().sheetReveal().fieldSurface(appCorner(16)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).clip(appCorner(12)).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Hash, null, tint = AppColors.Accent, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("#$name", color = AppColors.Text, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$count ${booksWord(count)}", color = AppColors.Muted, fontSize = 12.5.sp, maxLines = 1)
            }
        }
        SettingsGroup(null) {
            SettingSwitch("Полка в шкафу", pinned, hint = null, icon = AppIcons.Shelf) { v ->
                app.libraryPrefs.update { p ->
                    p.copy(tagShelves = if (v) (p.tagShelves + name).distinctBy { it.lowercase() } else p.tagShelves.filterNot { it.equals(name, true) })
                }
            }
        }
        PaperField(name, "Название тега", AppIcons.Tag) { rename = true }
        DeleteRow("Удалить тег") { delete = true }
        if (rename) {
            TextInputSheet("Название тега", name, "Название тега", onDismiss = { rename = false }, icon = AppIcons.Tag, maxLength = 40) { text ->
                val clean = text.trim().trimStart('#').replace('|', ' ').trim()
                if (clean.isNotEmpty() && clean != name) {
                    app.appScope.launch { app.library.renameTag(name, clean) }
                    app.libraryPrefs.update { p ->
                        p.copy(
                            tagShelves = p.tagShelves.map { if (it.equals(name, true)) clean else it }.distinctBy { it.lowercase() },
                            filter = if (p.filter.startsWith(TagFilterPrefix)) "all" else p.filter,
                        )
                    }
                    onDismiss()
                }
            }
        }
        if (delete) {
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
                    onDismiss()
                },
                onDismiss = { delete = false },
            )
        }
    }
}

@Composable
fun TagManageSheet(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val counts = remember(books) { tagCounts(books) }
    var open by remember { mutableStateOf<TagCount?>(null) }
    AppSheet(onDismiss, title = "Управление тегами") {
        if (counts.isEmpty()) EmptyNote(AppIcons.Hash, "Тегов пока нет", "Добавь их в карточке книги")
        counts.forEach { t -> TagRow(t, prefs.tagShelves.any { it.equals(t.name, true) }) { open = t } }
        open?.let { t -> TagActionsSheet(t.name, t.count) { open = null } }
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
        list.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().sheetReveal(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { s -> SeriesTile(s, Modifier.weight(1f)) { open = s.name } }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        open?.let { name -> SeriesSheet(name, null, onOpenBook) { open = null } }
    }
}

@Composable
private fun SeriesTile(s: SeriesInfo, modifier: Modifier, onClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val shape = appCorner(18)
    val ordered = remember(s) { s.books.sortedBy { b -> b.series.firstOrNull { it.name.equals(s.name, true) }?.number ?: Double.MAX_VALUE } }
    val front = ordered.firstOrNull { it.status != com.flerty.wikiflerty.data.ReadStatus.Finished } ?: ordered.firstOrNull()
    val stack = remember(ordered, front) { (listOfNotNull(front) + ordered.filter { it.id != front?.id }).take(3) }
    val aura by rememberCoverAura(remember(front) { front?.let { app.library.coverFile(it) } }, !EInk.on)
    val tint by androidx.compose.animation.animateColorAsState(aura?.firstOrNull() ?: AppColors.Accent, Motion.tween(420), label = "series-tint")
    val total = s.books.size.coerceAtLeast(1)
    val doneColor = if (s.complete) AppColors.Good else AppColors.Accent
    Column(
        modifier
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onClick = onClick)
            .fieldSurface(shape)
            .clip(shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 56.dp + 20.dp, height = 84.dp + 8.dp).padding(top = 4.dp)) {
                stack.asReversed().forEachIndexed { r, b ->
                    val i = stack.size - 1 - r
                    BookCover(
                        b.id,
                        56.dp,
                        Modifier
                            .offset(x = 10.dp * i, y = (8 - 4 * i).dp)
                            .graphicsLayer { alpha = 1f - i * 0.28f },
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(appCorner(9))
                    .background(AppColors.Background.copy(alpha = 0.82f))
                    .border(1.dp, AppColors.PanelBorder, appCorner(9))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text("${s.done}/${s.books.size}", color = if (s.complete) AppColors.Good else AppColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
        Text(
            s.name,
            color = AppColors.Text,
            fontSize = 13.5.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp).heightIn(min = 34.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp).height(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (total <= 12) {
                repeat(total) { k ->
                    val on by androidx.compose.animation.animateColorAsState(if (k < s.done) doneColor else AppColors.Line, Motion.tween(320), label = "series-seg")
                    Box(Modifier.weight(1f).fillMaxHeight().clip(appCorner(2)).background(on))
                }
            } else {
                Box(Modifier.weight(1f).fillMaxHeight().clip(appCorner(2)).background(AppColors.Line)) {
                    Box(Modifier.fillMaxWidth(s.done.toFloat() / total).fillMaxHeight().background(doneColor))
                }
            }
        }
    }
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
        tagCounts(books.filter { (it.lastReadAt ?: 0L) >= yearStart || (it.finishedAt ?: 0L) >= yearStart }).take(10)
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
            ThemesPodium(tags)
        }
    }
}

@Composable
private fun PodiumStep(tag: TagCount, place: Int, height: androidx.compose.ui.unit.Dp, grow: Float, modifier: Modifier) {
    val first = place == 1
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "#" + tag.name,
            color = AppColors.Text,
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            fontWeight = if (first) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = grow.coerceIn(0f, 1f) },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(height * (0.25f + 0.75f * grow))
                .clip(shape)
                .background(
                    if (first) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(AppColors.Accent, androidx.compose.ui.graphics.lerp(AppColors.Accent, Color.Black, 0.22f)))
                    else androidx.compose.ui.graphics.Brush.verticalGradient(listOf(AppColors.Accent.copy(alpha = 0.2f), AppColors.Accent.copy(alpha = 0.1f))),
                )
                .border(1.dp, AppColors.Accent.copy(alpha = if (first) 0.6f else 0.32f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = grow.coerceIn(0f, 1f) }) {
                Text("$place", color = if (first) AppColors.OnAccent else AppColors.Accent, fontSize = if (first) 24.sp else 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("${tag.count} ${themeBooks(tag.count)}", color = if (first) AppColors.OnAccent.copy(alpha = 0.8f) else AppColors.Muted, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun themeBooks(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "книга"
        m10 in 2..4 && m100 !in 12..14 -> "книги"
        else -> "книг"
    }
}

@Composable
private fun ThemesPodium(tags: List<TagCount>) {
    val grow = remember { androidx.compose.animation.core.Animatable(if (Motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 220f)) }
    Column(Modifier.padding(top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(AppIcons.Hash, null, tint = AppColors.Accent, modifier = Modifier.size(13.dp))
            Text("Темы года", color = AppColors.Muted, fontSize = 12.5.sp, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val g = grow.value
            if (tags.size >= 2) PodiumStep(tags[1], 2, 70.dp, g, Modifier.weight(1f)) else Box(Modifier.weight(1f))
            PodiumStep(tags[0], 1, 96.dp, g, Modifier.weight(1f))
            if (tags.size >= 3) PodiumStep(tags[2], 3, 54.dp, g, Modifier.weight(1f)) else Box(Modifier.weight(1f))
        }
        if (tags.size > 3) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.drop(3).forEach { t ->
                    Row(
                        Modifier
                            .height(32.dp)
                            .clip(appCorner(10))
                            .background(AppColors.Line.copy(alpha = 0.3f))
                            .border(1.dp, AppColors.PanelBorder, appCorner(10))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("#" + t.name, color = AppColors.Text, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                        Text("${t.count}", color = AppColors.Accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
