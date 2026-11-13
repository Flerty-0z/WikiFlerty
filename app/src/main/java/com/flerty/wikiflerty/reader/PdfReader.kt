package com.flerty.wikiflerty.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.LruCache
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import com.flerty.wikiflerty.data.Bookmark
import com.flerty.wikiflerty.data.Note
import com.flerty.wikiflerty.data.ReadStatus
import com.flerty.wikiflerty.data.convert.PdfOutline
import com.flerty.wikiflerty.data.convert.PdfPageMap
import com.flerty.wikiflerty.ui.LocalRevealViewport
import com.flerty.wikiflerty.ui.PickerOption
import com.flerty.wikiflerty.ui.PickerRow
import com.flerty.wikiflerty.ui.RevealViewport
import com.flerty.wikiflerty.ui.revealScroll
import com.flerty.wikiflerty.ui.revealViewport
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.produceState
import com.flerty.wikiflerty.ui.AppButton
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.AppSheet
import com.flerty.wikiflerty.ui.AppStyle
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.HeaderLabel
import com.flerty.wikiflerty.ui.KitSlider
import com.flerty.wikiflerty.ui.KitSpinner
import com.flerty.wikiflerty.ui.ListDivider
import com.flerty.wikiflerty.ui.LocalFeedback
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.Reveal
import com.flerty.wikiflerty.ui.SheetDivider
import com.flerty.wikiflerty.ui.TextInputSheet
import com.flerty.wikiflerty.ui.WikiFlertyTheme
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import com.flerty.wikiflerty.ui.sheetClose
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

class PdfDoc private constructor(
    private val engine: PdfEngine,
    private val widths: IntArray,
    private val heights: IntArray,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val thread = executor.asCoroutineDispatcher()
    @Volatile private var closed = false
    private val texts = ConcurrentHashMap<Int, String>()
    private val linkCache = ConcurrentHashMap<Int, List<PdfLink>>()

    val count: Int get() = widths.size
    val textSupported: Boolean get() = engine.textSupported

    fun aspect(i: Int): Float = widths[i].toFloat() / heights[i].coerceAtLeast(1)
    fun pageWidth(i: Int): Int = widths[i]
    fun pageHeight(i: Int): Int = heights[i]

    private val cache = object : LruCache<Long, ImageBitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 5).toInt().coerceAtLeast(16 * 1024)) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    private fun key(i: Int, w: Int) = (i.toLong() shl 20) or w.toLong()

    fun cached(i: Int, w: Int): ImageBitmap? = cache.get(key(i, w))

    private suspend fun <T> onPage(i: Int, block: (PdfPageApi) -> T): T? {
        if (closed || i !in 0 until count) return null
        return try {
            withContext(thread) {
                if (closed) return@withContext null
                val page = engine.page(i)
                try {
                    block(page)
                } finally {
                    page.close()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun render(i: Int, w: Int): ImageBitmap? {
        cached(i, w)?.let { return it }
        return onPage(i) { page ->
            cached(i, w) ?: run {
                val h = (w.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, w * 4)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp)
                bmp.asImageBitmap().also { cache.put(key(i, w), it) }
            }
        }
    }

    suspend fun text(i: Int): String? {
        if (!textSupported) return null
        texts[i]?.let { return it }
        return onPage(i) { it.text().orEmpty() }?.also { texts[i] = it }
    }

    fun cachedLinks(i: Int): List<PdfLink>? = linkCache[i]

    suspend fun links(i: Int): List<PdfLink> {
        if (!textSupported) return emptyList()
        linkCache[i]?.let { return it }
        return (onPage(i) { it.links() } ?: emptyList()).also { linkCache[i] = it }
    }

    suspend fun select(i: Int, start: Point, stop: Point): PdfSelectionResult? = onPage(i) { it.select(start, stop) }

    suspend fun search(i: Int, query: String): List<List<RectF>> = onPage(i) { it.search(query) } ?: emptyList()

    fun close() {
        if (closed) return
        closed = true
        executor.execute {
            runCatching { engine.close() }
            cache.evictAll()
        }
        executor.shutdown()
    }

    companion object {
        suspend fun open(file: File, sizesFile: File): PdfDoc = withContext(Dispatchers.IO) {
            val engine = PdfEngine.open(file)
            try {
                val n = engine.count
                val stored = runCatching {
                    val j = JSONObject(sizesFile.readText())
                    val w = j.getJSONArray("w")
                    val h = j.getJSONArray("h")
                    if (w.length() != n || h.length() != n) null else IntArray(n) { w.getInt(it) } to IntArray(n) { h.getInt(it) }
                }.getOrNull()
                val (widths, heights) = stored ?: run {
                    val w = IntArray(n)
                    val h = IntArray(n)
                    for (i in 0 until n) {
                        runCatching {
                            val p = engine.page(i)
                            try {
                                w[i] = p.width.coerceAtLeast(1)
                                h[i] = p.height.coerceAtLeast(1)
                            } finally {
                                p.close()
                            }
                        }
                        if (w[i] <= 0) {
                            w[i] = if (i > 0) w[i - 1] else 612
                            h[i] = if (i > 0) h[i - 1] else 792
                        }
                    }
                    runCatching {
                        sizesFile.parentFile?.mkdirs()
                        sizesFile.writeText(JSONObject().put("w", JSONArray(w.toList())).put("h", JSONArray(h.toList())).toString())
                    }
                    w to h
                }
                PdfDoc(engine, widths, heights)
            } catch (e: Throwable) {
                engine.close()
                throw e
            }
        }
    }
}

enum class PdfFilter(val icon: ImageVector, val paper: Color, val filter: ColorFilter?) {
    Day(AppIcons.Sun, Color.White, null),
    Night(AppIcons.Moon, Color(0xFF101010), ColorFilter.colorMatrix(nightMatrix())),
    Sepia(AppIcons.Drop, Color(0xFFFCEFD6), ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        0.98f, 0f, 0f, 0f, 4f,
        0f, 0.92f, 0f, 0f, 4f,
        0f, 0f, 0.80f, 0f, 6f,
        0f, 0f, 0f, 1f, 0f,
    )))),
}

private fun nightMatrix(): ColorMatrix {
    val h = arrayOf(
        floatArrayOf(-0.574f, 1.43f, 0.144f),
        floatArrayOf(0.426f, 0.43f, 0.144f),
        floatArrayOf(0.426f, 1.43f, -0.856f),
    )
    val k = -0.88f
    val off = 240f
    return ColorMatrix(floatArrayOf(
        h[0][0] * k, h[0][1] * k, h[0][2] * k, 0f, off,
        h[1][0] * k, h[1][1] * k, h[1][2] * k, 0f, off,
        h[2][0] * k, h[2][1] * k, h[2][2] * k, 0f, off,
        0f, 0f, 0f, 1f, 0f,
    ))
}

internal const val PDF_RECTS = "pdfRects"

internal fun encodeRects(rects: List<RectF>): String =
    rects.joinToString(";") { String.format(Locale.US, "%.1f,%.1f,%.1f,%.1f", it.left, it.top, it.right, it.bottom) }

internal fun decodeRects(raw: Any?): List<RectF> {
    val s = raw as? String ?: return emptyList()
    return s.split(';').mapNotNull { part ->
        val v = part.split(',').mapNotNull { it.toFloatOrNull() }
        if (v.size == 4) RectF(v[0], v[1], v[2], v[3]) else null
    }
}

data class PdfSel(val page: Int, val start: Point, val stop: Point, val text: String, val rects: List<RectF>)

class PdfMark(val note: Note, val rects: List<RectF>)

class PdfUiState(context: Context, val title: String, val author: String, start: Int, val finished: Boolean, dark: Boolean) {
    private val prefs = context.getSharedPreferences("pdf", Context.MODE_PRIVATE)
    var doc by mutableStateOf<PdfDoc?>(null)
    var error by mutableStateOf<String?>(null)
    var ui by mutableStateOf(false)
    var page by mutableIntStateOf(start)
    var jump by mutableStateOf<Int?>(null)
    var grid by mutableStateOf(false)
    var goTo by mutableStateOf(false)
    var askFinish by mutableStateOf(false)
    var asked = finished
    var outline by mutableStateOf<List<PdfOutline.Entry>>(emptyList())
    var sheet by mutableStateOf<ReaderSheet?>(null)
    var selection by mutableStateOf<PdfSel?>(null)
    var selectionBounds by mutableStateOf<Pair<Float, Float>?>(null)
    var editor by mutableStateOf<NoteDraft?>(null)
    var translate by mutableStateOf<TranslateInfo?>(null)
    var quote by mutableStateOf<QuoteDraft?>(null)
    var nameMark by mutableStateOf<Bookmark?>(null)
    val search = SearchState()
    var paged by mutableStateOf(prefs.getBoolean("paged", false))
        private set
    var filter by mutableStateOf(PdfFilter.entries.getOrElse(prefs.getInt("filter", if (dark) 1 else 0)) { PdfFilter.Day })
        private set

    fun togglePaged() {
        paged = !paged
        prefs.edit().putBoolean("paged", paged).apply()
    }

    fun nextFilter() = pickFilter(PdfFilter.entries[(filter.ordinal + 1) % PdfFilter.entries.size])

    fun pickFilter(value: PdfFilter) {
        filter = value
        prefs.edit().putInt("filter", value.ordinal).apply()
    }

    fun pickPaged(value: Boolean) {
        if (paged != value) togglePaged()
    }

    fun chapterAt(page: Int): String? {
        var best: PdfOutline.Entry? = null
        for (e in outline) if (e.page <= page && (best == null || e.page >= best.page)) best = e
        return best?.title
    }
}

private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var ox by mutableFloatStateOf(0f)
    var oy by mutableFloatStateOf(0f)
    var w = 1f
    var h = 1f

    val zoomed: Boolean get() = scale > 1.01f

    private fun clampX() {
        val mx = (scale - 1f) * w / 2f
        ox = ox.coerceIn(-mx, mx)
    }

    fun zoom(factor: Float, centroid: Offset) {
        val s2 = (scale * factor).coerceIn(1f, 5f)
        val qx = centroid.x - w / 2f
        val qy = centroid.y - h / 2f
        ox = qx - (qx - ox) * s2 / scale
        oy = qy - (qy - oy) * s2 / scale
        scale = s2
        clampX()
        val my = (scale - 1f) * h / 2f
        oy = oy.coerceIn(-my, my)
    }

    fun pan(dx: Float, dy: Float): Float {
        ox += dx
        clampX()
        val my = (scale - 1f) * h / 2f
        val ny = oy + dy
        oy = ny.coerceIn(-my, my)
        return ny - oy
    }

    fun reset() {
        scale = 1f
        ox = 0f
        oy = 0f
    }

    suspend fun animateTo(target: Float, focus: Offset) {
        val s0 = scale
        val x0 = ox
        val y0 = oy
        val qx = focus.x - w / 2f
        val qy = focus.y - h / 2f
        val mx = (target - 1f) * w / 2f
        val my = (target - 1f) * h / 2f
        val x1 = if (target <= 1f) 0f else (qx - (qx - x0) * target / s0).coerceIn(-mx, mx)
        val y1 = if (target <= 1f) 0f else (qy - (qy - y0) * target / s0).coerceIn(-my, my)
        animate(0f, 1f, animationSpec = tween(Motion.ms(260).coerceAtLeast(1))) { v, _ ->
            scale = s0 + (target - s0) * v
            ox = x0 + (x1 - x0) * v
            oy = y0 + (y1 - y0) * v
        }
    }
}

private class PageHit(val page: Int, val x: Float, val y: Float)

private class PageRegistry {
    val pages = HashMap<Int, LayoutCoordinates>()
    var root: LayoutCoordinates? = null

    fun hit(doc: PdfDoc, pos: Offset): PageHit? {
        val r = root?.takeIf { it.isAttached } ?: return null
        val win = r.localToWindow(pos)
        for ((i, c) in pages) {
            if (!c.isAttached) continue
            val local = c.windowToLocal(win)
            val w = c.size.width.toFloat()
            val h = c.size.height.toFloat()
            if (local.x in 0f..w && local.y in 0f..h && w > 0 && h > 0) {
                return PageHit(i, local.x / w * doc.pageWidth(i), local.y / h * doc.pageHeight(i))
            }
        }
        return null
    }

    fun windowRect(doc: PdfDoc, page: Int, rect: RectF): Pair<Float, Float>? {
        val c = pages[page]?.takeIf { it.isAttached } ?: return null
        val sx = c.size.width.toFloat() / doc.pageWidth(page)
        val sy = c.size.height.toFloat() / doc.pageHeight(page)
        val top = c.localToWindow(Offset(rect.left * sx, rect.top * sy)).y
        val bottom = c.localToWindow(Offset(rect.left * sx, rect.bottom * sy)).y
        return top to bottom
    }
}

class PdfReaderActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BOOK = "book"
        const val EXTRA_PAGE = "page"
    }

    private val app get() = application as App
    private lateinit var bookId: String
    private lateinit var book: Book
    internal lateinit var state: PdfUiState
    private var visible = false
    private var lastActivity = 0L
    private var pagesSinceTrack = 0
    private var lastPage = -1
    private var saveJob: Job? = null
    private var pendingPage = -1
    private var searchJob: Job? = null
    private var selectJob: Job? = null
    private var translateJob: Job? = null
    private var translateLocator: Locator? = null
    private var pendingQuoteSave: Triple<QuoteDraft, QuoteLook, QuoteFormat>? = null
    internal var map: PdfPageMap? = null

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingQuoteSave
        pendingQuoteSave = null
        if (granted && pending != null) saveQuote(pending.first, pending.second, pending.third)
    }

    override fun finish() {
        super.finish()
        ReaderTransitions.closing(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flerty.wikiflerty.ui.HighRefresh.apply(window)
        ReaderTransitions.install(this)
        val found = intent.getStringExtra(EXTRA_BOOK)?.let { id -> app.library.books.value.firstOrNull { it.id == id } }
        if (found == null) {
            finish()
            return
        }
        book = found
        bookId = found.id
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val settings = app.readerSettings.settings.value
        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (settings.brightness >= 0) {
            window.attributes = window.attributes.apply { screenBrightness = settings.brightness.toFloat().coerceIn(0.01f, 1f) }
        }
        if (found.pdfText) map = PdfPageMap.load(this, found.id)
        val requested = intent.getIntExtra(EXTRA_PAGE, -1)
        val start = if (requested >= 0) requested else startPage(found)
        lastPage = start
        state = PdfUiState(this, found.title, found.author, start, found.status == ReadStatus.Finished, !settings.pageTheme.isLight)
        lifecycleScope.launch { app.readerSettings.settings.collect { applySettings(it) } }
        app.appScope.launch { app.library.markOpened(found.id) }
        val file = app.library.pdfFile(found)
        val meta = File(filesDir, "pdfmeta")
        lifecycleScope.launch {
            runCatching { PdfDoc.open(file, File(meta, "${found.id}.json")) }
                .onSuccess { doc ->
                    if (isDestroyed) {
                        doc.close()
                        return@onSuccess
                    }
                    if (requested >= doc.count) state.page = doc.count - 1
                    state.doc = doc
                    if (!found.pdfText && found.positions != doc.count) app.library.setPositions(found.id, doc.count)
                }
                .onFailure { state.error = if (it is SecurityException) "PDF защищён паролем" else "Не удалось открыть PDF" }
        }
        lifecycleScope.launch {
            state.outline = withContext(Dispatchers.IO) { loadOutline(file, File(meta, "${found.id}.toc.json")) }
        }
        setContent {
            CompositionLocalProvider(LocalFeedback provides app.feedback) {
                WikiFlertyTheme {
                    PdfReaderScreen(this@PdfReaderActivity, state)
                }
            }
        }
    }

    private fun loadOutline(file: File, cacheFile: File): List<PdfOutline.Entry> {
        runCatching {
            if (cacheFile.exists()) {
                val arr = JSONArray(cacheFile.readText())
                return (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    PdfOutline.Entry(o.getString("t"), o.getInt("d"), o.getInt("p"))
                }
            }
        }
        val list = PdfOutline.read(file)
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("t", it.title).put("d", it.depth).put("p", it.page)) }
            cacheFile.writeText(arr.toString())
        }
        return list
    }

    private fun startPage(book: Book): Int {
        val saved = book.locatorJson?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        val total = map?.pages ?: book.positions
        saved?.let { PdfLocators.pageOf(it, map, total) }?.let { return it }
        return if (total > 1) (book.progress * (total - 1)).roundToInt().coerceIn(0, total - 1) else 0
    }

    private fun applySettings(s: ReaderSettings) {
        if (s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = if (s.brightness < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else s.brightness.toFloat().coerceIn(0.01f, 1f)
        }
        val orientation = when (ScreenOrientation.of(s.orientation)) {
            ScreenOrientation.Auto -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientation.Portrait -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenOrientation.Landscape -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (requestedOrientation != orientation) requestedOrientation = orientation
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = s.pageTheme.isLight
            isAppearanceLightNavigationBars = s.pageTheme.isLight
            if (s.fullscreen) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    internal fun pageOf(loc: Locator?): Int? = loc?.let { PdfLocators.pageOf(it, map, state.doc?.count ?: map?.pages ?: 0) }

    internal fun openText() {
        val m = map ?: return
        flushProgress()
        val current = app.library.books.value.firstOrNull { it.id == bookId } ?: return
        val loc = PdfLocators.textLocator(m, state.page)
        lifecycleScope.launch {
            app.prepareReader(current.copy(locatorJson = null), loc)
                .onSuccess {
                    startActivity(Intent(this@PdfReaderActivity, ReaderActivity::class.java))
                    finish()
                }
                .onFailure { Toast.makeText(this@PdfReaderActivity, "Не удалось открыть текст", Toast.LENGTH_SHORT).show() }
        }
    }

    internal fun searchUnavailable() {
        val text = if (map != null) "Поиск по тексту — в виде «Текст»" else "В этом PDF нет текстового слоя"
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        if (map != null) {
            state.sheet = ReaderSheet.Theme
        }
    }

    internal fun locator(page: Int, rects: List<RectF> = emptyList(), text: String? = null): Locator {
        val total = state.doc?.count ?: book.positions.coerceAtLeast(1)
        val progression = if (total <= 1) 1.0 else page.toDouble() / (total - 1)
        return Locator(
            href = Url("page")!!,
            mediaType = MediaType.PDF,
            title = state.chapterAt(page) ?: state.title,
            locations = Locator.Locations(
                position = page + 1,
                progression = progression,
                totalProgression = progression,
                otherLocations = if (rects.isEmpty()) emptyMap() else mapOf(PDF_RECTS to encodeRects(rects)),
            ),
            text = Locator.Text(highlight = text),
        )
    }

    internal fun onPage(page: Int) {
        if (page == lastPage) return
        pagesSinceTrack += abs(page - lastPage).coerceAtMost(3)
        lastPage = page
        trackReading()
        pendingPage = page
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(1200)
            flushProgress()
        }
        val doc = state.doc ?: return
        if (page >= doc.count - 1 && !state.asked) {
            state.asked = true
            state.askFinish = true
        }
    }

    internal fun markFinished() {
        state.askFinish = false
        app.appScope.launch { app.library.setStatus(bookId, ReadStatus.Finished) }
    }

    private fun flushProgress() {
        saveJob?.cancel()
        saveJob = null
        val page = pendingPage
        if (page < 0) return
        pendingPage = -1
        if (state.doc == null) return
        val loc = locator(page)
        val id = bookId
        app.appScope.launch { app.library.saveProgress(id, loc) }
    }

    private fun trackReading() {
        val now = SystemClock.elapsedRealtime()
        if (visible && lastActivity > 0) {
            val gap = now - lastActivity
            val counted = if (gap <= 5 * 60_000L) gap else 60_000L
            val key = app.library.books.value.firstOrNull { it.id == bookId }?.statsKey ?: bookId
            app.stats.add(key, counted, pagesSinceTrack)
            pagesSinceTrack = 0
        }
        lastActivity = now
    }

    internal fun toggleBookmark(marks: List<Bookmark>) {
        val page = state.page
        val existing = marks.filter { pageOf(it.locator) == page }
        lifecycleScope.launch {
            if (existing.isNotEmpty()) {
                app.bookmarks.delete(existing.map { it.id })
            } else {
                val snippet = state.doc?.text(page)?.replace(Regex("\\s+"), " ")?.trim()?.take(140)?.takeIf { it.isNotBlank() } ?: "Страница ${page + 1}"
                state.nameMark = app.bookmarks.create(bookId, locator(page), state.chapterAt(page) ?: "Страница ${page + 1}", snippet)
            }
        }
    }

    internal val bookmarkActions by lazy {
        BookmarkActions(
            onToggle = { },
            onOpen = { mark ->
                state.sheet = null
                pageOf(mark.locator)?.let { state.jump = it }
            },
            onDelete = { mark -> lifecycleScope.launch { app.bookmarks.delete(listOf(mark.id)) } },
            onRename = { mark -> state.nameMark = mark },
            onName = { id, label -> app.appScope.launch { app.bookmarks.rename(id, label) } },
        )
    }

    internal fun beginSelection(page: Int, x: Float, y: Float): Boolean {
        val doc = state.doc ?: return false
        if (!doc.textSupported) {
            state.editor = NoteDraft(null, locator(page), "Страница ${page + 1}", "", app.readerSettings.settings.value.markerColor, fromList = false)
            state.sheet = ReaderSheet.NoteEditor
            state.ui = false
            return true
        }
        val p = Point(x.roundToInt(), y.roundToInt())
        selectJob?.cancel()
        selectJob = lifecycleScope.launch {
            val r = doc.select(page, p, p) ?: return@launch
            val first = r.rects.firstOrNull() ?: return@launch
            val last = r.rects.last()
            state.translate = null
            state.ui = false
            state.selection = PdfSel(
                page,
                Point((first.left + 0.5f).roundToInt(), first.centerY().roundToInt()),
                Point((last.right - 0.5f).roundToInt(), last.centerY().roundToInt()),
                r.text,
                r.rects,
            )
        }
        return true
    }

    internal fun moveHandle(start: Boolean, x: Float, y: Float) {
        val doc = state.doc ?: return
        val sel = state.selection ?: return
        val p = Point(x.roundToInt(), y.roundToInt())
        val a = if (start) p else sel.start
        val b = if (start) sel.stop else p
        selectJob?.cancel()
        selectJob = lifecycleScope.launch {
            val r = doc.select(sel.page, a, b) ?: return@launch
            if (r.rects.isEmpty()) return@launch
            state.selection = state.selection?.copy(start = a, stop = b, text = r.text, rects = r.rects)
        }
    }

    internal fun endSelection() {
        selectJob?.cancel()
        state.selection = null
        state.selectionBounds = null
    }

    private fun selectionLocator(sel: PdfSel) = locator(sel.page, sel.rects, sel.text.take(500))

    private fun cleanText(text: String) = text.replace(Regex("-\\s*\\n\\s*"), "").replace(Regex("\\s+"), " ").trim()

    internal val noteActions by lazy {
        NoteActions(
            onSave = { draft ->
                lifecycleScope.launch {
                    val id = draft.id
                    if (id == null) {
                        val loc = draft.locator ?: return@launch
                        val page = (loc.locations.position ?: 1) - 1
                        app.notes.create(bookId, loc, draft.text, draft.note, draft.color, state.chapterAt(page))
                    } else {
                        app.notes.edit(id, draft.note, draft.color)
                    }
                    app.readerSettings.update { it.copy(markerColor = draft.color) }
                    state.sheet = if (draft.fromList) ReaderSheet.Notes else null
                    state.editor = null
                }
            },
            onDelete = { id ->
                lifecycleScope.launch {
                    app.notes.delete(id)
                    state.sheet = if (state.editor?.fromList == true) ReaderSheet.Notes else null
                    state.editor = null
                    Toast.makeText(this@PdfReaderActivity, "Заметка удалена", Toast.LENGTH_SHORT).show()
                }
            },
            onGoTo = { note ->
                state.sheet = null
                pageOf(note.locator)?.let { state.jump = it }
            },
            onSelectionNote = {
                state.selection?.let { sel ->
                    state.editor = NoteDraft(null, selectionLocator(sel), cleanText(sel.text), "", app.readerSettings.settings.value.markerColor, fromList = false)
                    endSelection()
                    state.sheet = ReaderSheet.NoteEditor
                }
            },
            onSelectionMarker = { color ->
                state.selection?.let { sel ->
                    val loc = selectionLocator(sel)
                    endSelection()
                    lifecycleScope.launch {
                        app.notes.create(bookId, loc, cleanText(sel.text), "", color, state.chapterAt(sel.page))
                        if (color != app.readerSettings.settings.value.markerColor) app.readerSettings.update { it.copy(markerColor = color) }
                    }
                    app.feedback.play(FeedbackSound.Toggle)
                }
            },
            onSelectionCopy = {
                state.selection?.let { sel ->
                    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("WikiFlerty", cleanText(sel.text)))
                    Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
                }
                endSelection()
            },
            onSelectionAi = { },
            onSelectionClose = { endSelection() },
            onSelectionQuote = {
                state.selection?.let { sel ->
                    state.quote = QuoteDraft(cleanText(sel.text), book.title, book.author, app.library.coverFile(book)?.takeIf { it.exists() }?.path, android.graphics.Typeface.SERIF)
                    endSelection()
                    state.sheet = ReaderSheet.Quote
                }
            },
            onSelectionTranslate = {
                state.selection?.let { sel ->
                    endSelection()
                    startTranslate(cleanText(sel.text), selectionLocator(sel), word = false)
                }
            },
            onSelectionDictionary = {
                state.selection?.let { sel ->
                    endSelection()
                    startTranslate(cleanText(sel.text), selectionLocator(sel), word = true)
                }
            },
        )
    }

    private fun cleanWord(text: String): String =
        text.replace(Regex("\\s+"), " ").trim().trim('.', ',', '!', '?', ':', ';', '«', '»', '"', '“', '”', '(', ')', '—', '-', '…')

    private fun startTranslate(text: String, loc: Locator?, word: Boolean, target: String = app.readerSettings.settings.value.translateTo) {
        val clean = if (word) cleanWord(text).take(120) else text.trim()
        if (clean.isBlank()) return
        translateJob?.cancel()
        translateLocator = loc
        state.ui = false
        val known = word && app.dictionary.entries.value.any { it.word.equals(clean, ignoreCase = true) }
        state.translate = TranslateInfo(clean, target, word, saved = known)
        translateJob = lifecycleScope.launch {
            val result = runCatching { if (word) Translator.define(clean, target) else Translator.translate(clean, target) }
            val current = state.translate?.takeIf { it.text == clean && it.target == target } ?: return@launch
            result.onSuccess { r ->
                state.translate = current.copy(loading = false, result = r, error = null)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                state.translate = current.copy(loading = false, error = "Нет связи с переводчиком")
            }
        }
    }

    private suspend fun contextAround(loc: Locator?): String {
        val page = (loc?.locations?.position ?: return "") - 1
        val text = state.doc?.text(page)?.replace(Regex("\\s+"), " ") ?: return ""
        val needle = loc.text.highlight?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val at = if (needle.isEmpty()) -1 else text.indexOf(needle)
        if (at < 0) return ""
        return text.substring(max(0, at - 160), min(text.length, at + needle.length + 160)).trim()
    }

    internal val translateActions by lazy {
        TranslateActions(
            onTarget = { code ->
                val info = state.translate
                app.readerSettings.update { it.copy(translateTo = code) }
                if (info != null) startTranslate(info.text, translateLocator, info.word, code)
            },
            onSave = {
                val info = state.translate
                val result = info?.result
                if (info != null && result != null && !info.saved && !(info.word && result.empty)) {
                    state.translate = info.copy(saved = true)
                    val loc = translateLocator
                    val word = if (info.word) info.text else info.text.take(300)
                    app.appScope.launch {
                        app.dictionary.add(word, result.card(), contextAround(loc).take(400), bookId, book.title, loc)
                    }
                    app.feedback.play(FeedbackSound.Toggle)
                }
            },
            onRetry = { state.translate?.let { startTranslate(it.text, translateLocator, it.word, it.target) } },
            onCopy = {
                state.translate?.result?.let { r ->
                    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("WikiFlerty", r.text))
                    Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
                }
            },
            onClose = {
                translateJob?.cancel()
                state.translate = null
            },
        )
    }

    internal val searchActions by lazy {
        SearchActions(
            state = state.search,
            onSearch = { q -> runSearch(q) },
            onOpen = { index ->
                val hit = state.search.results.getOrNull(index)
                if (hit != null) {
                    state.search.current = index
                    state.sheet = null
                    state.ui = false
                    hit.locator.locations.position?.let { state.jump = it - 1 }
                }
            },
            onClear = {
                searchJob?.cancel()
                state.search.results.clear()
                state.search.current = -1
                state.search.query = ""
                state.search.searched = false
                state.search.running = false
                state.search.limited = false
            },
        )
    }

    private fun runSearch(query: String) {
        val doc = state.doc ?: return
        val s = state.search
        searchJob?.cancel()
        s.results.clear()
        s.current = -1
        s.error = null
        s.limited = false
        s.searched = false
        s.running = true
        val q = query.trim()
        if (q.isEmpty()) {
            s.running = false
            return
        }
        val needle = q.lowercase()
        searchJob = lifecycleScope.launch {
            try {
                outer@ for (i in 0 until doc.count) {
                    ensureActive()
                    val text = doc.text(i) ?: continue
                    val lower = text.lowercase()
                    var at = lower.indexOf(needle)
                    if (at < 0) continue
                    val bounds = doc.search(i, q)
                    var k = 0
                    while (at >= 0) {
                        val end = min(text.length, at + needle.length)
                        val before = text.substring(max(0, at - 50), at).replace(Regex("\\s+"), " ").trimStart()
                        val after = text.substring(end, min(text.length, end + 80)).replace(Regex("\\s+"), " ").trimEnd()
                        s.results += SearchHit(
                            locator(i, bounds.getOrNull(k).orEmpty(), text.substring(at, end)),
                            state.chapterAt(i) ?: "Страница ${i + 1}",
                            before,
                            text.substring(at, end),
                            after,
                        )
                        k++
                        if (s.results.size >= 400) {
                            s.limited = true
                            break@outer
                        }
                        at = lower.indexOf(needle, end)
                    }
                }
            } finally {
                s.running = false
                s.searched = true
            }
        }
    }

    internal val quoteActions by lazy {
        QuoteActions(
            onShare = { draft, look, format -> shareQuote(draft, look, format) },
            onSave = { draft, look, format ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    pendingQuoteSave = Triple(draft, look, format)
                    storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    saveQuote(draft, look, format)
                }
            },
        )
    }

    private suspend fun renderQuote(draft: QuoteDraft, look: QuoteLook, format: QuoteFormat): Bitmap {
        val theme = QuoteTheme(AppStyle.base, AppStyle.current.accent.toInt())
        return withContext(Dispatchers.Default) {
            QuoteCard.render(draft, look, format, theme, QuoteCard.loadCover(draft.coverPath, maxWidth = 480))
        }
    }

    private fun shareQuote(draft: QuoteDraft, look: QuoteLook, format: QuoteFormat) {
        if (quoteActions.busy) return
        quoteActions.busy = true
        lifecycleScope.launch {
            try {
                val bitmap = renderQuote(draft, look, format)
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(cacheDir, "share").apply { mkdirs() }
                    val file = File(dir, "quote.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    androidx.core.content.FileProvider.getUriForFile(this@PdfReaderActivity, "$packageName.files", file)
                }
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = ClipData.newRawUri("quote", uri)
                startActivity(Intent.createChooser(intent, "Поделиться цитатой"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@PdfReaderActivity, "Не удалось поделиться", Toast.LENGTH_SHORT).show()
            } finally {
                quoteActions.busy = false
            }
        }
    }

    private fun saveQuote(draft: QuoteDraft, look: QuoteLook, format: QuoteFormat) {
        if (quoteActions.busy) return
        quoteActions.busy = true
        lifecycleScope.launch {
            val saved = try {
                val bitmap = renderQuote(draft, look, format)
                withContext(Dispatchers.IO) { writeToGallery(bitmap) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                quoteActions.busy = false
            }
            Toast.makeText(this@PdfReaderActivity, if (saved) "Сохранено в галерею" else "Не удалось сохранить", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeToGallery(bitmap: Bitmap): Boolean {
        val name = "WikiFlerty_${System.currentTimeMillis()}.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/WikiFlerty")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val written = runCatching {
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
            }.getOrDefault(false)
            if (!written) {
                resolver.delete(uri, null, null)
                return false
            }
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "WikiFlerty")
            dir.mkdirs()
            val file = File(dir, name)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            android.media.MediaScannerConnection.scanFile(this, arrayOf(file.path), arrayOf("image/png"), null)
            true
        }
    }

    internal fun openLink(link: PdfLink) {
        val page = link.page
        if (page != null) {
            state.jump = page.coerceIn(0, (state.doc?.count ?: 1) - 1)
            return
        }
        val uri = link.uri ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
            .onFailure { Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show() }
    }

    internal fun editNote(note: Note, fromList: Boolean) {
        state.editor = NoteDraft(note.id, note.locator, note.text, note.note, note.color, fromList = fromList)
        state.sheet = ReaderSheet.NoteEditor
        state.ui = false
    }

    internal fun noteForPage(page: Int) {
        if (state.doc == null) return
        state.editor = NoteDraft(null, locator(page), "Страница ${page + 1}", "", app.readerSettings.settings.value.markerColor, fromList = false)
        state.sheet = ReaderSheet.NoteEditor
        state.ui = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volume = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volume && app.readerSettings.settings.value.volumeKeys && ::state.isInitialized && state.doc != null && state.sheet == null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val delta = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1
                val count = state.doc?.count ?: 0
                state.jump = (state.page + delta).coerceIn(0, (count - 1).coerceAtLeast(0))
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        lastActivity = SystemClock.elapsedRealtime()
    }

    override fun onPause() {
        if (::state.isInitialized) trackReading()
        visible = false
        app.appScope.launch(Dispatchers.IO) { app.stats.save() }
        com.flerty.wikiflerty.widget.ReadingWidget.refreshSoon(app)
        super.onPause()
    }

    override fun onStop() {
        if (::state.isInitialized) flushProgress()
        super.onStop()
        app.cloud.request(2_000)
    }

    override fun onDestroy() {
        if (::state.isInitialized) state.doc?.close()
        super.onDestroy()
    }
}

private val PdfNav = listOf(ReaderSheet.Toc, ReaderSheet.Notes, ReaderSheet.Bookmarks)
private val PdfOptionPages = listOf(ReaderSheet.Theme, ReaderSheet.Screen, ReaderSheet.Sound, ReaderSheet.Atmosphere)

@Composable
private fun PdfViewOptions(host: PdfReaderActivity, state: PdfUiState) {
    if (host.map != null) {
        PickerRow(
            label = "Вид",
            icon = AppIcons.Book,
            options = listOf(
                PickerOption("text", "Текст", "Как обычная книга: шрифты, темы, озвучка", icon = AppIcons.TextSize),
                PickerOption("pages", "Оригинал", "Страницы как в файле", icon = AppIcons.Pages),
            ),
            selectedId = "pages",
        ) { id -> if (id == "text") host.openText() }
    }
    PickerRow(
        label = "Прокрутка",
        icon = AppIcons.Scroll,
        options = listOf(
            PickerOption("scroll", "Лента", "Страницы идут сплошной лентой", icon = AppIcons.Scroll),
            PickerOption("pages", "Листание", "По одной странице", icon = AppIcons.Pages),
        ),
        selectedId = if (state.paged) "pages" else "scroll",
    ) { id -> state.pickPaged(id == "pages") }
    PickerRow(
        label = "Цвет страниц",
        icon = AppIcons.Theme,
        options = listOf(
            PickerOption(PdfFilter.Day.name, "Как в файле", "Белые страницы", icon = AppIcons.Sun, swatch = Color.White),
            PickerOption(PdfFilter.Sepia.name, "Сепия", "Тёплая бумага", icon = AppIcons.Drop, swatch = PdfFilter.Sepia.paper),
            PickerOption(PdfFilter.Night.name, "Ночь", "Светлый текст на тёмном", icon = AppIcons.Moon, swatch = PdfFilter.Night.paper),
        ),
        selectedId = state.filter.name,
    ) { id -> PdfFilter.entries.firstOrNull { it.name == id }?.let(state::pickFilter) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfReaderScreen(host: PdfReaderActivity, state: PdfUiState) {
    val app = host.application as App
    val settings by app.readerSettings.settings.collectAsState()
    val allNotes by app.notes.notes.collectAsState()
    val allMarks by app.bookmarks.bookmarks.collectAsState()
    val bookId = host.intent.getStringExtra(PdfReaderActivity.EXTRA_BOOK).orEmpty()
    val notes = remember(allNotes, bookId) { allNotes.filter { it.bookId == bookId }.sortedBy { it.progression } }
    val marks = remember(allMarks, bookId) { allMarks.filter { it.bookId == bookId }.sortedBy { it.progression } }
    val docReady = state.doc != null
    val marksByPage = remember(notes, docReady) {
        notes.mapNotNull { n -> host.pageOf(n.locator)?.let { it to n } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) ->
                list.map { n ->
                    val loc = n.locator
                    PdfMark(n, if (loc != null && PdfLocators.isPage(loc)) decodeRects(loc.locations.otherLocations[PDF_RECTS]) else emptyList())
                }
            }
    }
    val bookmarkedPages = remember(marks, docReady) { marks.mapNotNull { host.pageOf(it.locator) }.toSet() }
    val cover by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val book = app.library.books.value.firstOrNull { it.id == bookId }
                val file = book?.let { app.library.coverFile(it) }?.takeIf { it.exists() }
                file?.let { android.graphics.BitmapFactory.decodeFile(it.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val feedback = LocalFeedback.current
    LaunchedEffect(state) { snapshotFlow { state.page }.collect { host.onPage(it) } }
    BackHandler(enabled = state.sheet != null || state.selection != null || state.translate != null || state.ui) {
        when {
            state.sheet == ReaderSheet.NoteEditor && state.editor?.fromList == true -> state.sheet = ReaderSheet.Notes
            state.sheet != null -> state.sheet = null
            state.selection != null -> host.endSelection()
            state.translate != null -> host.translateActions.onClose()
            else -> state.ui = false
        }
    }

    val top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(Color(settings.pageTheme.background))) {
        val doc = state.doc
        Crossfade(
            targetState = when {
                state.error != null -> 2
                doc == null -> 0
                else -> 1
            },
            animationSpec = Motion.fade(260),
            label = "pdf-state",
        ) { s ->
            when (s) {
                0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(36.dp)) }
                2 -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(AppIcons.Alert, null, tint = AppColors.Muted, modifier = Modifier.size(34.dp))
                    Text(state.error.orEmpty(), color = AppColors.Text, fontSize = 16.sp)
                    if (host.map != null) AppButton("Открыть как текст", primary = true) { host.openText() }
                    AppButton("Назад", primary = host.map == null) { host.finish() }
                }
                else -> state.doc?.let { PdfContent(host, it, state, marksByPage, bookmarkedPages, settings) }
            }
        }

        val count = doc?.count ?: 0

        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = state.ui && doc != null,
                enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(300)) { -it },
                exit = fadeOut(Motion.fade(200)) + slideOutVertically(Motion.tween(240)) { -it },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopPanel(
                    title = state.title,
                    cover = cover,
                    chapter = null,
                    progression = if (count > 1) state.page.toDouble() / (count - 1) else 0.0,
                    progressText = "${state.page + 1}/$count",
                    totalPositions = count,
                    marks = emptyList(),
                    onBack = { host.finish() },
                    onListen = { },
                    onSearch = { if (doc?.textSupported == true) state.sheet = ReaderSheet.Search else host.searchUnavailable() },
                    onToc = { state.sheet = ReaderSheet.Toc },
                    onSeek = { f -> state.jump = (f * (count - 1)).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0)) },
                    onChapter = { dir -> state.jump = (state.page + dir).coerceIn(0, (count - 1).coerceAtLeast(0)) },
                    bookmarked = state.page in bookmarkedPages,
                    onBookmark = { host.toggleBookmark(marks) },
                    onJump = { state.goTo = true },
                )
            }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            AnimatedVisibility(
                visible = state.ui && doc != null && state.sheet == null,
                enter = fadeIn(Motion.fade(220)) + slideInVertically(Motion.tween(260)) { it / 2 },
                exit = fadeOut(Motion.fade(180)) + slideOutVertically(Motion.tween(220)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val supported = listOf(QuickButton.Toc, QuickButton.Theme, QuickButton.Scroll, QuickButton.Screen, QuickButton.Notes, QuickButton.Bookmarks, QuickButton.Search)
                val buttons = remember(settings.quickButtons) {
                    val chosen = settings.quickButtons.mapNotNull { QuickButton.of(it) }.filter { it in supported }
                    (chosen + listOf(QuickButton.Toc, QuickButton.Notes, QuickButton.Bookmarks, QuickButton.Theme).filter { it !in chosen }).take(maxOf(4, chosen.size))
                }
                BottomPanel(
                    buttons = buttons,
                    onButton = { button ->
                        when (button) {
                            QuickButton.Toc -> state.sheet = ReaderSheet.Toc
                            QuickButton.Notes -> state.sheet = ReaderSheet.Notes
                            QuickButton.Bookmarks -> state.sheet = ReaderSheet.Bookmarks
                            QuickButton.Theme -> state.sheet = ReaderSheet.Theme
                            QuickButton.Screen -> state.sheet = ReaderSheet.Screen
                            QuickButton.Scroll -> state.togglePaged()
                            QuickButton.Search -> if (doc?.textSupported == true) state.sheet = ReaderSheet.Search else host.searchUnavailable()
                            else -> Unit
                        }
                    },
                    onOptions = { state.sheet = ReaderSheet.Options },
                )
            }
        }

        var chip by remember { mutableStateOf(false) }
        LaunchedEffect(state.page, state.ui) {
            if (state.ui || doc == null) {
                chip = false
                return@LaunchedEffect
            }
            chip = true
            delay(1600)
            chip = false
        }
        AnimatedVisibility(
            visible = chip && state.sheet == null && state.selection == null && state.translate == null && state.search.current < 0,
            enter = fadeIn(Motion.fade(160)),
            exit = fadeOut(Motion.fade(400)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 14.dp),
        ) {
            Text(
                "${state.page + 1} / $count",
                color = AppColors.Text,
                fontSize = 12.sp,
                modifier = Modifier.panelSurface(appCorner(12), 4.dp).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }

        AnimatedVisibility(
            visible = state.search.current >= 0 && state.search.results.isNotEmpty() && state.sheet == null && state.selection == null && !state.ui,
            enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(240)) { it / 2 },
            exit = fadeOut(Motion.fade(160)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 16.dp),
        ) {
            SearchNavBar(host.searchActions, onReopen = { state.sheet = ReaderSheet.Search }, modifier = Modifier)
        }

        AnimatedVisibility(
            visible = state.askFinish && state.sheet == null,
            enter = fadeIn(Motion.fade(220)) + scaleIn(Motion.tween(280), initialScale = 0.9f),
            exit = fadeOut(Motion.fade(180)) + scaleOut(Motion.tween(200), targetScale = 0.9f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + if (state.ui) 150.dp else 48.dp),
        ) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .panelSurface(appCorner(20))
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AppIcons.Flag, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
                Text("Книга прочитана?", color = AppColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                RoundIconButton(AppIcons.Check, "Прочитано", filled = true, sound = FeedbackSound.Success) { host.markFinished() }
                RoundIconButton(AppIcons.Close, "Позже", sound = FeedbackSound.Close) { state.askFinish = false }
            }
        }

        AnimatedVisibility(
            visible = state.translate != null && state.sheet == null,
            enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(260)) { it / 3 },
            exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(200)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 16.dp),
        ) {
            val last = remember { arrayOf<TranslateInfo?>(null) }
            state.translate?.let { last[0] = it }
            last[0]?.let { TranslatePopup(it, host.translateActions, Modifier.pointerInput(Unit) { detectTapGestures { } }) }
        }

        AnimatedVisibility(
            visible = state.selection != null && state.selectionBounds != null && state.sheet == null,
            enter = fadeIn(Motion.fade(220)),
            exit = fadeOut(Motion.fade(160)),
        ) {
            val last = remember { arrayOf<Pair<Float, Float>?>(null) }
            state.selectionBounds?.let { last[0] = it }
            val bounds = last[0] ?: return@AnimatedVisibility
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val menuHeight = with(density) { 58.dp.toPx() }
                val gap = with(density) { 14.dp.toPx() }
                val minY = with(density) { (top + 12.dp).toPx() }
                val maxY = with(density) { maxHeight.toPx() } - menuHeight - with(density) { (bottom + 24.dp).toPx() }
                val above = bounds.first - menuHeight - gap
                val below = bounds.second + gap + with(density) { 22.dp.toPx() }
                val y = when {
                    above >= minY -> above
                    below <= maxY -> below
                    else -> maxY
                }.coerceIn(minY, max(minY, maxY))
                val supported = setOf(SelectionButton.Note, SelectionButton.Marker, SelectionButton.Translate, SelectionButton.Dictionary, SelectionButton.Quote, SelectionButton.Copy)
                val layout = remember(settings.selectionButtons, settings.selectionMore) {
                    val (m, o) = settings.selectionLayout()
                    val main = m.filter { it in supported }
                    val more = (o.filter { it in supported } + supported.filter { it !in m && it !in o }).distinct()
                    main to more
                }
                SelectionMenu(
                    markerColor = settings.markerColor,
                    palette = settings.noteColors,
                    actions = host.noteActions,
                    main = layout.first,
                    more = layout.second,
                    modifier = Modifier.fillMaxWidth().offset { IntOffset(0, y.roundToInt()) },
                )
            }
        }

        AnimatedVisibility(
            visible = state.sheet != null && doc != null,
            enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(280)) { it / 3 },
            exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(220)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom),
        ) {
            val last = remember { arrayOf(ReaderSheet.Toc) }
            state.sheet?.let { last[0] = it }
            SheetFrame {
                PdfSheet(host, state, last[0], notes, marks, settings)
            }
        }
    }

    val doc = state.doc
    if (state.grid && doc != null) PdfGridSheet(doc, state, bookmarkedPages, marksByPage.keys)
    state.nameMark?.let { mark ->
        key(mark.id) {
            TextInputSheet(
                "Закладка",
                mark.label,
                "Название закладки",
                onDismiss = { state.nameMark = null },
                icon = AppIcons.Bookmark,
                maxLength = 80,
            ) { text -> host.bookmarkActions.onName(mark.id, text.trim()) }
        }
    }
    if (state.goTo && doc != null) {
        TextInputSheet(
            "Страница",
            "",
            "1–${doc.count}",
            onDismiss = { state.goTo = false },
            icon = AppIcons.Pages,
            number = true,
            maxLength = 6,
        ) { text ->
            text.trim().toIntOrNull()?.let { state.jump = (it - 1).coerceIn(0, doc.count - 1) }
        }
    }
}

@Composable
private fun PdfSheet(
    host: PdfReaderActivity,
    state: PdfUiState,
    sheet: ReaderSheet,
    notes: List<Note>,
    marks: List<Bookmark>,
    settings: ReaderSettings,
) {
    val app = host.application as App
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(appCorner(2))
                .background(AppColors.Line),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val back = when {
                sheet == ReaderSheet.NoteEditor && state.editor?.fromList == true -> ReaderSheet.Notes
                sheet in PdfOptionPages -> ReaderSheet.Options
                else -> null
            }
            if (back != null) {
                RoundIconButton(AppIcons.Back, "Назад", sound = FeedbackSound.Close) { state.sheet = back }
            } else if (sheet !in PdfNav) {
                Spacer(Modifier.size(44.dp))
            }
            if (sheet in PdfNav) {
                NavTabs(sheet, onSelect = { state.sheet = it }, modifier = Modifier.weight(1f).padding(end = 6.dp), sheets = PdfNav)
            } else {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    HeaderLabel(
                        when {
                            sheet == ReaderSheet.NoteEditor && state.editor?.id == null -> "Новая заметка"
                            sheet == ReaderSheet.Theme -> "Вид"
                            else -> sheet.title
                        },
                    )
                }
            }
            RoundIconButton(AppIcons.Close, "Закрыть", filled = true, sound = FeedbackSound.Close) { state.sheet = null }
        }
        SheetDivider()
        AnimatedContent(
            targetState = sheet,
            transitionSpec = {
                val dir = if (PdfNav.indexOf(targetState) >= PdfNav.indexOf(initialState)) 1 else -1
                (slideInHorizontally(Motion.tween(280)) { dir * it / 4 } + fadeIn(Motion.fade(220)))
                    .togetherWith(slideOutHorizontally(Motion.tween(240)) { -dir * it / 4 } + fadeOut(Motion.fade(160)))
                    .using(SizeTransform(clip = true))
            },
            modifier = Modifier.weight(1f, fill = false),
            label = "pdf-sheet",
        ) { tab ->
            when (tab) {
                ReaderSheet.Notes -> BookNotesList(
                    notes = notes,
                    onOpen = host.noteActions.onGoTo,
                    onEdit = { host.editNote(it, fromList = true) },
                )
                ReaderSheet.Bookmarks -> BookmarkList(marks, host.bookmarkActions)
                ReaderSheet.Search -> SearchContent(host.searchActions, Modifier)
                ReaderSheet.Quote -> state.quote?.let { QuoteContent(it, host.quoteActions) }
                ReaderSheet.NoteEditor -> state.editor?.let { draft ->
                    NoteEditor(
                        draft = draft,
                        palette = settings.noteColors,
                        onPaletteChange = { colors -> app.readerSettings.update { it.copy(noteColors = colors) } },
                        onSave = host.noteActions.onSave,
                        onDelete = host.noteActions.onDelete,
                        onCancel = { state.sheet = if (draft.fromList) ReaderSheet.Notes else null },
                    )
                }
                ReaderSheet.Options, ReaderSheet.Theme, ReaderSheet.Screen, ReaderSheet.Sound, ReaderSheet.Atmosphere -> {
                    val viewport = remember(tab) { RevealViewport() }
                    CompositionLocalProvider(LocalRevealViewport provides viewport) {
                        Column(
                            Modifier.revealViewport(viewport).revealScroll(),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            val change: ((ReaderSettings) -> ReaderSettings) -> Unit = { t -> app.readerSettings.update(t) }
                            when (tab) {
                                ReaderSheet.Options -> OptionTiles(
                                    listOf(
                                        OptionTile("Вид", "Текст, лента, цвет", AppIcons.Pages, 0xFFA78BFA, 0xFF7653E0) { state.sheet = ReaderSheet.Theme },
                                        OptionTile("Экран", "Яркость, поворот", AppIcons.Screen, 0xFFF7D774, 0xFFD9A92B, dark = true) { state.sheet = ReaderSheet.Screen },
                                        OptionTile("Звук", "Кнопки, вибрация", AppIcons.Sound, 0xFFF6A26B, 0xFFD9692B) { state.sheet = ReaderSheet.Sound },
                                        OptionTile("Атмосфера", "Дождь, камин", AppIcons.Fire, 0xFFFCA5A5, 0xFFB45309) { state.sheet = ReaderSheet.Atmosphere },
                                        OptionTile("Страницы", "Сетка всех страниц", AppIcons.Grid, 0xFF5EEAD4, 0xFF1FA392, dark = true) {
                                            state.sheet = null
                                            state.grid = true
                                        },
                                    ),
                                )
                                ReaderSheet.Theme -> OptionFrame { PdfViewOptions(host, state) }
                                ReaderSheet.Screen -> OptionFrame { ScreenOptions(settings, change) }
                                ReaderSheet.Sound -> OptionFrame { SoundOptions(settings, change) }
                                else -> AmbienceOptions(title = "Звуки фона")
                            }
                        }
                    }
                }
                else -> PdfToc(state)
            }
        }
    }
}

@Composable
private fun PdfToc(state: PdfUiState) {
    val outline = state.outline
    if (outline.isEmpty()) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("В этом PDF нет оглавления", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Перейти можно по сетке страниц или номеру страницы.", color = AppColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
        return
    }
    val current = remember(outline, state.page) {
        var best = -1
        outline.forEachIndexed { i, e -> if (e.page <= state.page && (best < 0 || e.page >= outline[best].page)) best = i }
        best
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    LazyColumn(state = list) {
        itemsIndexed(outline) { index, item ->
            Column {
                if (index > 0) ListDivider()
                Reveal {
                    val shape = appCorner(12)
                    val active = index == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) {
                                state.sheet = null
                                state.jump = item.page
                            }
                            .padding(start = (8 + item.depth.coerceAtMost(5) * 16).dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            item.title,
                            color = when {
                                active -> AppColors.Accent
                                item.depth == 0 -> AppColors.Text
                                else -> AppColors.Muted
                            },
                            fontSize = if (item.depth == 0) 15.sp else 14.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${item.page + 1}", color = AppColors.Faint, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfContent(
    host: PdfReaderActivity,
    doc: PdfDoc,
    state: PdfUiState,
    marksByPage: Map<Int, List<PdfMark>>,
    bookmarkedPages: Set<Int>,
    settings: ReaderSettings,
) {
    val zoom = remember { ZoomState() }
    val feedback = LocalFeedback.current
    val current by rememberUpdatedState(settings)
    val registry = remember { PageRegistry() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val paged = state.paged
    val marks by rememberUpdatedState(marksByPage)
    LaunchedEffect(paged) { zoom.reset() }
    var listState by remember { mutableStateOf<LazyListState?>(null) }
    var fling by remember { mutableStateOf<Job?>(null) }
    val hi by remember { derivedStateOf { zoom.scale >= 1.6f } }
    val zoomed by remember { derivedStateOf { zoom.zoomed } }
    val searchHit = state.search.results.getOrNull(state.search.current)
    val searchPage = searchHit?.locator?.locations?.position?.minus(1)
    val searchRects = remember(searchHit) { decodeRects(searchHit?.locator?.locations?.otherLocations?.get(PDF_RECTS)) }

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(state.selection, zoom.scale, zoom.ox + zoom.oy * 7f) }.collect { (sel, _, _) ->
            if (sel == null || sel.rects.isEmpty()) {
                state.selectionBounds = null
            } else {
                withFrameMillis { }
                val union = RectF(sel.rects[0])
                sel.rects.forEach { union.union(it) }
                state.selectionBounds = registry.windowRect(doc, sel.page, union)
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { registry.root = it }
            .pointerInput(paged) {
                detectTapGestures(
                    onDoubleTap = { pos -> scope.launch { zoom.animateTo(if (zoom.zoomed) 1f else 2.5f, pos) } },
                    onLongPress = { pos ->
                        val hit = registry.hit(doc, pos) ?: return@detectTapGestures
                        if (host.beginSelection(hit.page, hit.x, hit.y)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onTap = { pos ->
                        if (state.selection != null) {
                            host.endSelection()
                            return@detectTapGestures
                        }
                        if (state.translate != null) {
                            host.translateActions.onClose()
                            return@detectTapGestures
                        }
                        val hit = registry.hit(doc, pos)
                        if (hit != null) {
                            val note = marks[hit.page]?.firstOrNull { m -> m.rects.any { it.contains(hit.x, hit.y) } }
                            if (note != null) {
                                host.editNote(note.note, fromList = false)
                                return@detectTapGestures
                            }
                            val link = doc.cachedLinks(hit.page)?.firstOrNull { l -> l.rects.any { it.contains(hit.x, hit.y) } }
                            if (link != null && !state.ui) {
                                host.openLink(link)
                                return@detectTapGestures
                            }
                        }
                        val w = size.width
                        val s = current
                        val zone = if (s.edgeTaps) s.edgeZone.toFloat() else 0f
                        val edge = when {
                            state.ui || zoom.zoomed || zone <= 0f -> 0
                            pos.x < w * zone -> if (s.leftTapForward) 1 else -1
                            pos.x > w * (1 - zone) -> 1
                            else -> 0
                        }
                        when {
                            edge != 0 && paged -> state.jump = (state.page + edge).coerceIn(0, doc.count - 1)
                            edge != 0 -> listState?.let { ls -> scope.launch { ls.animateScrollBy(edge * size.height * 0.85f, tween(Motion.ms(320).coerceAtLeast(1))) } }
                            state.ui -> state.ui = false
                            else -> {
                                state.ui = true
                                feedback?.play(FeedbackSound.Open)
                            }
                        }
                    },
                )
            }
            .pointerInput(paged) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fling?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var panning = false
                    var acc = Offset.Zero
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val factor = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (factor != 1f) zoom.zoom(factor, event.calculateCentroid(useCurrent = true))
                            val left = zoom.pan(pan.x, pan.y)
                            if (!paged && left != 0f) listState?.dispatchRawDelta(-left / zoom.scale)
                            event.changes.forEach { it.consume() }
                            panning = false
                            acc = Offset(Float.MAX_VALUE, 0f)
                        } else if (pressed == 1 && zoom.zoomed && state.selection == null) {
                            val ch = event.changes.first { it.pressed }
                            val d = ch.positionChange()
                            if (!panning) {
                                acc += d
                                if (acc.getDistance() > viewConfiguration.touchSlop) panning = true
                            }
                            if (panning) {
                                val left = zoom.pan(d.x, d.y)
                                if (!paged && left != 0f) listState?.dispatchRawDelta(-left / zoom.scale)
                                ch.consume()
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    val list = listState
                    if (panning && !paged && list != null) {
                        val vy = tracker.calculateVelocity().y
                        if (abs(vy) > 300f) {
                            fling = scope.launch {
                                list.scroll {
                                    var prev = 0f
                                    AnimationState(0f, vy).animateDecay(exponentialDecay()) {
                                        val d = value - prev
                                        prev = value
                                        val left = zoom.pan(0f, d)
                                        if (left != 0f) {
                                            val used = scrollBy(-left / zoom.scale)
                                            if (used == 0f) cancelAnimation()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
    ) {
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        zoom.w = maxW.toFloat()
        zoom.h = maxH.toFloat()
        val gap = 8.dp
        val gapPx = with(density) { gap.toPx() }

        @Composable
        fun page(i: Int, width: Int, modifier: Modifier) {
            PdfPageView(
                host = host,
                doc = doc,
                index = i,
                width = width,
                filter = state.filter,
                marks = marks[i].orEmpty(),
                bookmarked = i in bookmarkedPages,
                search = if (i == searchPage) searchRects else emptyList(),
                selection = state.selection?.takeIf { it.page == i },
                registry = registry,
                modifier = modifier,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.ox
                    translationY = zoom.oy
                },
        ) {
            key(paged) {
                if (!paged) {
                    val ls = rememberLazyListState(initialFirstVisibleItemIndex = state.page)
                    LaunchedEffect(ls) { listState = ls }
                    val pageW = (maxW - 2 * gapPx).roundToInt().coerceAtLeast(1)
                    val renderW = renderWidth(pageW, hi)
                    LaunchedEffect(ls) {
                        snapshotFlow {
                            val info = ls.layoutInfo
                            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                            info.visibleItemsInfo.firstOrNull { it.offset <= center && it.offset + it.size >= center }?.index ?: ls.firstVisibleItemIndex
                        }.collect { state.page = it }
                    }
                    LaunchedEffect(ls) {
                        snapshotFlow { ls.isScrollInProgress }.collect { if (it && state.selection != null) host.endSelection() }
                    }
                    LaunchedEffect(ls) {
                        snapshotFlow { state.jump }.collect { target ->
                            if (target != null) {
                                state.jump = null
                                if (abs(target - state.page) <= 1 && Motion.enabled) ls.animateScrollToItem(target) else ls.scrollToItem(target)
                            }
                        }
                    }
                    LazyColumn(
                        state = ls,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = gap, end = gap, top = top + 12.dp, bottom = bottom + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(doc.count, key = { it }) { i ->
                            page(i, renderW, Modifier.fillMaxWidth().aspectRatio(doc.aspect(i)))
                        }
                    }
                } else {
                    val pager = rememberPagerState(initialPage = state.page) { doc.count }
                    LaunchedEffect(pager) { listState = null }
                    LaunchedEffect(pager) {
                        snapshotFlow { pager.currentPage }.collect {
                            state.page = it
                            if (zoom.zoomed) zoom.reset()
                        }
                    }
                    LaunchedEffect(pager) {
                        snapshotFlow { pager.isScrollInProgress }.collect { if (it && state.selection != null) host.endSelection() }
                    }
                    LaunchedEffect(pager) {
                        snapshotFlow { state.jump }.collect { target ->
                            if (target != null) {
                                state.jump = null
                                if (abs(target - pager.currentPage) <= 2 && Motion.enabled) pager.animateScrollToPage(target) else pager.scrollToPage(target)
                            }
                        }
                    }
                    val availW = maxW - 2 * gapPx
                    val availH = maxH - with(density) { (top + bottom + 24.dp).toPx() }
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !zoomed && state.selection == null,
                        beyondViewportPageCount = 1,
                        key = { it },
                    ) { i ->
                        val a = doc.aspect(i)
                        val fitW = if (availW / availH.coerceAtLeast(1f) > a) availH * a else availW
                        val fitH = fitW / a
                        Box(Modifier.fillMaxSize().padding(top = top + 12.dp, bottom = bottom + 12.dp), contentAlignment = Alignment.Center) {
                            page(
                                i,
                                renderWidth(fitW.roundToInt().coerceAtLeast(1), hi && i == pager.currentPage),
                                Modifier.size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() }),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun renderWidth(px: Int, hi: Boolean): Int {
    val base = if (hi) (px * 2).coerceAtMost(2600) else px.coerceAtMost(1600)
    return ((base + 63) / 64) * 64
}

@Composable
private fun PdfPageView(
    host: PdfReaderActivity,
    doc: PdfDoc,
    index: Int,
    width: Int,
    filter: PdfFilter,
    marks: List<PdfMark>,
    bookmarked: Boolean,
    search: List<RectF>,
    selection: PdfSel?,
    registry: PageRegistry,
    modifier: Modifier,
) {
    var shown by remember(index) { mutableStateOf(doc.cached(index, width)) }
    LaunchedEffect(index, width) {
        doc.render(index, width)?.let { shown = it }
        doc.links(index)
    }
    DisposableEffect(index) {
        onDispose { registry.pages.remove(index) }
    }
    val accent = AppColors.Accent
    val night = filter == PdfFilter.Night
    Box(
        modifier
            .onGloballyPositioned { registry.pages[index] = it }
            .shadow(3.dp, RectangleShape, clip = false)
            .background(filter.paper)
            .border(0.5.dp, AppColors.PanelBorder),
    ) {
        Crossfade(shown != null, animationSpec = Motion.fade(220), label = "pdf-page") { ready ->
            val bmp = shown
            if (ready && bmp != null) {
                Image(bmp, null, contentScale = ContentScale.FillBounds, colorFilter = filter.filter, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(22.dp), AppColors.Faint) }
            }
        }
        val pw = doc.pageWidth(index).toFloat()
        val ph = doc.pageHeight(index).toFloat()
        val highlighted = marks.filter { it.rects.isNotEmpty() }
        if (highlighted.isNotEmpty() || search.isNotEmpty() || selection != null) {
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width / pw
                val sy = size.height / ph
                fun box(r: RectF, color: Color, blend: BlendMode) {
                    drawRect(color, Offset(r.left * sx, r.top * sy), Size((r.right - r.left) * sx, (r.bottom - r.top) * sy), blendMode = blend)
                }
                val blend = if (night) BlendMode.Screen else BlendMode.Multiply
                highlighted.forEach { m ->
                    val c = Color(m.note.color)
                    m.rects.forEach { box(it, c.copy(alpha = if (night) 0.45f else 0.55f), blend) }
                    if (m.note.note.isNotBlank()) {
                        val last = m.rects.last()
                        drawCircle(c, radius = 3.5f * density, center = Offset(last.right * sx + 4f * density, last.top * sy))
                    }
                }
                search.forEach { box(it, accent.copy(alpha = 0.45f), blend) }
                selection?.rects?.forEach { box(it, accent.copy(alpha = 0.35f), BlendMode.SrcOver) }
            }
        }
        val pageNotes = marks.filter { it.rects.isEmpty() }
        if (pageNotes.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.TopStart).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pageNotes.take(4).forEach { m ->
                    Box(
                        Modifier
                            .size(26.dp)
                            .liveClick(appCorner(8), sound = FeedbackSound.Open, pressedScale = 0.88f) { host.editNote(m.note, fromList = false) }
                            .clip(appCorner(8))
                            .background(Color(m.note.color))
                            .border(1.dp, Color(0x33000000), appCorner(8)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Note, null, tint = Color(0xCC1A1A1A), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = bookmarked,
            enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(260)) { -it },
            exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(200)) { -it },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp),
        ) {
            PageRibbon()
        }
        if (selection != null && selection.rects.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wpx = constraints.maxWidth.toFloat()
                val hpx = constraints.maxHeight.toFloat()
                val sx = wpx / pw
                val sy = hpx / ph
                val first = selection.rects.first()
                val last = selection.rects.last()
                SelectionHandle(
                    anchor = Offset(first.left * sx, first.bottom * sy),
                    start = true,
                    onMove = { p -> host.moveHandle(true, p.x / sx, (p.y / sy - first.height() / 2f).coerceAtLeast(0f)) },
                )
                SelectionHandle(
                    anchor = Offset(last.right * sx, last.bottom * sy),
                    start = false,
                    onMove = { p -> host.moveHandle(false, p.x / sx, (p.y / sy - last.height() / 2f).coerceAtLeast(0f)) },
                )
            }
        }
    }
}

@Composable
private fun SelectionHandle(anchor: Offset, start: Boolean, onMove: (Offset) -> Unit) {
    val size = 22.dp
    val px = with(LocalDensity.current) { size.toPx() }
    val current by rememberUpdatedState(anchor)
    val move by rememberUpdatedState(onMove)
    var drag by remember { mutableStateOf<Offset?>(null) }
    val shown = drag ?: anchor
    val shape = if (start) {
        RoundedCornerShape(50, 0, 50, 50)
    } else {
        RoundedCornerShape(0, 50, 50, 50)
    }
    Box(
        Modifier
            .offset { IntOffset((shown.x - if (start) px else 0f).roundToInt(), shown.y.roundToInt()) }
            .size(size)
            .pointerInput(start) {
                detectDragGestures(
                    onDragStart = { drag = current },
                    onDragEnd = { drag = null },
                    onDragCancel = { drag = null },
                ) { change, amount ->
                    change.consume()
                    val next = (drag ?: current) + amount
                    drag = next
                    move(next)
                }
            }
            .shadow(3.dp, shape)
            .clip(shape)
            .background(AppColors.Accent),
    )
}

@Composable
private fun PdfGridSheet(doc: PdfDoc, state: PdfUiState, bookmarked: Set<Int>, noted: Set<Int>) {
    AppSheet(onDismiss = { state.grid = false }, title = "Страницы") {
        val close = sheetClose { state.grid = false }
        val height = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
        val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (state.page - 3).coerceAtLeast(0))
        val thumbW = with(LocalDensity.current) { 120.dp.toPx() }.roundToInt().let { ((it + 63) / 64) * 64 }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = grid,
            modifier = Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(doc.count, key = { it }) { i ->
                val current = i == state.page
                val shape = appCorner(8)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .liveClick(shape, sound = FeedbackSound.Tap, pressedScale = 0.94f) {
                                state.jump = i
                                close()
                            }
                            .clip(shape)
                            .border(if (current) 2.dp else 1.dp, if (current) AppColors.Accent else AppColors.PanelBorder, shape)
                            .background(AppColors.Field)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val a = doc.aspect(i)
                        PdfThumb(doc, i, thumbW, state.filter, Modifier.aspectRatio(a, matchHeightConstraintsFirst = a < 0.72f))
                        if (i in bookmarked) PageRibbon(Modifier.align(Alignment.TopEnd).padding(end = 4.dp).size(10.dp, 18.dp))
                        if (i in noted) {
                            Box(
                                Modifier.align(Alignment.TopStart).size(8.dp).clip(RoundedCornerShape(50)).background(AppColors.Accent),
                            )
                        }
                    }
                    Text("${i + 1}", color = if (current) AppColors.Accent else AppColors.Muted, fontSize = 11.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun PdfThumb(doc: PdfDoc, index: Int, width: Int, filter: PdfFilter, modifier: Modifier) {
    var shown by remember(index) { mutableStateOf(doc.cached(index, width)) }
    LaunchedEffect(index, width) { doc.render(index, width)?.let { shown = it } }
    Box(modifier.background(filter.paper)) {
        Crossfade(shown, animationSpec = Motion.fade(200), label = "pdf-thumb") { bmp ->
            if (bmp != null) {
                Image(bmp, null, contentScale = ContentScale.FillBounds, colorFilter = filter.filter, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(16.dp), AppColors.Faint) }
            }
        }
    }
}
