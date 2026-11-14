package com.flerty.wikiflerty.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.BookFacts
import com.flerty.wikiflerty.data.BookInfo
import com.flerty.wikiflerty.data.BookLookup
import com.flerty.wikiflerty.data.BookSeries
import com.flerty.wikiflerty.data.LookupResult
import com.flerty.wikiflerty.data.ReadStatus
import kotlinx.coroutines.launch

const val SpoilerMark = "!"

fun tagTitle(tag: String): String = "#" + tag.removePrefix(SpoilerMark)

fun tagSpoiler(tag: String): Boolean = tag.startsWith(SpoilerMark)

@Composable
private fun InfoChip(
    text: String,
    icon: ImageVector? = null,
    accent: Boolean = false,
    dashed: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = appCorner(12)
    val line = AppColors.Accent.copy(alpha = 0.55f)
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.94f, onClick = onClick)
            .then(
                when {
                    dashed -> Modifier.drawBehind {
                        drawRoundRect(
                            line,
                            cornerRadius = CornerRadius(12.dp.toPx() * AppStyle.corners),
                            style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                    accent -> Modifier.clip(shape).background(AppColors.AccentSoft).border(1.dp, AppColors.Accent.copy(alpha = 0.3f), shape)
                    else -> Modifier.clip(shape).background(AppColors.Field).border(1.dp, AppColors.PanelBorder, shape)
                },
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (accent || dashed) AppColors.Accent else AppColors.Muted, modifier = Modifier.size(14.dp))
        Text(
            text,
            color = if (accent || dashed) AppColors.Accent else AppColors.Text,
            fontSize = 12.5.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = AppColors.Faint, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, modifier = Modifier.width(108.dp))
        Text(value, color = AppColors.Text, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun CardButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val shape = appCorner(12)
    Row(
        modifier
            .height(40.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.96f, onClick = onClick)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.35f), shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(17.dp))
        FitText(text, AppColors.Accent, 13.sp, weight = FontWeight.SemiBold)
    }
}

private val FactIcons: Map<String, ImageVector>
    get() = mapOf(
        BookFacts.Publisher to AppIcons.Book,
        BookFacts.Year to AppIcons.Calendar,
        BookFacts.Language to AppIcons.Globe,
        BookFacts.Translator to AppIcons.Translate,
        BookFacts.Original to AppIcons.Quote,
        BookFacts.Isbn to AppIcons.Barcode,
    )

private class FactItem(val icon: ImageVector, val label: String, val value: String, val wide: Boolean)

@Composable
private fun HeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.88f, onClick = onClick)
            .clip(CircleShape)
            .background(AppColors.AccentSoft)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = AppColors.Accent, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun FactTile(item: FactItem, modifier: Modifier) {
    val shape = appCorner(12)
    Column(
        modifier
            .clip(shape)
            .background(AppColors.Line.copy(alpha = 0.22f))
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(item.icon, null, tint = AppColors.Accent, modifier = Modifier.size(12.dp))
            Text(item.label, color = AppColors.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(item.value, color = AppColors.Text, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = if (item.wide) 3 else 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FactGrid(items: List<FactItem>) {
    val rows = remember(items) {
        val out = mutableListOf<List<FactItem>>()
        val pending = mutableListOf<FactItem>()
        items.forEach { f ->
            if (f.wide) out += listOf(f) else {
                pending += f
                if (pending.size == 2) {
                    out += pending.toList()
                    pending.clear()
                }
            }
        }
        if (pending.isNotEmpty()) out += pending.toList()
        out
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { FactTile(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun SeriesLine(series: BookSeries, onClick: () -> Unit) {
    val shape = appCorner(12)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .clip(shape)
            .background(AppColors.AccentSoft)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.25f), shape)
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(AppIcons.Series, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
        Text(series.name, color = AppColors.Text, fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        series.numberLabel?.let { n ->
            Box(Modifier.heightIn(min = 26.dp).clip(appCorner(8)).background(AppColors.Accent).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text("№ $n", color = AppColors.OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TagChip(text: String, icon: ImageVector?, onClick: () -> Unit) {
    val shape = appCorner(10)
    Row(
        Modifier
            .height(32.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.94f, onClick = onClick)
            .clip(shape)
            .background(AppColors.Line.copy(alpha = 0.3f))
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = AppColors.Muted, modifier = Modifier.size(13.dp))
        Text(text, color = AppColors.Text, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun TagStrip(tags: List<String>, hide: Boolean, onReveal: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(32.dp).clip(appCorner(10)).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Tag, null, tint = AppColors.Accent, modifier = Modifier.size(15.dp))
        }
        if (tags.isEmpty()) {
            Text("Нет тегов", color = AppColors.Faint, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(top = 7.dp))
        } else {
            ChipsFold(Modifier.weight(1f)) {
                tags.forEach { tag ->
                    val hidden = hide && tagSpoiler(tag)
                    TagChip(if (hidden) "#спойлер" else tagTitle(tag), if (hidden) AppIcons.EyeOff else null) { if (hidden) onReveal() else onOpen() }
                }
            }
        }
        Box {
            Box(
                Modifier
                    .size(32.dp)
                    .liveClick(appCorner(10), sound = FeedbackSound.Open, pressedScale = 0.88f, onClick = onOpen)
                    .clip(appCorner(10))
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.35f), appCorner(10)),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Plus, "Теги", tint = AppColors.Accent, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
fun BookAboutCard(app: App, book: Book, onOpenBook: ((Book) -> Unit)? = null) {
    val info by produceState<BookInfo?>(null, book.id, book.scan) { value = app.library.info(book) }
    val description = book.customDescription?.takeIf { it.isNotBlank() } ?: info?.description.orEmpty()
    var expanded by remember(book.id) { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var lookup by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf<BookSeries?>(null) }
    var reveal by remember(book.id) { mutableStateOf(false) }
    val icons = FactIcons
    val facts = BookFacts.Shown.mapNotNull { k ->
        book.fact(k).takeIf { it.isNotBlank() }?.let { v ->
            FactItem(icons[k] ?: AppIcons.Info, BookFacts.title(k), v, wide = k == BookFacts.Publisher || k == BookFacts.Translator || k == BookFacts.Original)
        }
    } + listOfNotNull(book.positions.takeIf { it > 0 && !book.paper }?.let { FactItem(AppIcons.Pages, "Объём", "$it стр.", wide = false) })
    val finished = book.status == ReadStatus.Finished
    val library by app.library.books.collectAsStateWithLifecycle()
    val sequel = remember(library, book.id, finished) { if (finished) nextInSeries(library, book) else null }
    val empty = description.isBlank() && facts.isEmpty() && book.series.isEmpty()
    TabbedFrame("О книге", icon = AppIcons.Info) {
        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var long by remember(description) { mutableStateOf(false) }
            if (description.isNotBlank()) {
                    ExpandingText(
                        description,
                        expanded = expanded,
                        lines = 5,
                        color = AppColors.Text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                        onOverflow = { if (it) long = true },
                    )
            }
            Column {
                ListDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (long) {
                        Row(
                            Modifier.liveClick(appCorner(8), sound = FeedbackSound.Toggle) {
                                expanded = !expanded
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.animation.AnimatedContent(
                                expanded,
                                transitionSpec = { androidx.compose.animation.fadeIn(Motion.tween(220)) togetherWith androidx.compose.animation.fadeOut(Motion.tween(160)) },
                                label = "about-more",
                            ) { open ->
                                Text(
                                    if (open) "Свернуть" else "Читать полностью",
                                    color = AppColors.Accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) -90f else 90f, Motion.tween(320), label = "about-turn")
                            Icon(AppIcons.Chevron, null, tint = AppColors.Accent, modifier = Modifier.padding(start = 4.dp).size(14.dp).graphicsLayer { rotationZ = turn })
                        }
                    }
                    Box(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HeaderAction(AppIcons.Search, "Найти сведения") { lookup = true }
                        HeaderAction(AppIcons.Pencil, "Изменить") { editing = true }
                    }
                }
                ListDivider()
            }
            if (empty) {
                CardButton("Найти сведения", AppIcons.Search, Modifier.fillMaxWidth()) { lookup = true }
            }
            if (book.series.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { book.series.forEach { s -> SeriesLine(s) { series = s } } }
            }
            sequel?.let { (next, s) ->
                NextInSeriesCard(next, s, app.library.coverFile(next), onClick = onOpenBook?.let { go -> { go(next) } })
            }
            if (facts.isNotEmpty()) FactGrid(facts)
            TagStrip(book.tags, hide = !finished && !reveal, onReveal = { reveal = true }, onOpen = { tagging = true })
        }
    }
    if (editing) BookDetailsSheet(book) { editing = false }
    if (lookup) BookLookupSheet(book) { lookup = false }
    if (tagging) BookTagsSheet(book) { tagging = false }
    series?.let { s -> SeriesSheet(s.name, book.id, onOpenBook) { series = null } }
}

private class SeriesDraft(name: String, number: String) {
    var name by mutableStateOf(name)
    var number by mutableStateOf(number)
}

private fun partLabel(number: String): String? = number.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { BookSeries("", it).numberLabel }

@Composable
private fun SeriesEditRow(draft: SeriesDraft, onClick: () -> Unit) {
    val shape = appCorner(14)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).clip(appCorner(10)).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Series, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(draft.name.ifBlank { "Без названия" }, color = if (draft.name.isBlank()) AppColors.Faint else AppColors.Text, fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            partLabel(draft.number)?.let { Text("Том $it", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1) }
        }
        Icon(AppIcons.Pencil, null, tint = AppColors.Faint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun SeriesEditSheet(draft: SeriesDraft, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var naming by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as App
    val known = remember { app.library.books.value.flatMap { b -> b.series.map { it.name.trim() } }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() } }
    AppSheet(onDismiss, title = "Серия") {
        val close = sheetClose(onDismiss)
        PaperField(draft.name, "Название серии", AppIcons.Series) { naming = true }
        val others = known.filter { !it.equals(draft.name.trim(), ignoreCase = true) }
        if (others.isNotEmpty()) {
            TabbedFrame("Мои серии", icon = AppIcons.Series) {
                ChipsFold(Modifier.padding(vertical = 8.dp)) {
                    others.forEach { n -> TagChip(n, AppIcons.Series) { draft.name = n } }
                }
            }
        }
        AppButton("Готово", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true) { close() }
        DeleteRow("Убрать серию") {
            onRemove()
            close()
        }
        if (naming) {
            TextInputSheet("Серия", draft.name, "Название серии", onDismiss = { naming = false }, icon = AppIcons.Series, maxLength = 120) { draft.name = it.trim() }
        }
    }
}

@Composable
fun BookDetailsSheet(book: Book, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val keys = listOf(BookFacts.Publisher, BookFacts.Year, BookFacts.Language, BookFacts.Translator, BookFacts.Original, BookFacts.Isbn)
    val icons = FactIcons
    val values = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { keys.forEach { put(it, book.fact(it)) } } }
    val series = remember { androidx.compose.runtime.mutableStateListOf<SeriesDraft>().apply { book.series.forEach { add(SeriesDraft(it.name, it.numberLabel.orEmpty())) } } }
    var field by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<SeriesDraft?>(null) }
    AppSheet(onDismiss, title = "Сведения о книге") {
        val close = sheetClose(onDismiss)
        TabbedFrame("Серия", icon = AppIcons.Series) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                series.forEach { s -> SeriesEditRow(s) { open = s } }
                CardButton("Добавить серию", AppIcons.Plus, Modifier.fillMaxWidth()) {
                    val draft = SeriesDraft("", "")
                    series.add(draft)
                    open = draft
                }
            }
        }
        keys.forEach { k -> PaperField(values[k].orEmpty(), BookFacts.title(k), icons.getValue(k)) { field = k } }
        AppButton("Сохранить", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true) {
            val facts = keys.associateWith { values[it].orEmpty() }
            val all = app.library.books.value
            val list = series.filter { it.name.isNotBlank() }.map { d ->
                val n = d.name.trim()
                val own = book.series.firstOrNull { it.name.equals(n, true) }?.number
                val next = all.filter { it.id != book.id }.mapNotNull { b -> b.series.firstOrNull { it.name.equals(n, true) }?.number }.maxOrNull()?.let { kotlin.math.floor(it) + 1 }
                BookSeries(n, own ?: next)
            }
            app.appScope.launch { app.library.editDetails(book.id, facts, list) }
            close()
        }
        field?.let { k ->
            TextInputSheet(BookFacts.title(k), values[k].orEmpty(), BookFacts.title(k), onDismiss = { field = null }, icon = icons.getValue(k), number = k == BookFacts.Year, maxLength = 160) { values[k] = it.trim() }
        }
        open?.let { draft ->
            SeriesEditSheet(
                draft,
                onRemove = { series.remove(draft) },
                onDismiss = {
                    open = null
                    if (draft.name.isBlank()) series.remove(draft)
                },
            )
        }
    }
}

@Composable
private fun LookupCard(result: LookupResult, busy: Boolean, onClick: () -> Unit) {
    val shape = appCorner(16)
    val image by produceState<ImageBitmap?>(null, result.cover) { value = result.cover?.let { CoverSearch.bitmap(it, 2)?.asImageBitmap() } }
    Row(
        Modifier
            .fillMaxWidth()
            .sheetReveal()
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(width = 54.dp, height = 81.dp).clip(appCorner(8)).background(AppColors.Field).border(1.dp, AppColors.PanelBorder, appCorner(8)), contentAlignment = Alignment.Center) {
            androidx.compose.animation.Crossfade(image, Modifier.fillMaxSize(), animationSpec = Motion.fade(220), label = "lookup-cover") { bmp ->
                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(AppIcons.Book, null, tint = AppColors.Faint, modifier = Modifier.size(20.dp)) }
            }
            if (busy) KitSpinner(Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(result.title, color = AppColors.Text, fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (result.author.isNotBlank()) Text(result.author, color = AppColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            result.series.firstOrNull()?.let { Text(it.label, color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(result.brings.joinToString(" · "), color = AppColors.Faint, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.clip(appCorner(8)).background(AppColors.AccentSoft).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Text(result.source, color = AppColors.Accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun BookLookupSheet(book: Book, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<LookupResult?>(null) }
    val outcome by produceState<BookLookup.Outcome?>(null, book.title, book.author) {
        value = runCatching { BookLookup.find(book.title, book.author) }.getOrDefault(BookLookup.Outcome(emptyList(), true))
    }
    val results = outcome?.items
    AppSheet(onDismiss, title = "Найти сведения") {
        val close = sheetClose(onDismiss)
        AnimatedContent(
            targetState = results?.let { if (it.isEmpty()) 1 else 2 } ?: 0,
            transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(140)) },
            label = "book-lookup",
        ) { state ->
            when (state) {
                0 -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(34.dp)) }
                1 -> Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(AppIcons.Search, null, tint = AppColors.Faint, modifier = Modifier.size(30.dp))
                    Text(if (outcome?.offline == true) "Нет связи с каталогами" else "Ничего не нашлось", color = AppColors.Muted, fontSize = 15.sp)
                    Text(if (outcome?.offline == true) "Проверь интернет или VPN" else "Проверь название и автора", color = AppColors.Faint, fontSize = 13.sp)
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    results.orEmpty().forEach { r ->
                        LookupCard(r, busy === r) {
                            if (busy != null) return@LookupCard
                            busy = r
                            scope.launch {
                                app.library.fillDetails(book.id, r.description, r.facts, r.series)
                                if (app.library.coverFile(book)?.exists() != true && r.cover != null) {
                                    CoverSearch.download(context, r.cover)?.let { app.library.setCover(book.id, it) }
                                }
                                busy = null
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookTagsSheet(book: Book, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    val books by app.library.books.collectAsStateWithLifecycle()
    val current = books.firstOrNull { it.id == book.id } ?: book
    var adding by remember { mutableStateOf(false) }
    val have = current.tags.map { it.removePrefix(SpoilerMark).lowercase() }.toSet()
    val popular = remember(books, have) {
        books.flatMap { it.tags }.map { it.removePrefix(SpoilerMark) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            .filter { it.lowercase() !in have }.take(14)
    }
    fun save(tags: List<String>) {
        scope.launch { app.library.setTags(book.id, tags) }
    }
    AppSheet(onDismiss, title = "Теги") {
        TabbedFrame("У этой книги", icon = AppIcons.Tag) {
            Column(Modifier.padding(vertical = 8.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = current.tags.isEmpty(),
                    enter = androidx.compose.animation.expandVertically(Motion.tween(280)) + androidx.compose.animation.fadeIn(Motion.fade(220)),
                    exit = androidx.compose.animation.shrinkVertically(Motion.tween(240)) + androidx.compose.animation.fadeOut(Motion.fade(140)),
                ) {
                    Text("Пока нет тегов", color = AppColors.Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
                AnimatedItems(current.tags, key = { it.removePrefix(SpoilerMark).lowercase() }) { tag ->
                    val spoiler = tagSpoiler(tag)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tagTitle(tag), color = AppColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val tint by androidx.compose.animation.animateColorAsState(if (spoiler) AppColors.Accent else AppColors.Muted, Motion.fade(200), label = "tag-eye")
                        val edge by androidx.compose.animation.animateColorAsState(if (spoiler) AppColors.Accent.copy(alpha = 0.4f) else AppColors.PanelBorder, Motion.fade(200), label = "tag-edge")
                        val fill by androidx.compose.animation.animateColorAsState(if (spoiler) AppColors.AccentSoft else Color.Transparent, Motion.fade(200), label = "tag-fill")
                        Box(
                            Modifier.size(38.dp).liveClick(appCorner(12), sound = if (spoiler) FeedbackSound.Uncheck else FeedbackSound.Check, pressedScale = 0.88f) {
                                save(current.tags.map { if (it == tag) (if (spoiler) it.removePrefix(SpoilerMark) else SpoilerMark + it) else it })
                            }.clip(appCorner(12)).background(fill).border(1.dp, edge, appCorner(12)),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.animation.Crossfade(spoiler, animationSpec = Motion.fade(180), label = "tag-eye-icon") { on ->
                                Icon(if (on) AppIcons.EyeOff else AppIcons.Eye, if (on) "Спойлер" else "Сделать спойлером", tint = tint, modifier = Modifier.size(17.dp))
                            }
                        }
                        Box(
                            Modifier.size(38.dp).liveClick(appCorner(12), sound = FeedbackSound.Trash, pressedScale = 0.88f) { save(current.tags - tag) }.border(1.dp, AppColors.PanelBorder, appCorner(12)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Trash, "Убрать тег", tint = AppColors.Muted, modifier = Modifier.size(17.dp)) }
                    }
                }
                CardButton("Новый тег", AppIcons.Plus, Modifier.fillMaxWidth().padding(top = 8.dp)) { adding = true }
            }
        }
        val lastPopular = remember { arrayOf(popular) }
        if (popular.isNotEmpty()) lastPopular[0] = popular
        androidx.compose.animation.AnimatedVisibility(
            visible = popular.isNotEmpty(),
            enter = androidx.compose.animation.expandVertically(Motion.tween(300)) + androidx.compose.animation.fadeIn(Motion.fade(240)),
            exit = androidx.compose.animation.shrinkVertically(Motion.tween(260)) + androidx.compose.animation.fadeOut(Motion.fade(160)),
        ) {
            TabbedFrame("Мои теги", icon = AppIcons.Hash, inset = false) {
                ChipsFold(Modifier.padding(vertical = 12.dp).animateContentSize(Motion.tween(260)), rowPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
                    lastPopular[0].forEach { t -> androidx.compose.runtime.key(t) { InfoChip("#$t", AppIcons.Plus) { save(current.tags + t) } } }
                }
            }
        }
        if (adding) {
            TextInputSheet("Новый тег", "", "Например, Тёмное фэнтези", onDismiss = { adding = false }, icon = AppIcons.Tag, maxLength = 40) { text ->
                val clean = text.trim().trimStart('#').trim()
                if (clean.isNotEmpty() && clean.lowercase() !in have) save(current.tags + clean)
            }
        }
    }
}

@Composable
fun SeriesSheet(name: String, currentId: String?, onOpenBook: ((Book) -> Unit)?, onRead: ((Book) -> Unit)? = null, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val items = remember(books, name) {
        books.mapNotNull { b -> b.series.firstOrNull { it.name.equals(name, true) }?.let { b to it.number } }
            .sortedWith(compareBy<Pair<Book, Double?>> { it.second ?: Double.MAX_VALUE }.thenBy { it.first.title })
            .map { it.first }
    }
    val byId = remember(items) { items.associateBy { it.id } }
    var order by remember { mutableStateOf(items.map { it.id }) }
    var moving by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(items) {
        if (moving == null) order = items.map { it.id }
    }
    val shown = order.mapNotNull { byId[it] }
    val done = items.count { it.status == ReadStatus.Finished }
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val pinned = prefs.seriesShelves.any { it.equals(name, true) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Book?>(null) }
    val start = remember { items.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: items.indexOfFirst { it.status == ReadStatus.Reading }.takeIf { it >= 0 } ?: items.indexOfFirst { it.status != ReadStatus.Finished }.coerceAtLeast(0) }
    val pos = remember { androidx.compose.animation.core.Animatable(start.toFloat()) }
    val center = shown.getOrNull(kotlin.math.round(pos.value).toInt().coerceIn(0, (shown.size - 1).coerceAtLeast(0)))
    AppSheet(onDismiss, title = "Серия") {
        val close = sheetClose(onDismiss)
        Column(Modifier.fillMaxWidth().sheetReveal(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, color = AppColors.Text, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${items.size} ${seriesBooksWord(items.size)} · прочитано $done", color = AppColors.Muted, fontSize = 12.5.sp, maxLines = 1)
        }
        if (shown.isEmpty()) {
            EmptyNote(AppIcons.Series, "В серии пока нет книг", "")
        } else {
            SeriesCarousel(
                shown,
                pos,
                moving,
                onMoveStart = { id -> moving = id },
                onMove = { id, to ->
                    val from = order.indexOf(id)
                    if (from >= 0 && to != from) order = order.toMutableList().apply { removeAt(from); add(to.coerceIn(0, size), id) }
                },
                onMoveEnd = {
                    moving = null
                    val ids = order
                    if (ids != items.map { it.id }) app.appScope.launch { app.library.reorderSeries(name, ids) }
                },
            )
            val last = remember { arrayOfNulls<Book>(1) }
            if (center != null) last[0] = center
            last[0]?.let { b ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedContent(
                        b.id,
                        transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(140)) },
                        label = "series-title",
                    ) { id ->
                        val book = byId[id] ?: b
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(book.title, color = AppColors.Text, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(book.status, small = true, framed = true)
                                if (book.status == ReadStatus.Reading && book.progress > 0) {
                                    Text("${(book.progress * 100).toInt()}%", color = AppColors.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                    SeriesDots(shown, kotlin.math.round(pos.value).toInt())
                }
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    androidx.compose.animation.AnimatedContent(
                        moving != null,
                        transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(140)) },
                        label = "series-actions",
                    ) { move ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (move) {
                                Box(Modifier.weight(1f).height(48.dp).fieldSurface(appCorner(14)), contentAlignment = Alignment.Center) {
                                    Text("Листай, чтобы переставить", color = AppColors.Muted, fontSize = 13.sp, maxLines = 1)
                                }
                            } else {
                                val read = onRead?.takeIf { !b.paper } ?: onOpenBook?.takeIf { b.id != currentId }
                                if (read != null) {
                                    AppButton(if (onRead != null && !b.paper) "Читать" else "Открыть", primary = true, modifier = Modifier.weight(1f).height(48.dp), labeled = true) {
                                        close()
                                        read(b)
                                    }
                                } else {
                                    Box(Modifier.weight(1f))
                                }
                                SeriesIconButton(AppIcons.Plus, "Добавить книгу", false) { adding = true }
                                SeriesIconButton(AppIcons.Shelf, "Полка в шкафу", pinned) {
                                    app.libraryPrefs.update { p ->
                                        p.copy(seriesShelves = if (!pinned) (p.seriesShelves + name).distinctBy { it.lowercase() } else p.seriesShelves.filterNot { it.equals(name, true) })
                                    }
                                }
                                SeriesIconButton(AppIcons.Trash, "Убрать из серии", false) { removing = b }
                            }
                        }
                    }
                }
            }
            Text("Зажми обложку, чтобы поменять порядок", color = AppColors.Faint, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        removing?.let { b ->
            ConfirmSheet(
                title = "Убрать из серии?",
                text = "«${b.title}» останется в библиотеке.",
                confirm = "Убрать",
                onConfirm = { app.appScope.launch { app.library.removeFromSeries(b.id, name) } },
                onDismiss = { removing = null },
            )
        }
        if (adding) {
            val next = (books.mapNotNull { bk -> bk.series.firstOrNull { it.name.equals(name, true) }?.number }.maxOrNull() ?: items.size.toDouble()).let { kotlin.math.floor(it) + 1 }
            SeriesAddSheet(name, next, items.map { it.id }.toSet()) { adding = false }
        }
    }
}

private fun seriesBooksWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "книга"
        m10 in 2..4 && m100 !in 12..14 -> "книги"
        else -> "книг"
    }
}

@Composable
private fun SeriesIconButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val shape = appCorner(14)
    val edge by androidx.compose.animation.animateColorAsState(if (active) AppColors.Accent.copy(alpha = 0.6f) else AppColors.PanelBorder, Motion.fade(200), label = "series-btn-edge")
    val tint by androidx.compose.animation.animateColorAsState(if (active) AppColors.Accent else AppColors.Text, Motion.fade(200), label = "series-btn-tint")
    Box(
        Modifier
            .size(48.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.9f, onClick = onClick)
            .fieldSurface(shape, selected = active)
            .border(1.dp, edge, shape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun SeriesDots(books: List<Book>, index: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        books.forEachIndexed { i, b ->
            val on = i == index
            val width by androidx.compose.animation.core.animateDpAsState(if (on) 20.dp else 8.dp, Motion.tween(260), label = "series-dot")
            val color = when {
                b.status == ReadStatus.Finished -> AppColors.Good
                b.status == ReadStatus.Reading -> AppColors.Accent
                on -> AppColors.Muted
                else -> AppColors.Line
            }
            val c by androidx.compose.animation.animateColorAsState(if (on && b.status != ReadStatus.Finished) AppColors.Accent else color, Motion.fade(220), label = "series-dot-color")
            Box(Modifier.width(width).height(8.dp).clip(CircleShape).background(c))
        }
    }
}

@Composable
private fun SeriesCarousel(
    books: List<Book>,
    pos: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    moving: String?,
    onMoveStart: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onMoveEnd: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val feedback = LocalFeedback.current
    val coverW = 156.dp
    val coverH = 234.dp
    val step = with(density) { 150.dp.toPx() }
    val count = books.size
    val latest by androidx.compose.runtime.rememberUpdatedState(books)
    val movingNow by androidx.compose.runtime.rememberUpdatedState(moving)
    val slots = remember { HashMap<String, androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
    books.forEachIndexed { i, b ->
        val a = slots.getOrPut(b.id) { androidx.compose.animation.core.Animatable(i.toFloat()) }
        androidx.compose.runtime.LaunchedEffect(b.id, i) {
            if (a.targetValue != i.toFloat()) a.animateTo(i.toFloat(), androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 420f))
        }
    }
    androidx.compose.runtime.LaunchedEffect(count) {
        if (pos.value > count - 1) pos.snapTo((count - 1).coerceAtLeast(0).toFloat())
    }
    val lift = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(moving) {
        lift.animateTo(if (moving != null) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 420f))
    }
    var lastTick by remember { androidx.compose.runtime.mutableIntStateOf(kotlin.math.round(pos.value).toInt()) }
    val tick = kotlin.math.round(pos.value).toInt()
    if (tick != lastTick) {
        lastTick = tick
        androidx.compose.runtime.SideEffect { feedback?.play(FeedbackSound.Tick) }
    }
    fun settle(velocity: Float, from: Float) {
        val n = latest.size
        val target = (from - velocity / step * 0.18f).let { kotlin.math.round(it) }.coerceIn(0f, (n - 1).coerceAtLeast(0).toFloat())
        scope.launch { pos.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f), initialVelocity = -velocity / step) }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(coverH + 28.dp)
            .sheetReveal()
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val holdMs = viewConfiguration.longPressTimeoutMillis
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    scope.launch { pos.stop() }
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val mid = size.width / 2f
                    val onCenter = kotlin.math.abs(down.position.x - mid) < coverW.toPx() / 2f
                    var mode = 0
                    var cur = pos.value
                    var travel = Offset.Zero
                    val started = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (mode == 0) {
                                val dx = down.position.x - mid
                                if (kotlin.math.abs(dx) > coverW.toPx() / 2f) {
                                    val target = (kotlin.math.round(pos.value) + if (dx > 0) 1 else -1).coerceIn(0f, (latest.size - 1).coerceAtLeast(0).toFloat())
                                    scope.launch { pos.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)) }
                                }
                            } else {
                                val v = tracker.calculateVelocity().x
                                if (mode == 2) {
                                    val id = movingNow
                                    val index = latest.indexOfFirst { it.id == id }.coerceAtLeast(0).toFloat()
                                    val spring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.85f, stiffness = 320f)
                                    val slot = id?.let { slots[it] }
                                    scope.launch {
                                        slot?.snapTo(cur)
                                        onMoveEnd()
                                        launch { slot?.animateTo(index, spring) }
                                        pos.animateTo(index, spring)
                                    }
                                } else {
                                    settle(v, cur)
                                }
                            }
                            change.consume()
                            break
                        }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val delta = change.position - change.previousPosition
                        travel += delta
                        if (mode == 0) {
                            if (kotlin.math.abs(travel.x) > slop) {
                                mode = 1
                                cur = pos.value
                            } else if (travel.getDistance() > slop) {
                                break
                            } else if (onCenter && change.uptimeMillis - started >= holdMs) {
                                val n = latest.size
                                val idx = kotlin.math.round(pos.value).toInt().coerceIn(0, n - 1)
                                if (n > 1) {
                                    mode = 2
                                    cur = idx.toFloat()
                                    scope.launch { pos.snapTo(cur) }
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    feedback?.play(FeedbackSound.Pick)
                                    onMoveStart(latest[idx].id)
                                }
                            }
                        }
                        if (mode != 0) {
                            change.consume()
                            val n = latest.size
                            val next = (cur - delta.x / step).coerceIn(-0.35f, (n - 1) + 0.35f)
                            cur = next
                            scope.launch { pos.snapTo(next) }
                            if (mode == 2) {
                                val id = movingNow
                                if (id != null) onMove(id, kotlin.math.round(next).toInt().coerceIn(0, n - 1))
                            }
                        }
                    }
                }
            },
    ) {
        val width = constraints.maxWidth.toFloat()
        val drawOrder = books.sortedByDescending { b ->
            val at = if (b.id == moving) pos.value else slots[b.id]?.value ?: 0f
            kotlin.math.abs(at - pos.value)
        }
        drawOrder.forEach { b ->
            androidx.compose.runtime.key(b.id) {
                val isMoving = b.id == moving
                val index = books.indexOf(b)
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .size(coverW, coverH)
                        .graphicsLayer {
                            val at = if (isMoving) pos.value else slots[b.id]?.value ?: index.toFloat()
                            val d = at - pos.value
                            val k = kotlin.math.min(1f, kotlin.math.abs(d))
                            translationX = d * step
                            val s = (1f - 0.2f * k) * (1f + 0.07f * if (isMoving) lift.value else 0f)
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.5f * k
                            translationY = -10.dp.toPx() * if (isMoving) lift.value else 0f
                            rotationZ = if (isMoving) -2f * lift.value else 0f
                            shadowElevation = (8.dp.toPx() + 14.dp.toPx() * if (isMoving) lift.value else 0f) * (1f - k)
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                            clip = true
                        },
                ) {
                    SmallCover(app.library.coverFile(b), Modifier.fillMaxSize(), maxWidth = 360, title = b.title, author = b.author)
                    val label = books.indexOf(b) + 1
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(appCorner(8))
                            .background(AppColors.Accent)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text("Том $label", color = AppColors.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun SeriesAddSheet(name: String, next: Double, inSeries: Set<String>, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val choices = remember(books, inSeries) { books.filter { it.id !in inSeries && !it.paper }.sortedBy { it.title.lowercase() } }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var query by remember { mutableStateOf("") }
    val shown = remember(choices, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) choices else choices.filter { it.title.lowercase().contains(q) || it.author.lowercase().contains(q) }
    }
    AppSheet(onDismiss, title = "Добавить в серию") {
        val close = sheetClose(onDismiss)
        if (choices.isEmpty()) {
            EmptyNote(AppIcons.Book, "Все книги уже в серии", "")
        }
        if (choices.size > 5) {
            SearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Название или автор")
            if (shown.isEmpty()) EmptyNote(AppIcons.Search, "Ничего не нашлось", "")
        }
        if (shown.isNotEmpty()) {
            SettingsGroup("Книги · ${picked.size}/${choices.size}") {
                Column(Modifier.padding(vertical = 6.dp)) {
                    shown.forEachIndexed { i, b ->
                        if (i > 0) ListDivider()
                        val on = b.id in picked
                        val tint by androidx.compose.animation.animateColorAsState(if (on) AppColors.Accent else AppColors.Text, Motion.fade(200), label = "pick-tint")
                        val fill by androidx.compose.animation.animateColorAsState(if (on) AppColors.Accent else Color.Transparent, Motion.fade(200), label = "pick-fill")
                        val edge by androidx.compose.animation.animateColorAsState(if (on) AppColors.Accent else AppColors.Line, Motion.fade(200), label = "pick-edge")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.98f) { if (on) picked.remove(b.id) else picked.add(b.id) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BookCover(b.id, 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(b.title, color = tint, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val index = picked.indexOf(b.id)
                                val label = remember { arrayOf("") }
                                if (index >= 0) label[0] = "Том " + BookSeries(name, next + index).numberLabel.orEmpty()
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = on,
                                    enter = androidx.compose.animation.expandVertically(Motion.tween(220)) + androidx.compose.animation.fadeIn(Motion.fade(200)),
                                    exit = androidx.compose.animation.shrinkVertically(Motion.tween(200)) + androidx.compose.animation.fadeOut(Motion.fade(140)),
                                ) {
                                    Text(label[0], color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(appCorner(7))
                                    .background(fill)
                                    .border(1.5.dp, edge, appCorner(7)),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = on,
                                    enter = androidx.compose.animation.scaleIn(Motion.tween(180), initialScale = 0.5f) + androidx.compose.animation.fadeIn(Motion.fade(160)),
                                    exit = androidx.compose.animation.scaleOut(Motion.tween(140), targetScale = 0.5f) + androidx.compose.animation.fadeOut(Motion.fade(120)),
                                ) { Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(15.dp)) }
                            }
                        }
                    }
                }
            }
        }
        if (choices.isNotEmpty()) {
            AppButton(if (picked.isEmpty()) "Выбери книги" else "Добавить · ${picked.size}", primary = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth(), labeled = true) {
                if (picked.isEmpty()) return@AppButton
                val ids = picked.toList()
                app.appScope.launch { ids.forEachIndexed { i, id -> app.library.addToSeries(id, name, next + i) } }
                close()
            }
        }
    }
}
