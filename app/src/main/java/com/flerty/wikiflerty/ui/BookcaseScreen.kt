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
    class Add(val shelf: String) : ShelfUnit("add")
}

private fun arrange(books: List<Book>, shelf: String): List<ShelfUnit> {
    val standing = books.filter { it.status != ReadStatus.Finished }
        .sortedWith(compareBy<Book> { if (it.status == ReadStatus.Reading) 0 else 1 }.thenByDescending { it.lastReadAt ?: it.addedAt })
    val lying = books.filter { it.status == ReadStatus.Finished }.sortedByDescending { it.finishedAt ?: it.addedAt }
    val units = mutableListOf<ShelfUnit>()
    standing.forEachIndexed { i, b -> units += ShelfUnit.Standing(b, lean = standing.size >= 3 && i == standing.lastIndex) }
    lying.chunked(4).forEach { units += ShelfUnit.Stack(it) }
    units += ShelfUnit.Add(shelf)
    return units
}

@Composable
fun BookcaseScreen(viewModel: LibraryViewModel, focus: String?, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val books by viewModel.books.collectAsStateWithLifecycle()
    val prefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val shelves = allShelves(prefs, books)
    var menuBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<ShelfDialog?>(null) }
    val start = shelves.indexOf(focus).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)

    Box(modifier.fillMaxSize().bookcaseWall()) {
        if (shelves.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Полок пока нет", color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                AppButton("Новая полка", primary = true) { dialog = ShelfDialog.Create(null) }
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 18.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                itemsIndexed(shelves, key = { _, name -> name }) { index, name ->
                    val onShelf = books.filter { name in it.shelves }
                    Shelf(
                        name = name,
                        units = arrange(onShelf, name),
                        highlighted = name == focus,
                        index = index,
                        onBook = { menuBookId = it.id },
                        onEdit = { dialog = ShelfDialog.Edit(name) },
                    )
                }
                item(key = "new-shelf") {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                        val shape = appCorner(16)
                        Row(
                            Modifier
                                .liveClick(shape, sound = FeedbackSound.Open) { dialog = ShelfDialog.Create(null) }
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
    dialog?.let { d -> ShelfDialogView(d, shelves, onDismiss = { dialog = null }) }
}

private fun Modifier.bookcaseWall(): Modifier = this.drawBehind {
    val light = AppStyle.light
    val top = if (light) Color(0xFFE6D7C3) else Color(0xFF1D1611)
    val bottom = if (light) Color(0xFFD8C5AC) else Color(0xFF110D0A)
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
private fun Shelf(name: String, units: List<ShelfUnit>, highlighted: Boolean, index: Int, onBook: (Book) -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().riseIn("shelf:$name", index)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(BookWidth * 1.5f + 14.dp),
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            itemsIndexed(units, key = { _, u -> u.key }) { i, unit ->
                when (unit) {
                    is ShelfUnit.Standing -> StandingBook(unit.book, unit.lean, Modifier.riseIn("${name}:${unit.key}", i)) { onBook(unit.book) }
                    is ShelfUnit.Stack -> BookStack(unit.books, Modifier.riseIn("${name}:${unit.key}", i), onBook)
                    is ShelfUnit.Add -> AddSlot(onEdit)
                }
            }
        }
        Plank(name, highlighted, onEdit)
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
        books.reversed().forEach { b ->
            val h = b.id.hashCode()
            val width = 112.dp + ((h and 0x1F) % 26).dp
            val shift = (((h shr 5) and 0x0F) - 7).dp
            LyingSpine(b, width, Modifier.offset(x = shift)) { onBook(b) }
        }
    }
}

@Composable
private fun LyingSpine(book: Book, width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val base = rememberCoverColor(book)
    val shape = appCorner(3)
    Box(
        modifier
            .width(width)
            .height(SpineHeight)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onLongClick = onClick, onClick = onClick)
            .drawBehind {
                val dark = Color(
                    red = base.red * 0.55f,
                    green = base.green * 0.55f,
                    blue = base.blue * 0.55f,
                )
                drawRect(Brush.verticalGradient(listOf(lighten(base, 0.18f), base, dark)))
                val band = Color(0x8CF2C38B)
                val w = 2.5.dp.toPx()
                drawRect(band, Offset(8.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(w, size.height))
                drawRect(band, Offset(size.width - 8.dp.toPx() - w, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                drawRect(Color(0x33000000), Offset(0f, size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            book.title,
            color = if (base.luminanceApprox() > 0.6f) Color(0xE6261C12) else Color(0xE6FFFFFF),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
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
private fun Plank(name: String, highlighted: Boolean, onEdit: () -> Unit) {
    val light = AppStyle.light
    val wood = if (light) listOf(Color(0xFFC9A375), Color(0xFFB08656), Color(0xFF8E6538)) else listOf(Color(0xFFA8743F), Color(0xFF8A5A30), Color(0xFF6B4322))
    val engrave = if (light) Color(0xCC4A2E12) else Color(0xD9341F0E)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(20.dp)
                .liveClick(appCorner(4), sound = FeedbackSound.Open, pressedScale = 0.99f, onLongClick = onEdit, onClick = onEdit)
                .drawBehind {
                    drawRect(Brush.verticalGradient(wood))
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
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name,
                style = TextStyle(
                    color = engrave,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    shadow = Shadow(Color(0x59FFE6BE), Offset(0f, 2f), 0f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(12.dp)
                .background(Brush.verticalGradient(listOf(Color(0x80000000), Color(0x00000000)))),
        )
    }
}

@Composable
private fun rememberCoverColor(book: Book): Color {
    val app = LocalContext.current.applicationContext as App
    val fallback = remember(book.id) {
        val hues = listOf(0xFF3B5F7A, 0xFF8A3B2B, 0xFF5F7A4E, 0xFF7A4FA0, 0xFF2C2F5A, 0xFFB2763A, 0xFF1F6F8B, 0xFF6E2F3F)
        Color(hues[(book.id.hashCode() and 0x7FFFFFFF) % hues.size])
    }
    val file = app.library.coverFile(book)
    val color by produceState(fallback, file) {
        val c = withContext(Dispatchers.IO) { file?.takeIf { it.exists() }?.let { CoverCache.load(it, 48) }?.let { averageColor(it.asAndroidBitmap()) } }
        if (c != null) value = c
    }
    return color
}

private fun averageColor(bitmap: android.graphics.Bitmap): Color? {
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0
    val stepX = (bitmap.width / 12).coerceAtLeast(1)
    val stepY = (bitmap.height / 16).coerceAtLeast(1)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val p = bitmap.getPixel(x, y)
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
            x += stepX
        }
        y += stepY
    }
    if (n == 0) return null
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
    hsv[1] = (hsv[1] * 1.35f).coerceIn(0.18f, 0.85f)
    hsv[2] = hsv[2].coerceIn(0.28f, 0.78f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun lighten(c: Color, amount: Float): Color = Color(
    red = c.red + (1f - c.red) * amount,
    green = c.green + (1f - c.green) * amount,
    blue = c.blue + (1f - c.blue) * amount,
    alpha = c.alpha,
)

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
