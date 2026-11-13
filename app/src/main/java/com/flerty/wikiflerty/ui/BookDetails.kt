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
            Text(partLabel(draft.number)?.let { "Часть $it" } ?: "Без номера", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(AppIcons.Pencil, null, tint = AppColors.Faint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = appCorner(12)
    Box(
        Modifier
            .size(40.dp)
            .liveClick(shape, sound = FeedbackSound.Tick, pressedScale = 0.86f, enabled = enabled, onClick = onClick)
            .border(1.dp, if (enabled) AppColors.Accent.copy(alpha = 0.4f) else AppColors.PanelBorder, shape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (enabled) AppColors.Accent else AppColors.Faint, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun SeriesEditSheet(draft: SeriesDraft, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var naming by remember { mutableStateOf(false) }
    AppSheet(onDismiss, title = "Серия") {
        val close = sheetClose(onDismiss)
        PaperField(draft.name, "Название серии", AppIcons.Series) { naming = true }
        PartStepper(draft.number.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }) { v ->
            draft.number = v?.let { BookSeries("", it).numberLabel.orEmpty() }.orEmpty()
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
            val list = series.filter { it.name.isNotBlank() }.map { BookSeries(it.name.trim(), it.number.replace(',', '.').toDoubleOrNull()?.takeIf { n -> n > 0 }) }
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
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current.tags.isEmpty()) {
                    Text("Пока нет тегов", color = AppColors.Faint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
                current.tags.forEach { tag ->
                    val spoiler = tagSpoiler(tag)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tagTitle(tag), color = AppColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(38.dp).liveClick(appCorner(12), sound = if (spoiler) FeedbackSound.Uncheck else FeedbackSound.Check, pressedScale = 0.88f) {
                                save(current.tags.map { if (it == tag) (if (spoiler) it.removePrefix(SpoilerMark) else SpoilerMark + it) else it })
                            }.then(if (spoiler) Modifier.clip(appCorner(12)).background(AppColors.AccentSoft) else Modifier).border(1.dp, if (spoiler) AppColors.Accent.copy(alpha = 0.4f) else AppColors.PanelBorder, appCorner(12)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (spoiler) AppIcons.EyeOff else AppIcons.Eye, if (spoiler) "Спойлер" else "Сделать спойлером", tint = if (spoiler) AppColors.Accent else AppColors.Muted, modifier = Modifier.size(17.dp)) }
                        Box(
                            Modifier.size(38.dp).liveClick(appCorner(12), sound = FeedbackSound.Trash, pressedScale = 0.88f) { save(current.tags - tag) }.border(1.dp, AppColors.PanelBorder, appCorner(12)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Trash, "Убрать тег", tint = AppColors.Muted, modifier = Modifier.size(17.dp)) }
                    }
                }
                CardButton("Новый тег", AppIcons.Plus, Modifier.fillMaxWidth()) { adding = true }
            }
        }
        if (popular.isNotEmpty()) {
            TabbedFrame("Мои теги", icon = AppIcons.Hash, inset = false) {
                ChipsFold(Modifier.padding(vertical = 12.dp), rowPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
                    popular.forEach { t -> InfoChip("#$t", AppIcons.Plus) { save(current.tags + t) } }
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
fun SeriesSheet(name: String, currentId: String?, onOpenBook: ((Book) -> Unit)?, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val items = remember(books, name) {
        books.mapNotNull { b -> b.series.firstOrNull { it.name.equals(name, true) }?.let { b to it.number } }
            .sortedWith(compareBy<Pair<Book, Double?>> { it.second ?: Double.MAX_VALUE }.thenBy { it.first.title })
    }
    val missing = remember(items) {
        val numbers = items.mapNotNull { it.second }.filter { it == Math.floor(it) && it in 1.0..60.0 }.map { it.toInt() }.toSet()
        if (numbers.size < 2) emptyList() else (1..numbers.max()).filter { it !in numbers }
    }
    val done = items.count { it.first.status == ReadStatus.Finished }
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val pinned = prefs.seriesShelves.any { it.equals(name, true) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Book, Double?>?>(null) }
    AppSheet(onDismiss, title = "Серия") {
        val close = sheetClose(onDismiss)
        Column(Modifier.fillMaxWidth().sheetReveal().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, color = AppColors.Text, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("прочитано $done из ${items.size}", color = AppColors.Muted, fontSize = 12.5.sp, maxLines = 1)
                val part by androidx.compose.animation.core.animateFloatAsState(if (items.isEmpty()) 0f else done.toFloat() / items.size, Motion.tween(600), label = "series-part")
                Box(Modifier.weight(1f).height(4.dp).clip(appCorner(2)).background(AppColors.Line)) {
                    Box(Modifier.fillMaxWidth(part).height(4.dp).background(if (done == items.size && items.isNotEmpty()) AppColors.Good else AppColors.Accent))
                }
            }
        }
        SettingsGroup(null) {
            SettingSwitch("Полка в шкафу", pinned, hint = null, icon = AppIcons.Shelf) { v ->
                app.libraryPrefs.update { p ->
                    p.copy(seriesShelves = if (v) (p.seriesShelves + name).distinctBy { it.lowercase() } else p.seriesShelves.filterNot { it.equals(name, true) })
                }
            }
        }
        val line = AppColors.Line
        Column(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, (b, number) ->
                val shape = appCorner(16)
                val current = b.id == currentId
                val finished = b.status == ReadStatus.Finished
                val reading = b.status == ReadStatus.Reading
                val first = i == 0
                val last = false
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).sheetReveal()) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .fillMaxHeight()
                            .drawBehind {
                                val x = size.width / 2f
                                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                                val top = if (first) size.height / 2f else 0f
                                val bottom = if (last) size.height / 2f else size.height
                                drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx(), pathEffect = dash)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val fill = when {
                            finished -> AppColors.Good
                            reading || current -> AppColors.Accent
                            else -> AppColors.Panel
                        }
                        val edge = when {
                            finished -> AppColors.Good
                            reading || current -> AppColors.Accent
                            else -> AppColors.PanelBorder
                        }
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(fill).border(2.dp, edge, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (finished) {
                                Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(15.dp))
                            } else {
                                Text(
                                    number?.let { BookSeries(name, it).numberLabel }.orEmpty().ifEmpty { "${i + 1}" },
                                    color = if (reading || current) AppColors.OnAccent else AppColors.Muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .padding(start = 6.dp, top = 5.dp, bottom = 5.dp)
                            .liveClick(
                                shape,
                                sound = FeedbackSound.Open,
                                pressedScale = 0.98f,
                                onLongClick = { editing = b to number },
                            ) {
                                if (onOpenBook != null && !current) {
                                    close()
                                    onOpenBook(b)
                                } else {
                                    editing = b to number
                                }
                            }
                            .fieldSurface(shape, selected = current)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SmallCover(app.library.coverFile(b), Modifier.size(width = 38.dp, height = 57.dp), title = b.title, author = b.author)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(b.title, color = AppColors.Text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(b.status, small = true, framed = true)
                                if (reading && b.progress > 0) {
                                    Text("${(b.progress * 100).toInt()}%", color = AppColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            val accent = AppColors.Accent
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).sheetReveal()) {
                Box(
                    Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .drawBehind {
                            if (items.isNotEmpty()) {
                                val x = size.width / 2f
                                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                                drawLine(line, Offset(x, 0f), Offset(x, size.height / 2f), strokeWidth = 2.dp.toPx(), pathEffect = dash)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(AppColors.Panel)
                            .drawBehind {
                                drawCircle(
                                    accent.copy(alpha = 0.7f),
                                    radius = size.minDimension / 2f - 1.dp.toPx(),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                                    ),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Plus, null, tint = AppColors.Accent, modifier = Modifier.size(15.dp)) }
                }
                val shape = appCorner(16)
                Row(
                    Modifier
                        .weight(1f)
                        .padding(start = 6.dp, top = 5.dp, bottom = 5.dp)
                        .height(56.dp)
                        .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { adding = true }
                        .drawBehind {
                            val r = 16.dp.toPx() * AppStyle.corners
                            drawRoundRect(
                                accent.copy(alpha = 0.55f),
                                cornerRadius = CornerRadius(r, r),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                                ),
                            )
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(AppIcons.Plus, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                    Text("Добавить книгу", color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        if (missing.isNotEmpty()) {
            HintNote("В библиотеке нет частей: " + missing.joinToString(", "), AppIcons.Alert)
        }
        editing?.let { (b, n) -> SeriesBookSheet(b, name, n) { editing = null } }
        if (adding) {
            val next = (items.mapNotNull { it.second }.maxOrNull() ?: items.size.toDouble()).let { kotlin.math.floor(it) + 1 }
            SeriesAddSheet(name, next, items.map { it.first.id }.toSet()) { adding = false }
        }
    }
}

@Composable
private fun PartStepper(value: Double?, onChange: (Double?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).fieldSurface(appCorner(16)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.Sort, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
        Text("Часть", color = AppColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        StepButton(AppIcons.Minus, "Меньше", value != null) {
            val next = (value ?: 0.0) - 1
            onChange(if (next < 1) null else next)
        }
        androidx.compose.animation.AnimatedContent(
            value,
            transitionSpec = { androidx.compose.animation.fadeIn(Motion.fade(200)) togetherWith androidx.compose.animation.fadeOut(Motion.fade(120)) },
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(78.dp),
            label = "part",
        ) { v ->
            if (v == null) {
                Text("без номера", color = AppColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                Text(BookSeries("", v).numberLabel.orEmpty(), color = AppColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        StepButton(AppIcons.Plus, "Больше", (value ?: 0.0) < 999) {
            onChange((value ?: 0.0) + 1)
        }
    }
}

@Composable
private fun SeriesBookSheet(book: Book, name: String, number: Double?, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    var value by remember { mutableStateOf(number) }
    AppSheet(onDismiss, title = "Том серии") {
        val close = sheetClose(onDismiss)
        Row(
            Modifier.fillMaxWidth().sheetReveal().fieldSurface(appCorner(16)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallCover(app.library.coverFile(book), Modifier.size(width = 38.dp, height = 57.dp), title = book.title, author = book.author)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.title, color = AppColors.Text, fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(name, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PartStepper(value) { value = it }
        AppButton("Готово", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true) {
            if (value != number) app.appScope.launch { app.library.addToSeries(book.id, name, value) }
            close()
        }
        DeleteRow("Убрать из серии") {
            app.appScope.launch { app.library.removeFromSeries(book.id, name) }
            close()
        }
    }
}

@Composable
private fun SeriesAddSheet(name: String, next: Double, inSeries: Set<String>, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val choices = remember(books, inSeries) { books.filter { it.id !in inSeries && !it.paper }.sortedBy { it.title.lowercase() } }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    AppSheet(onDismiss, title = "Добавить в серию") {
        val close = sheetClose(onDismiss)
        if (choices.isEmpty()) {
            EmptyNote(AppIcons.Book, "Все книги уже в серии", "")
        }
        choices.forEach { b ->
            val on = b.id in picked
            val shape = appCorner(16)
            val edge by androidx.compose.animation.animateColorAsState(if (on) AppColors.Accent.copy(alpha = 0.6f) else Color.Transparent, Motion.fade(200), label = "pick-edge")
            Row(
                Modifier
                    .fillMaxWidth()
                    .sheetReveal()
                    .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.98f) { if (on) picked.remove(b.id) else picked.add(b.id) }
                    .fieldSurface(shape, selected = on)
                    .border(1.dp, edge, shape)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallCover(app.library.coverFile(b), Modifier.size(width = 32.dp, height = 48.dp), title = b.title, author = b.author)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(b.title, color = AppColors.Text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (b.author.isNotBlank()) Text(b.author, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val index = picked.indexOf(b.id)
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (on) AppColors.Accent else Color.Transparent)
                        .border(1.5.dp, if (on) AppColors.Accent else AppColors.Faint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Text(BookSeries(name, next + index).numberLabel.orEmpty(), color = AppColors.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
