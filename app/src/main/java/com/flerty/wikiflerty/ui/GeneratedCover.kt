package com.flerty.wikiflerty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

private val LibraryHues = listOf(0xFF5A2A22, 0xFF23384E, 0xFF2F4A2E, 0xFF4A2F52, 0xFF5E4220, 0xFF1F3F44)
private val ZenHues = listOf(0xFFF3EDE0, 0xFFE9E2D2, 0xFFEFE6DA, 0xFFE4E8DC)
private val ComicHues = listOf(0xFFFFCC00, 0xFF7AD3FF, 0xFFFF6B6B, 0xFF7DE38B, 0xFFFF8FB6, 0xFFB38CFF)
private val NewsHues = listOf(0xFFF6F1E6, 0xFFEFE9DC, 0xFFF2EEE4)
private val SeaHues = listOf(0xFF0A5C6E, 0xFF0F4A6B, 0xFF14606A, 0xFF1A4F7A, 0xFF0D6B5E)
private val ClayHues = listOf(0xFFFFB5C8, 0xFFB9E4C9, 0xFFFFD9A0, 0xFFC9B8EE, 0xFFA8DCF0, 0xFFFFC9A8)
private val PlainHues = listOf(0xFF3B5F7A, 0xFF8A3B2B, 0xFF5F7A4E, 0xFF7A4FA0, 0xFF2C2F5A, 0xFFB2763A, 0xFF1F6F8B, 0xFF6E2F3F)

private fun pick(list: List<Long>, seed: Int) = Color(list[(seed and 0x7FFFFFFF) % list.size])

private fun Color.lum() = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
fun GeneratedCover(title: String, author: String, modifier: Modifier = Modifier) {
    val decor = AppStyle.decor
    val seed = title.hashCode() * 31 + author.hashCode()
    val accent = AppColors.Accent
    val bg = when (decor) {
        Decor.Library -> pick(LibraryHues, seed)
        Decor.Zen -> pick(ZenHues, seed)
        Decor.Comic -> pick(ComicHues, seed)
        Decor.News -> pick(NewsHues, seed)
        Decor.Sea -> pick(SeaHues, seed)
        Decor.Clay -> pick(ClayHues, seed)
        Decor.None -> pick(PlainHues, seed)
    }
    val ink = when (decor) {
        Decor.Library -> Color(0xFFF0D48A)
        Decor.Zen -> Color(0xFF1E1E1E)
        Decor.Comic -> Color(0xFF141210)
        Decor.News -> Color(0xFF141414)
        Decor.Sea -> Color(0xFFEAFBFF)
        Decor.Clay -> Color(0xFF3B2F5C)
        Decor.None -> if (bg.lum() > 0.6f) Color(0xFF1A1A1A) else Color(0xFFF7F2E8)
    }
    val family = when (decor) {
        Decor.Library, Decor.Zen, Decor.News -> FontFamily.Serif
        else -> FontFamily.SansSerif
    }
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val pad = w * 0.12f
        Canvas(Modifier.fillMaxSize()) { coverArt(decor, bg, ink, accent, seed) }
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = pad, end = pad, top = w * (if (decor == Decor.News) 0.26f else 0.2f), bottom = w * 0.16f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (decor == Decor.News) Arrangement.Top else Arrangement.Center,
        ) {
            Text(
                if (decor == Decor.News || decor == Decor.Comic) title.uppercase() else title,
                color = ink,
                fontFamily = family,
                fontWeight = when (decor) {
                    Decor.Comic, Decor.News, Decor.Clay -> FontWeight.Black
                    Decor.Zen -> FontWeight.Medium
                    else -> FontWeight.Bold
                },
                fontStyle = if (decor == Decor.Sea) FontStyle.Italic else FontStyle.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 1.05.em(),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = (w.value * 0.17f).coerceIn(8f, 30f).sp, stepSize = 0.5.sp),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (author.isNotBlank()) {
                Text(
                    author,
                    color = ink.copy(alpha = 0.78f),
                    fontFamily = family,
                    fontSize = (w.value * 0.085f).coerceIn(6f, 13f).sp,
                    lineHeight = (w.value * 0.1f).coerceIn(7f, 15f).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = w * 0.05f),
                )
            }
        }
    }
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)

private fun DrawScope.coverArt(decor: Decor, bg: Color, ink: Color, accent: Color, seed: Int) {
    val w = size.width
    val h = size.height
    val u = w / 100f
    when (decor) {
        Decor.Library -> {
            drawRect(Brush.verticalGradient(listOf(lerp(bg, Color.White, 0.08f), bg, lerp(bg, Color.Black, 0.3f))))
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), endX = w * 0.12f), size = Size(w * 0.12f, h))
            val gold = Color(0xFFD8B46A)
            drawRect(gold, Offset(6 * u, 6 * u), Size(w - 12 * u, h - 12 * u), style = Stroke(1.6f * u))
            drawRect(gold.copy(alpha = 0.6f), Offset(9.5f * u, 9.5f * u), Size(w - 19 * u, h - 19 * u), style = Stroke(0.7f * u))
            for (c in listOf(Offset(9.5f * u, 9.5f * u), Offset(w - 9.5f * u, 9.5f * u), Offset(9.5f * u, h - 9.5f * u), Offset(w - 9.5f * u, h - 9.5f * u))) {
                val p = Path().apply { moveTo(c.x, c.y - 3 * u); lineTo(c.x + 3 * u, c.y); lineTo(c.x, c.y + 3 * u); lineTo(c.x - 3 * u, c.y); close() }
                drawPath(p, gold)
            }
            drawLine(gold.copy(alpha = 0.8f), Offset(w * 0.3f, h * 0.84f), Offset(w * 0.7f, h * 0.84f), 0.8f * u)
        }
        Decor.Zen -> {
            drawRect(bg)
            val r = java.util.Random(seed.toLong())
            for (i in 0 until 26) {
                val x = r.nextFloat() * w
                val y = r.nextFloat() * h
                drawLine(Color.Black.copy(alpha = 0.04f), Offset(x, y), Offset(x + (4 + r.nextFloat() * 10) * u, y + r.nextFloat() * 2 * u), 0.6f * u)
            }
            val brush = Path().apply {
                moveTo(10 * u, h * 0.86f)
                cubicTo(w * 0.35f, h * 0.83f, w * 0.6f, h * 0.89f, w - 10 * u, h * 0.85f)
            }
            drawPath(brush, Color(0xFF1E1E1E).copy(alpha = 0.75f), style = Stroke(2.4f * u, cap = StrokeCap.Round))
            drawRect(accent, Offset(w - 22 * u, 8 * u), Size(12 * u, 12 * u))
            drawRect(Color.White.copy(alpha = 0.6f), Offset(w - 19.5f * u, 10.5f * u), Size(7 * u, 7 * u), style = Stroke(0.8f * u))
            drawRect(Color.Black.copy(alpha = 0.08f), style = Stroke(0.8f * u))
        }
        Decor.Comic -> {
            drawRect(bg)
            val step = 7 * u
            var y = 0f
            var row = 0
            while (y < h) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < w) {
                    val k = (x / w + y / h) / 2f
                    drawCircle(Color.Black.copy(alpha = 0.12f), (0.4f + k * 2f) * u, Offset(x, y))
                    x += step
                }
                y += step * 0.866f
                row++
            }
            val burst = Path()
            val c = Offset(w * 0.5f, h * 0.48f)
            for (k in 0 until 24) {
                val a = 2f * PI.toFloat() * k / 24f
                val r = (if (k % 2 == 0) 46f else 32f) * u
                if (k == 0) burst.moveTo(c.x + kotlin.math.cos(a) * r, c.y + sin(a) * r * 1.3f) else burst.lineTo(c.x + kotlin.math.cos(a) * r, c.y + sin(a) * r * 1.3f)
            }
            burst.close()
            drawPath(burst, Color.White)
            drawPath(burst, Color(0xFF141210), style = Stroke(2 * u))
            drawRect(Color(0xFF141210), style = Stroke(4 * u))
        }
        Decor.News -> {
            drawRect(bg)
            val ink2 = Color(0xFF141414)
            drawRect(ink2, Offset(7 * u, 8 * u), Size(w - 14 * u, 3 * u))
            drawRect(ink2, Offset(7 * u, 13 * u), Size(w - 14 * u, 0.8f * u))
            drawRect(accent, Offset(7 * u, 16 * u), Size(18 * u, 3 * u))
            val bottom = h - 8 * u
            for (r in 0 until 6) {
                val y = bottom - r * 4 * u
                drawRect(ink2.copy(alpha = 0.18f), Offset(7 * u, y), Size(w * 0.4f, 1.4f * u))
                drawRect(ink2.copy(alpha = 0.18f), Offset(w * 0.53f, y), Size(w * 0.4f, 1.4f * u))
            }
            drawRect(ink2, Offset(7 * u, bottom - 26 * u), Size(w - 14 * u, 0.8f * u))
        }
        Decor.Sea -> {
            drawRect(Brush.verticalGradient(listOf(lerp(bg, Color.White, 0.18f), bg, lerp(bg, Color.Black, 0.35f))))
            for (i in 0 until 3) {
                val x = w * (0.15f + i * 0.3f)
                val p = Path().apply { moveTo(x, 0f); lineTo(x + 10 * u, 0f); lineTo(x + 2 * u, h * 0.7f); lineTo(x - 14 * u, h * 0.7f); close() }
                drawPath(p, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), endY = h * 0.7f))
            }
            val wave = Path().apply {
                moveTo(0f, h * 0.86f)
                var x = 0f
                while (x <= w) {
                    lineTo(x, h * 0.86f + sin(x / (9 * u)) * 2.2f * u)
                    x += 2 * u
                }
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(wave, Color(0xFFC9B48A).copy(alpha = 0.45f))
            val r = java.util.Random(seed.toLong())
            for (i in 0 until 5) drawCircle(Color.White.copy(alpha = 0.45f), (1 + r.nextFloat() * 2) * u, Offset(r.nextFloat() * w, r.nextFloat() * h * 0.6f), style = Stroke(0.6f * u))
        }
        Decor.Clay -> {
            drawRect(bg)
            drawRoundRect(lerp(bg, Color.White, 0.35f), Offset(7 * u, 7 * u), Size(w - 14 * u, h - 14 * u), CornerRadius(12 * u))
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent, Color.Black.copy(alpha = 0.08f))), Offset(7 * u, 7 * u), Size(w - 14 * u, h - 14 * u), CornerRadius(12 * u))
            drawCircle(lerp(bg, Color.Black, 0.12f), 6 * u, Offset(w * 0.24f, h * 0.88f))
            drawCircle(Color(0xFFB48CFF).copy(alpha = 0.8f), 4 * u, Offset(w * 0.5f, h * 0.9f))
            drawCircle(Color(0xFF8AD09A).copy(alpha = 0.9f), 5 * u, Offset(w * 0.74f, h * 0.88f))
        }
        Decor.None -> {
            drawRect(Brush.verticalGradient(listOf(lerp(bg, Color.White, 0.12f), bg, lerp(bg, Color.Black, 0.25f))))
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent), endX = w * 0.1f), size = Size(w * 0.1f, h))
            drawLine(ink.copy(alpha = 0.5f), Offset(w * 0.3f, h * 0.84f), Offset(w * 0.7f, h * 0.84f), 0.8f * u)
        }
    }
}
