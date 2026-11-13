package com.flerty.wikiflerty.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.LaunchedEffect
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import java.io.File
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun ratingText(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

@Composable
fun StarRating(value: Float, size: Dp, modifier: Modifier = Modifier, gap: Dp = 6.dp, onChange: ((Float) -> Unit)? = null) {
    val feedback = LocalFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val current by androidx.compose.runtime.rememberUpdatedState(value)
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    val input = if (onChange == null) Modifier else {
        fun pick(x: Float, width: Float) = (ceil(x / width * 10f) / 2f).coerceIn(0.5f, 5f)
        fun apply(v: Float) {
            if (v == current) return
            feedback?.play(FeedbackSound.Star, view, 0.82f + v * 0.08f)
            change?.invoke(v)
        }
        Modifier
            .pointerInput(Unit) { detectTapGestures { apply(pick(it.x, this.size.width.toFloat())) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> apply(pick(c.position.x, this.size.width.toFloat())) } }
    }
    Row(modifier.then(input), horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (i in 1..5) {
            val fill = (value - (i - 1)).coerceIn(0f, 1f)
            AnimatedStar(fill, size, i, interactive = onChange != null)
        }
    }
}

private class Spark(val angle: Float, val distance: Float, val radius: Float, val delay: Float)

@Composable
private fun AnimatedStar(fill: Float, size: Dp, index: Int, interactive: Boolean) {
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    val burst = remember { androidx.compose.animation.core.Animatable(1f) }
    val fade = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shown by androidx.compose.animation.core.animateFloatAsState(fill, Motion.tween(180), label = "star-fill")
    var previous by remember { mutableFloatStateOf(fill) }
    val sparks = remember(index) {
        val r = kotlin.random.Random(index * 97)
        List(9) { k -> Spark((k * 40f + r.nextFloat() * 22f) * (Math.PI.toFloat() / 180f), 0.75f + r.nextFloat() * 0.45f, 0.06f + r.nextFloat() * 0.05f, r.nextFloat() * 0.18f) }
    }
    LaunchedEffect(fill) {
        val grew = fill > previous
        val shrank = fill < previous
        previous = fill
        if (!interactive || !Motion.enabled) return@LaunchedEffect
        if (grew) {
            scope.launch {
                pop.snapTo(0.82f)
                pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.32f, stiffness = 520f))
            }
            if (fill >= 1f) scope.launch {
                burst.snapTo(0f)
                burst.animateTo(1f, androidx.compose.animation.core.tween(620, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
            }
        } else if (shrank) {
            scope.launch { burst.snapTo(1f) }
            scope.launch {
                pop.snapTo(0.9f)
                pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 700f))
            }
            scope.launch {
                fade.snapTo(0f)
                fade.animateTo(1f, androidx.compose.animation.core.tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
        }
    }
    val accent = AppColors.Accent
    val glow = StarYellow
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val p = burst.value
                if (p < 1f) {
                    val c = center
                    val base = this.size.minDimension
                    sparks.forEach { s ->
                        val t = ((p - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
                        if (t <= 0f) return@forEach
                        val d = base * (0.35f + s.distance * t)
                        val x = c.x + kotlin.math.cos(s.angle) * d
                        val y = c.y + kotlin.math.sin(s.angle) * d
                        val alpha = (1f - t) * (1f - t)
                        val r = base * s.radius * (1f - t * 0.6f)
                        drawCircle(glow.copy(alpha = alpha), r, androidx.compose.ui.geometry.Offset(x, y))
                        drawCircle(accent.copy(alpha = alpha * 0.5f), r * 1.9f, androidx.compose.ui.geometry.Offset(x, y))
                    }
                    drawCircle(glow.copy(alpha = (1f - p) * 0.22f), base * (0.45f + p * 0.35f), c)
                }
                val f = fade.value
                if (f < 1f) {
                    val base = this.size.minDimension
                    drawCircle(
                        accent.copy(alpha = (1f - f) * 0.45f),
                        base * (0.62f - f * 0.3f),
                        center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = base * 0.05f * (1f - f) + 1f),
                    )
                }
            }
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = (1f - pop.value) * 40f
            },
    ) {
        Icon(AppIcons.StarFilled, null, tint = AppColors.Line, modifier = Modifier.size(size))
        if (shown > 0f) {
            Icon(
                AppIcons.StarFilled,
                null,
                tint = AppColors.Accent,
                modifier = Modifier.size(size).drawWithContent { clipRect(right = this.size.width * shown) { this@drawWithContent.drawContent() } },
            )
        }
    }
}

@Composable
fun RatingBadge(rating: Float, modifier: Modifier = Modifier, small: Boolean = false) {
    Row(
        modifier
            .background(androidx.compose.ui.graphics.Color(0xD0100D0B), androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = if (small) 6.dp else 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(AppIcons.StarFilled, null, tint = AppColors.Accent, modifier = Modifier.size(if (small) 10.dp else 12.dp))
        Text(ratingText(rating), color = AppColors.Accent, fontSize = if (small) 10.sp else 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
fun RatingSheet(bookId: String, celebrate: Boolean, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val context = LocalContext.current
    val books by app.library.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId } ?: return
    val days by app.stats.days.collectAsStateWithLifecycle()
    val stats = remember(days, book.statsKey) { StatsCalc.book(days, book.statsKey) }
    val scope = rememberCoroutineScope()
    var rating by remember { mutableFloatStateOf(book.rating ?: 0f) }
    var review by remember { mutableStateOf(book.review) }
    var sharing by remember { mutableStateOf(false) }

    val feedback = LocalFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    var party by remember { mutableIntStateOf(0) }
    val titlePop = remember { androidx.compose.animation.core.Animatable(if (celebrate && Motion.enabled) 0.6f else 1f) }
    LaunchedEffect(Unit) {
        if (!celebrate) return@LaunchedEffect
        kotlinx.coroutines.delay(180)
        party = 1
        feedback?.play(FeedbackSound.Success, view)
        titlePop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 380f))
    }

    AppSheet(onDismiss) {
        val close = sheetClose(onDismiss)
        Column(
            Modifier.fillMaxWidth().confetti(party, listOf(AppColors.Accent, StarYellow, Color(0xFFFF8FB1), Color(0xFF7ED3A0), Color(0xFF7DD3FC), Color(0xFFB9A2FF))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (celebrate) "Книга прочитана! 🎉" else "Оценка книги",
                color = AppColors.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp, stepSize = 0.5.sp),
                modifier = Modifier.padding(bottom = 6.dp).graphicsLayer {
                    scaleX = titlePop.value
                    scaleY = titlePop.value
                    alpha = ((titlePop.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                },
            )
            Row(
                Modifier.fillMaxWidth().fieldSurface(appCorner(16)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BookCover(book.id, 46.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        book.title,
                        color = AppColors.Text,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val pages = book.positions.takeIf { it > 0 }?.let { "$it/$it" }
                    val facts = listOfNotNull(book.author.takeIf { it.isNotBlank() }, pages, stats.ms.takeIf { it >= 60_000 }?.let { StatsCalc.duration(it) })
                    if (facts.isNotEmpty()) {
                        Text(facts.joinToString(" · "), color = AppColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            StarRating(rating, 42.dp, Modifier.padding(top = 12.dp), gap = 8.dp) { rating = it }
            Text(
                if (rating > 0f) "${ratingText(rating)}/5" else "Нажми на звёзды",
                color = if (rating > 0f) AppColors.Accent else AppColors.Faint,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        BasicTextField(
            value = review,
            onValueChange = { review = it.take(1000) },
            textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(AppColors.Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).fieldSurface(appCorner(16)).padding(14.dp),
            decorationBox = { inner ->
                Box {
                    if (review.isEmpty()) Text("Пара слов о книге…", color = AppColors.Faint, fontSize = 15.sp)
                    inner()
                }
            },
        )
        if (celebrate) {
            remember(books, book.id) { nextInSeries(books, book.copy(status = com.flerty.wikiflerty.data.ReadStatus.Finished)) }?.let { (next, s) ->
                NextInSeriesCard(next, s, app.library.coverFile(next), onClick = null)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = appCorner(16)
            Box(
                Modifier
                    .size(52.dp)
                    .liveClick(shape, sound = FeedbackSound.Open) {
                        if (sharing) return@liveClick
                        sharing = true
                        scope.launch {
                            shareRatingCard(context, app, book.copy(rating = rating, review = review.trim()))
                            sharing = false
                        }
                    }
                    .border(1.dp, AppColors.Line, shape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Share, "Поделиться", tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle) {
                        val r = rating.takeIf { it > 0f || review.isNotBlank() }
                        app.appScope.launch { app.library.setRating(book.id, r, review) }
                        close()
                    }
                    .accentHighlight(shape),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionLabel("Сохранить", accentContent, fontSize = 14.sp, iconSize = 21.dp, icon = AppIcons.Check)
            }
        }
    }
}

suspend fun shareRatingCard(context: Context, app: App, book: Book) {
    try {
        val uri = withContext(Dispatchers.IO) {
            val cover = app.library.coverFile(book)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            val bitmap = RatingCard.render(book, cover)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "review.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("review", uri)
        context.startActivity(Intent.createChooser(intent, "Поделиться отзывом").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось поделиться", Toast.LENGTH_SHORT).show()
    }
}

object RatingCard {
    private const val W = 1080
    private const val H = 1350

    fun render(book: Book, cover: Bitmap?): Bitmap {
        val accent = AppStyle.accent.toInt()
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), 0xFF221A14.toInt(), 0xFF120F0C.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = RadialGradient(W / 2f, 360f, 620f, withAlpha(accent, 0.28f), 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        val cw = 300f
        val ch = 450f
        val cr = RectF((W - cw) / 2f, 110f, (W + cw) / 2f, 110f + ch)
        p.color = 0x88000000.toInt()
        p.setShadowLayer(40f, 0f, 18f, 0xAA000000.toInt())
        c.drawRoundRect(cr, 22f, 22f, p)
        p.clearShadowLayer()
        if (cover != null) {
            c.save()
            val clip = Path().apply { addRoundRect(cr, 22f, 22f, Path.Direction.CW) }
            c.clipPath(clip)
            c.drawBitmap(cover, centerCrop(cover, cr), cr, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        } else {
            p.color = withAlpha(accent, 0.35f)
            c.drawRoundRect(cr, 22f, 22f, p)
        }

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF3EAE0.toInt()
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var y = cr.bottom + 60f
        y = drawBlock(c, book.title, title, y, 2, Layout.Alignment.ALIGN_CENTER)
        if (book.author.isNotBlank()) {
            val author = TextPaint(title).apply { textSize = 36f; color = 0xFFB8AA9B.toInt(); typeface = Typeface.DEFAULT }
            y = drawBlock(c, book.author, author, y + 10f, 1, Layout.Alignment.ALIGN_CENTER)
        }

        val rating = book.rating ?: 0f
        val star = 64f
        val gap = 18f
        val total = star * 5 + gap * 4
        var x = (W - total) / 2f
        val sy = y + 40f
        for (i in 1..5) {
            val fill = (rating - (i - 1)).coerceIn(0f, 1f)
            val path = starPath(x + star / 2, sy + star / 2, star / 2)
            p.color = 0xFF3A3129.toInt()
            c.drawPath(path, p)
            if (fill > 0f) {
                c.save()
                c.clipRect(x, sy, x + star * fill, sy + star)
                p.color = accent
                c.drawPath(path, p)
                c.restore()
            }
            x += star + gap
        }
        y = sy + star + 20f
        if (rating > 0f) {
            val score = TextPaint(title).apply { textSize = 40f; color = accent }
            y = drawBlock(c, "${ratingText(rating)}/5", score, y, 1, Layout.Alignment.ALIGN_CENTER)
        }

        if (book.review.isNotBlank()) {
            val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE9DED2.toInt()
                textSize = 38f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
            val box = RectF(90f, y + 36f, W - 90f, H - 150f)
            val layout = layout("«${book.review.trim()}»", text, (box.width() - 80).toInt(), 7, Layout.Alignment.ALIGN_CENTER)
            val bh = layout.height + 70f
            val top = box.top
            p.color = 0x33FFFFFF
            c.drawRoundRect(RectF(box.left, top, box.right, top + bh), 34f, 34f, p)
            p.color = withAlpha(accent, 0.6f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            c.drawRoundRect(RectF(box.left, top, box.right, top + bh), 34f, 34f, p)
            p.style = Paint.Style.FILL
            c.save()
            c.translate(box.left + 40f, top + 35f)
            layout.draw(c)
            c.restore()
        }

        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7E7165.toInt()
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawBlock(c, "WikiFlerty", brand, H - 100f, 1, Layout.Alignment.ALIGN_CENTER)
        return bmp
    }

    private fun drawBlock(c: Canvas, text: String, paint: TextPaint, y: Float, maxLines: Int, align: Layout.Alignment): Float {
        val l = layout(text, paint, W - 160, maxLines, align)
        c.save()
        c.translate(80f, y)
        l.draw(c)
        c.restore()
        return y + l.height
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.15f)
            .build()

    private fun starPath(cx: Float, cy: Float, r: Float): Path {
        val path = Path()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = Math.toRadians((-90 + i * 36).toDouble())
            val px = cx + (rr * cos(a)).toFloat()
            val py = cy + (rr * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    private fun centerCrop(src: Bitmap, dst: RectF): Rect {
        val sr = src.width.toFloat() / src.height
        val dr = dst.width() / dst.height()
        return if (sr > dr) {
            val w = (src.height * dr).toInt()
            Rect((src.width - w) / 2, 0, (src.width + w) / 2, src.height)
        } else {
            val h = (src.width / dr).toInt()
            Rect(0, (src.height - h) / 2, src.width, (src.height + h) / 2)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int = ((alpha * 255).toInt() shl 24) or (color and 0x00FFFFFF)
}
