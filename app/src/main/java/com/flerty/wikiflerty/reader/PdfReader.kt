package com.flerty.wikiflerty.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.LruCache
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.ReadStatus
import com.flerty.wikiflerty.ui.AppBackground
import com.flerty.wikiflerty.ui.AppButton
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.AppSheet
import com.flerty.wikiflerty.ui.AppStyle
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.KitSlider
import com.flerty.wikiflerty.ui.KitSpinner
import com.flerty.wikiflerty.ui.LocalFeedback
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.TextInputSheet
import com.flerty.wikiflerty.ui.WikiFlertyTheme
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import com.flerty.wikiflerty.ui.sheetClose
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

class PdfDoc private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val widths: IntArray,
    private val heights: IntArray,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val thread = executor.asCoroutineDispatcher()
    @Volatile private var closed = false

    val count: Int get() = widths.size

    fun aspect(i: Int): Float = widths[i].toFloat() / heights[i].coerceAtLeast(1)

    private val cache = object : LruCache<Long, ImageBitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 5).toInt().coerceAtLeast(16 * 1024)) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    private fun key(i: Int, w: Int) = (i.toLong() shl 20) or w.toLong()

    fun cached(i: Int, w: Int): ImageBitmap? = cache.get(key(i, w))

    suspend fun render(i: Int, w: Int): ImageBitmap? {
        cached(i, w)?.let { return it }
        if (closed || i !in 0 until count) return null
        return runCatching {
            withContext(thread) {
                if (closed) return@withContext null
                cached(i, w) ?: run {
                    val page = renderer.openPage(i)
                    try {
                        val h = (w.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, w * 4)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp.asImageBitmap().also { cache.put(key(i, w), it) }
                    } finally {
                        page.close()
                    }
                }
            }
        }.getOrNull()
    }

    fun close() {
        if (closed) return
        closed = true
        executor.execute {
            runCatching { renderer.close() }
            runCatching { fd.close() }
            cache.evictAll()
        }
        executor.shutdown()
    }

    companion object {
        suspend fun open(file: File, sizesFile: File): PdfDoc = withContext(Dispatchers.IO) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(fd)
            } catch (e: Throwable) {
                runCatching { fd.close() }
                throw e
            }
            val n = renderer.pageCount
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
                    val p = renderer.openPage(i)
                    w[i] = p.width.coerceAtLeast(1)
                    h[i] = p.height.coerceAtLeast(1)
                    p.close()
                }
                runCatching {
                    sizesFile.parentFile?.mkdirs()
                    sizesFile.writeText(JSONObject().put("w", JSONArray(w.toList())).put("h", JSONArray(h.toList())).toString())
                }
                w to h
            }
            PdfDoc(fd, renderer, widths, heights)
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

class PdfUiState(context: Context, val title: String, start: Int, val finished: Boolean) {
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
    var paged by mutableStateOf(prefs.getBoolean("paged", false))
        private set
    var filter by mutableStateOf(PdfFilter.entries.getOrElse(prefs.getInt("filter", 0)) { PdfFilter.Day })
        private set

    fun togglePaged() {
        paged = !paged
        prefs.edit().putBoolean("paged", paged).apply()
    }

    fun nextFilter() {
        filter = PdfFilter.entries[(filter.ordinal + 1) % PdfFilter.entries.size]
        prefs.edit().putInt("filter", filter.ordinal).apply()
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

class PdfReaderActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BOOK = "book"
    }

    private val app get() = application as App
    private lateinit var bookId: String
    private lateinit var state: PdfUiState
    private var visible = false
    private var lastActivity = 0L
    private var pagesSinceTrack = 0
    private var lastPage = -1
    private var saveJob: Job? = null
    private var pendingPage = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val book = intent.getStringExtra(EXTRA_BOOK)?.let { id -> app.library.books.value.firstOrNull { it.id == id } }
        if (book == null) {
            finish()
            return
        }
        bookId = book.id
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val settings = app.readerSettings.settings.value
        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (settings.brightness >= 0) {
            window.attributes = window.attributes.apply { screenBrightness = settings.brightness.toFloat().coerceIn(0.01f, 1f) }
        }
        val start = startPage(book)
        lastPage = start
        state = PdfUiState(this, book.title, start, book.status == ReadStatus.Finished)
        app.appScope.launch { app.library.markOpened(book.id) }
        val file = app.library.bookFile(book)
        lifecycleScope.launch {
            runCatching { PdfDoc.open(file, File(File(filesDir, "pdfmeta"), "${book.id}.json")) }
                .onSuccess { doc ->
                    if (isDestroyed) doc.close() else state.doc = doc
                    if (book.positions != doc.count) app.library.setPositions(book.id, doc.count)
                }
                .onFailure { state.error = if (it is SecurityException) "PDF защищён паролем" else "Не удалось открыть PDF" }
        }
        setContent {
            CompositionLocalProvider(LocalFeedback provides app.feedback) {
                WikiFlertyTheme {
                    PdfReaderScreen(
                        state = state,
                        onBack = { finish() },
                        onPage = ::onPage,
                        onBars = ::applyBars,
                        onFinished = {
                            state.askFinish = false
                            app.appScope.launch { app.library.setStatus(bookId, ReadStatus.Finished) }
                        },
                    )
                }
            }
        }
    }

    private fun startPage(book: com.flerty.wikiflerty.data.Book): Int {
        val fromLocator = book.locatorJson?.let { runCatching { Locator.fromJSON(JSONObject(it))?.locations?.position }.getOrNull() }
        if (fromLocator != null && fromLocator > 0) return fromLocator - 1
        val total = book.positions
        return if (total > 1) (book.progress * (total - 1)).roundToInt().coerceIn(0, total - 1) else 0
    }

    private fun applyBars(show: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = AppStyle.light
            isAppearanceLightNavigationBars = AppStyle.light
            if (show) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun onPage(page: Int) {
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

    private fun flushProgress() {
        saveJob?.cancel()
        saveJob = null
        val page = pendingPage
        if (page < 0) return
        pendingPage = -1
        val total = state.doc?.count ?: return
        val progression = if (total <= 1) 1.0 else page.toDouble() / (total - 1)
        val href = Url("page") ?: return
        val locator = Locator(
            href = href,
            mediaType = MediaType.PDF,
            title = state.title,
            locations = Locator.Locations(position = page + 1, progression = progression, totalProgression = progression),
        )
        val id = bookId
        app.appScope.launch { app.library.saveProgress(id, locator) }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volume = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volume && app.readerSettings.settings.value.volumeKeys && ::state.isInitialized && state.doc != null) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfReaderScreen(
    state: PdfUiState,
    onBack: () -> Unit,
    onPage: (Int) -> Unit,
    onBars: (Boolean) -> Unit,
    onFinished: () -> Unit,
) {
    LaunchedEffect(state.ui) { onBars(state.ui) }
    LaunchedEffect(state) { snapshotFlow { state.page }.collect { onPage(it) } }
    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        AppBackground()
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
                    AppButton("Назад", primary = true, onClick = onBack)
                }
                else -> state.doc?.let { PdfContent(it, state) }
            }
        }

        val top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
        val count = doc?.count ?: 0

        AnimatedVisibility(
            visible = state.ui && doc != null,
            enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(260)) { -it / 2 },
            exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(220)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PdfTopPanel(state, count, top, onBack)
        }
        AnimatedVisibility(
            visible = state.ui && doc != null,
            enter = fadeIn(Motion.fade(200)) + slideInVertically(Motion.tween(260)) { it / 2 },
            exit = fadeOut(Motion.fade(160)) + slideOutVertically(Motion.tween(220)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PdfBottomPanel(state, count, bottom)
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
            visible = chip,
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
            visible = state.askFinish,
            enter = fadeIn(Motion.fade(220)) + scaleIn(Motion.tween(280), initialScale = 0.9f),
            exit = fadeOut(Motion.fade(180)) + scaleOut(Motion.tween(200), targetScale = 0.9f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + if (state.ui) 110.dp else 48.dp),
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
                RoundIconButton(AppIcons.Check, "Прочитано", filled = true, sound = FeedbackSound.Success, onClick = onFinished)
                RoundIconButton(AppIcons.Close, "Позже", sound = FeedbackSound.Close) { state.askFinish = false }
            }
        }
    }

    val doc = state.doc
    if (state.grid && doc != null) PdfGridSheet(doc, state)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfContent(doc: PdfDoc, state: PdfUiState) {
    val zoom = remember { ZoomState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val paged = state.paged
    LaunchedEffect(paged) { zoom.reset() }
    var listState by remember { mutableStateOf<LazyListState?>(null) }
    var fling by remember { mutableStateOf<Job?>(null) }
    val hi by remember { derivedStateOf { zoom.scale >= 1.6f } }
    val zoomed by remember { derivedStateOf { zoom.zoomed } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(paged) {
                detectTapGestures(
                    onDoubleTap = { pos -> scope.launch { zoom.animateTo(if (zoom.zoomed) 1f else 2.5f, pos) } },
                    onTap = { pos ->
                        val w = size.width
                        when {
                            paged && !zoom.zoomed && pos.x < w * 0.28f && !state.ui -> state.jump = (state.page - 1).coerceAtLeast(0)
                            paged && !zoom.zoomed && pos.x > w * 0.72f && !state.ui -> state.jump = (state.page + 1).coerceAtMost(doc.count - 1)
                            else -> state.ui = !state.ui
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
                        } else if (pressed == 1 && zoom.zoomed) {
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
        zoom.w = constraints.maxWidth.toFloat()
        zoom.h = constraints.maxHeight.toFloat()
        val gap = 8.dp
        val gapPx = with(density) { gap.toPx() }
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
                    val pageW = (constraints.maxWidth - 2 * gapPx).roundToInt().coerceAtLeast(1)
                    val renderW = renderWidth(pageW, hi)
                    LaunchedEffect(ls) {
                        snapshotFlow {
                            val info = ls.layoutInfo
                            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                            info.visibleItemsInfo.firstOrNull { it.offset <= center && it.offset + it.size >= center }?.index ?: ls.firstVisibleItemIndex
                        }.collect { state.page = it }
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
                            PdfPageView(doc, i, renderW, state.filter, Modifier.fillMaxWidth().aspectRatio(doc.aspect(i)))
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
                        snapshotFlow { state.jump }.collect { target ->
                            if (target != null) {
                                state.jump = null
                                if (abs(target - pager.currentPage) <= 2 && Motion.enabled) pager.animateScrollToPage(target) else pager.scrollToPage(target)
                            }
                        }
                    }
                    val availW = constraints.maxWidth - 2 * gapPx
                    val availH = constraints.maxHeight - with(density) { (top + bottom + 24.dp).toPx() }
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !zoomed,
                        beyondViewportPageCount = 1,
                        key = { it },
                    ) { i ->
                        val a = doc.aspect(i)
                        val fitW = if (availW / availH.coerceAtLeast(1f) > a) availH * a else availW
                        val fitH = fitW / a
                        Box(Modifier.fillMaxSize().padding(top = top + 12.dp, bottom = bottom + 12.dp), contentAlignment = Alignment.Center) {
                            PdfPageView(
                                doc,
                                i,
                                renderWidth(fitW.roundToInt().coerceAtLeast(1), hi && i == pager.currentPage),
                                state.filter,
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
private fun PdfPageView(doc: PdfDoc, index: Int, width: Int, filter: PdfFilter, modifier: Modifier) {
    var shown by remember(index) { mutableStateOf(doc.cached(index, width)) }
    LaunchedEffect(index, width) {
        doc.render(index, width)?.let { shown = it }
    }
    Box(
        modifier
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
    }
}

@Composable
private fun PdfTopPanel(state: PdfUiState, count: Int, top: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, top = top + 8.dp)
            .widthIn(max = PanelMaxWidth)
            .fillMaxWidth()
            .height(64.dp)
            .panelSurface(appCorner(22))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(AppIcons.Back, "Назад", sound = FeedbackSound.Close, onClick = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            MarqueeText(state.title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("PDF · $count стр.", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
        }
        Crossfade(state.paged, animationSpec = Motion.fade(180), label = "pdf-mode") { p ->
            RoundIconButton(if (p) AppIcons.Pages else AppIcons.Scroll, if (p) "Листание" else "Лента", sound = FeedbackSound.Toggle) { state.togglePaged() }
        }
        Crossfade(state.filter, animationSpec = Motion.fade(180), label = "pdf-filter") { f ->
            RoundIconButton(f.icon, "Цвет страниц", sound = FeedbackSound.Toggle) { state.nextFilter() }
        }
        RoundIconButton(AppIcons.Grid, "Страницы", sound = FeedbackSound.Open) { state.grid = true }
    }
}

@Composable
private fun PdfBottomPanel(state: PdfUiState, count: Int, bottom: androidx.compose.ui.unit.Dp) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag?.roundToInt() ?: state.page
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = bottom + 10.dp)
            .widthIn(max = PanelMaxWidth)
            .fillMaxWidth()
            .height(60.dp)
            .panelSurface(appCorner(22))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (count > 1) {
            KitSlider(
                value = drag ?: state.page.toFloat(),
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { state.jump = it.roundToInt().coerceIn(0, count - 1) }
                    drag = null
                },
                valueRange = 0f..(count - 1).toFloat(),
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(Modifier.weight(1f))
        }
        Text(
            "${shown + 1} / $count",
            color = if (drag != null) AppColors.Accent else AppColors.Text,
            fontSize = 13.sp,
            fontWeight = if (drag != null) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 64.dp)
                .liveClick(appCorner(10), sound = FeedbackSound.Open, pressedScale = 0.9f) { state.goTo = true }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PdfGridSheet(doc: PdfDoc, state: PdfUiState) {
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
                        PdfPageView(doc, i, thumbW, state.filter, Modifier.aspectRatio(a, matchHeightConstraintsFirst = a < 0.72f))
                    }
                    Text("${i + 1}", color = if (current) AppColors.Accent else AppColors.Muted, fontSize = 11.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
