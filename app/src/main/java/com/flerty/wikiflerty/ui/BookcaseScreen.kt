package com.flerty.wikiflerty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.ReadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BookWidth = 92.dp
private val SpineHeight = 22.dp

private sealed class ShelfUnit(val key: String) {
    class Standing(val book: Book, val lean: Boolean) : ShelfUnit("b:" + book.id)
    class Stack(val books: List<Book>) : ShelfUnit("s:" + books.joinToString(",") { it.id })
    class Add(val onAdd: () -> Unit) : ShelfUnit("add")
}

class PlankSpec(val key: String, val name: String?, val books: List<Book>, val onAdd: (() -> Unit)?, val onEdit: (() -> Unit)?, val wrap: Boolean)

private class PlankRow(val key: String, val name: String?, val units: List<ShelfUnit>, val onEdit: (() -> Unit)?, val scroll: Boolean)

private fun arrange(books: List<Book>, onAdd: (() -> Unit)?): List<ShelfUnit> {
    val standing = books.filter { it.status != ReadStatus.Finished }
        .sortedWith(compareBy<Book> { if (it.status == ReadStatus.Reading) 0 else 1 }.thenByDescending { it.lastReadAt ?: it.addedAt })
    val lying = books.filter { it.status == ReadStatus.Finished }.sortedByDescending { it.finishedAt ?: it.addedAt }
    val units = mutableListOf<ShelfUnit>()
    standing.forEachIndexed { i, b -> units += ShelfUnit.Standing(b, lean = standing.size >= 3 && i == standing.lastIndex) }
    lying.chunked(4).forEach { units += ShelfUnit.Stack(it) }
    if (onAdd != null) units += ShelfUnit.Add(onAdd)
    return units
}

private fun unitWidth(u: ShelfUnit): Float = when (u) {
    is ShelfUnit.Standing -> 97f + if (u.lean) 14f else 0f
    is ShelfUnit.Stack -> 160f
    is ShelfUnit.Add -> 84f
}

private fun rows(planks: List<PlankSpec>, width: Float): List<PlankRow> = planks.flatMap { p ->
    val units = arrange(p.books, p.onAdd)
    if (!p.wrap) return@flatMap listOf(PlankRow(p.key, p.name, units, p.onEdit, scroll = true))
    val out = mutableListOf<MutableList<ShelfUnit>>(mutableListOf())
    var used = 0f
    units.forEach { u ->
        val w = unitWidth(u)
        if (used + w > width && out.last().isNotEmpty()) {
            out += mutableListOf<ShelfUnit>()
            used = 0f
        }
        out.last() += u
        used += w
    }
    out.mapIndexed { i, r -> PlankRow(p.key + "#" + i, if (i == 0) p.name else null, r, p.onEdit, scroll = false) }
}

@Composable
fun BookcaseView(
    planks: List<PlankSpec>,
    onBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
    animationKey: Any? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize().bookcaseWall()) {
        val width = maxWidth.value - 44f
        val list = remember(planks, width) { rows(planks, width) }
        androidx.compose.runtime.key(animationKey) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                itemsIndexed(list, key = { _, r -> r.key }) { index, row ->
                    Shelf(row, index, onBook)
                }
                if (footer != null) item(key = "footer") { footer() }
            }
        }
    }
}

@Composable
fun NewShelfButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        val shape = appCorner(16)
        Row(
            Modifier
                .liveClick(shape, sound = FeedbackSound.Open, onClick = onClick)
                .border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(AppIcons.ShelfAdd, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            Text("Новая полка", color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun Modifier.bookcaseWall(): Modifier = this.drawBehind {
    val decor = AppStyle.kit.decor
    if (decor != Decor.None && decor != Decor.Library) return@drawBehind
    val light = AppStyle.light
    val top = if (light) Color(0xFFE6D7C3) else if (decor == Decor.Library) Color(0xFF241509) else Color(0xFF1D1611)
    val bottom = if (light) Color(0xFFD8C5AC) else if (decor == Decor.Library) Color(0xFF150B05) else Color(0xFF110D0A)
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val step = 86.dp.toPx()
    var x = step / 2
    val seam = if (light) Color(0x14000000) else Color(0x12FFE0B0)
    while (x < size.width) {
        drawLine(seam, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        x += step
    }
}

@Composable
private fun Shelf(row: PlankRow, index: Int, onBook: (Book) -> Unit) {
    Column(Modifier.fillMaxWidth().scrollReveal()) {
        val content: @Composable (Int, ShelfUnit) -> Unit = { i, unit ->
            when (unit) {
                is ShelfUnit.Standing -> StandingBook(unit.book, unit.lean, Modifier.dropIn(index, i)) { onBook(unit.book) }
                is ShelfUnit.Stack -> BookStack(unit.books, Modifier.dropIn(index, i), onBook)
                is ShelfUnit.Add -> Box(Modifier.dropIn(index, i)) { AddSlot(unit.onAdd) }
            }
        }
        if (row.scroll) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(BookWidth * 1.5f + 14.dp),
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                itemsIndexed(row.units, key = { _, u -> u.key }) { i, unit -> content(i, unit) }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(BookWidth * 1.5f + 14.dp).padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                row.units.forEachIndexed { i, unit -> androidx.compose.runtime.key(unit.key) { content(i, unit) } }
            }
        }
        Box(Modifier.plankIn(index)) { Plank(row.name, false, row.onEdit) }
    }
}

@Composable
private fun Modifier.dropIn(shelf: Int, item: Int): Modifier {
    val p = remember { androidx.compose.animation.core.Animatable(if (Motion.enabled) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        p.animateTo(1f, Motion.tween(480, 120 + shelf.coerceAtMost(4) * 90 + item.coerceAtMost(8) * 55, androidx.compose.animation.core.FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
    }
}

@Composable
private fun Modifier.plankIn(shelf: Int): Modifier {
    val p = remember { androidx.compose.animation.core.Animatable(if (Motion.enabled) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        p.animateTo(1f, Motion.tween(460, 40 + shelf.coerceAtMost(4) * 110, androidx.compose.animation.core.FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v
    }
}

@Composable
private fun StandingBook(book: Book, lean: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = appCorner(6)
    Box(
        modifier
            .padding(start = if (lean) 14.dp else 0.dp)
            .graphicsLayer {
                if (lean) {
                    transformOrigin = TransformOrigin(0f, 1f)
                    rotationZ = -7f
                }
            }
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.96f, onLongClick = onClick, onClick = onClick),
    ) {
        BookCover(book.id, BookWidth)
    }
}

@Composable
private fun BookStack(books: List<Book>, modifier: Modifier = Modifier, onBook: (Book) -> Unit) {
    Column(modifier.padding(start = 6.dp, end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val order = books.reversed()
        order.forEachIndexed { i, b ->
            val h = b.id.hashCode() and 0x7FFFFFFF
            val width = 114.dp + (h % 26).dp
            val shift = (((h shr 5) and 0x0F) - 7).dp
            val pages = i > 0 && (h shr 9) % 3 == 0
            LyingBook(b, width, top = i == 0, pagesOut = pages, modifier = Modifier.offset(x = shift)) { onBook(b) }
        }
    }
}

@Composable
private fun LyingBook(book: Book, width: Dp, top: Boolean, pagesOut: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tones = rememberCoverTones(book)
    val main = tones[tones.size / 2]
    val shape = appCorner(3)
    val board = 4.dp
    Column(modifier.width(width).liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onLongClick = onClick, onClick = onClick)) {
        if (top) {
            Box(
                Modifier.fillMaxWidth().height(board).drawBehind {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(5.dp.toPx(), 0f)
                        lineTo(size.width - 3.dp.toPx(), 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, Brush.horizontalGradient(tones.map { lighten(it, 0.28f) }))
                    drawPath(path, Color(0x22FFFFFF))
                },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(SpineHeight)
                .drawBehind {
                    if (pagesOut) {
                        val b = 3.dp.toPx()
                        drawRect(Brush.verticalGradient(listOf(Color(0xFFEDE3CC), Color(0xFFD2C3A3))), Offset(0f, b), androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height - 2 * b))
                        val line = Color(0x26000000)
                        var y = b + 2.dp.toPx()
                        while (y < size.height - b - 1f) {
                            drawLine(line, Offset(2.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), 0.7f)
                            y += 2.2.dp.toPx()
                        }
                        drawRect(main, size = androidx.compose.ui.geometry.Size(size.width, b))
                        drawRect(darken(main, 0.6f), Offset(0f, size.height - b), androidx.compose.ui.geometry.Size(size.width, b))
                        drawRect(darken(main, 0.75f), Offset(size.width - 4.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
                    } else {
                        drawRect(Brush.horizontalGradient(tones))
                        drawRect(Brush.verticalGradient(0f to Color(0x30FFFFFF), 0.32f to Color(0x14FFFFFF), 0.55f to Color(0x00000000), 1f to Color(0x66000000)))
                        val band = Color(0xA6E7B877)
                        val w = 2.dp.toPx()
                        listOf(7.dp.toPx(), 11.dp.toPx()).forEach { x ->
                            drawRect(band, Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                            drawRect(band, Offset(size.width - x - w, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                        }
                    }
                },
        )
    }
}

@Composable
private fun AddSlot(onClick: () -> Unit) {
    val shape = appCorner(8)
    val line = if (AppStyle.light) Color(0x33000000) else Color(0x33F2C38B)
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(width = BookWidth * 0.8f, height = BookWidth * 1.2f)
            .liveClick(shape, sound = FeedbackSound.Open, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    line,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            },
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.Plus, "Добавить книги", tint = line.copy(alpha = 0.6f), modifier = Modifier.size(22.dp)) }
}

@Composable
private fun Plank(name: String?, highlighted: Boolean, onEdit: (() -> Unit)?) {
    val light = AppStyle.light
    val decor = AppStyle.kit.decor
    val accent = AppColors.Accent
    val ink = AppColors.Text
    val wood = when {
        decor == Decor.Library -> if (light) listOf(Color(0xFF9A6A3C), Color(0xFF7A4E28), Color(0xFF5A3518)) else listOf(Color(0xFF7A4A24), Color(0xFF5E381A), Color(0xFF40240F))
        light -> listOf(Color(0xFFC9A375), Color(0xFFB08656), Color(0xFF8E6538))
        else -> listOf(Color(0xFFA8743F), Color(0xFF8A5A30), Color(0xFF6B4322))
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(20.dp)
                .then(if (decor == Decor.Comic) Modifier.hardShadow(shape, 4.dp) else Modifier)
                .then(if (onEdit != null) Modifier.liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.99f, onLongClick = onEdit, onClick = onEdit) else Modifier)
                .drawBehind {
                    when (decor) {
                        Decor.Zen -> {
                            val seg = 46.dp.toPx()
                            drawRoundRect(if (light) Color(0xFFA7B37A) else Color(0xFF6E7A48), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                            var x = seg
                            while (x < size.width) {
                                drawLine(if (light) Color(0xFF7D8A52) else Color(0xFF4E5A30), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                                x += seg
                            }
                            drawRect(Color.White.copy(alpha = 0.18f), Offset(0f, size.height * 0.18f), androidx.compose.ui.geometry.Size(size.width, size.height * 0.16f))
                        }
                        Decor.Comic -> {
                            drawRect(Color(0xFFFFCC00))
                            drawRect(Color(0xFF111111), style = Stroke(2.5.dp.toPx()))
                        }
                        Decor.News -> {
                            drawRect(ink, size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()))
                            drawRect(ink, Offset(0f, size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                            if (highlighted) drawRect(accent.copy(alpha = 0.12f))
                        }
                        Decor.Clay -> {
                            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                            drawRoundRect(if (highlighted) AppColors.AccentLight else accent, cornerRadius = r)
                            drawRoundRect(
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.18f))),
                                cornerRadius = r,
                            )
                        }
                        Decor.Sea -> {
                            val core = size.height * 0.55f
                            drawRoundRect(accent.copy(alpha = if (highlighted) 0.3f else 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                            drawRoundRect(
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = if (light) 0.85f else 0.35f), Color.White.copy(alpha = if (light) 0.35f else 0.08f))),
                                Offset(0f, (size.height - core) / 2),
                                androidx.compose.ui.geometry.Size(size.width, core),
                                androidx.compose.ui.geometry.CornerRadius(core / 2),
                            )
                        }
                        else -> {
                            drawRoundRect(Brush.verticalGradient(wood), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                            drawRect(Color(0x73FFDCAA), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                            if (highlighted) drawRect(Color(0x26FFD9A0))
                            var x = 0f
                            val grain = Color(0x14000000)
                            var k = 0
                            while (x < size.width) {
                                val y = size.height * (0.35f + 0.3f * ((k * 37) % 10) / 10f)
                                drawLine(grain, Offset(x, y), Offset(x + 60.dp.toPx(), y + 1.dp.toPx()), 1.dp.toPx())
                                x += 46.dp.toPx(); k++
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (name != null) {
                val plaque = decor == Decor.Library
                Text(
                    if (decor == Decor.News) name.uppercase() else name,
                    color = when {
                        plaque -> Color(0xFF3A2A12)
                        decor == Decor.Comic -> Color(0xFF111111)
                        decor == Decor.News || decor == Decor.Sea -> AppColors.Text
                        decor == Decor.Clay -> AppColors.OnAccent
                        decor == Decor.Zen -> if (light) Color(0xFF2B3A18) else Color(0xFFEDEFE0)
                        light -> Color(0xFF3A2612)
                        else -> Color(0xFFF7EBD8)
                    },
                    fontSize = 11.sp,
                    fontWeight = if (decor == Decor.Comic || decor == Decor.News || decor == Decor.Clay) FontWeight.ExtraBold else FontWeight.SemiBold,
                    letterSpacing = if (decor == Decor.News) 1.5.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .then(
                            when {
                                plaque -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFFF0D48A), Color(0xFFB8873F))), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).padding(horizontal = 10.dp)
                                else -> Modifier
                            },
                        ),
                )
            }
        }
        if (decor == Decor.None || decor == Decor.Library || decor == Decor.Zen || decor == Decor.Comic) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(12.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x80000000), Color(0x00000000)))),
            )
        }
    }
}

@Composable
private fun rememberCoverTones(book: Book): List<Color> {
    val app = LocalContext.current.applicationContext as App
    val fallback = remember(book.id) {
        val hues = listOf(0xFF3B5F7A, 0xFF8A3B2B, 0xFF5F7A4E, 0xFF7A4FA0, 0xFF2C2F5A, 0xFFB2763A, 0xFF1F6F8B, 0xFF6E2F3F)
        val c = Color(hues[(book.id.hashCode() and 0x7FFFFFFF) % hues.size])
        listOf(darken(c, 0.8f), c, lighten(c, 0.1f), c, darken(c, 0.8f))
    }
    val file = app.library.coverFile(book)
    val tones by produceState(CoverCache.peekTones(file) ?: fallback, file) {
        if (file == null || CoverCache.peekTones(file) != null) return@produceState
        val t = withContext(Dispatchers.IO) { CoverCache.load(file, 64)?.let { coverTones(it.asAndroidBitmap()) } }
        if (t != null) {
            CoverCache.putTones(file, t)
            value = t
        }
    }
    return tones
}

private fun coverTones(bitmap: android.graphics.Bitmap): List<Color>? {
    if (bitmap.width == 0 || bitmap.height == 0) return null
    val bands = 7
    return (0 until bands).map { band ->
        val y0 = bitmap.height * band / bands
        val y1 = (bitmap.height * (band + 1) / bands).coerceAtLeast(y0 + 1)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        var y = y0
        while (y < y1) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
                x += 3
            }
            y += 2
        }
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / n.coerceAtLeast(1)).toInt(), (g / n.coerceAtLeast(1)).toInt(), (b / n.coerceAtLeast(1)).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.25f).coerceIn(0.12f, 0.85f)
        hsv[2] = hsv[2].coerceIn(0.22f, 0.82f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
}

private fun darken(c: Color, factor: Float): Color = Color(red = c.red * factor, green = c.green * factor, blue = c.blue * factor, alpha = c.alpha)

private fun lighten(c: Color, amount: Float): Color = Color(
    red = c.red + (1f - c.red) * amount,
    green = c.green + (1f - c.green) * amount,
    blue = c.blue + (1f - c.blue) * amount,
    alpha = c.alpha,
)

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
