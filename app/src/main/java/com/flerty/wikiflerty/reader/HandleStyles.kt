package com.flerty.wikiflerty.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

enum class HandleStyle(val id: String, val title: String) {
    Drop("drop", "Капля"),
    Circle("circle", "Круг"),
    Pin("pin", "Булавка"),
    Caret("caret", "Каретка"),
    Diamond("diamond", "Ромб"),
    Bracket("bracket", "Уголок"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Drop
    }
}

fun DrawScope.drawSelectionHandle(style: HandleStyle, start: Boolean, color: Color, anchor: Offset, unit: Float) {
    val s = if (start) -1f else 1f
    val shadow = Color.Black.copy(alpha = 0.22f)
    val lift = Offset(0f, 1.2f * unit)
    fun both(draw: (Color, Offset) -> Unit) {
        draw(shadow, lift)
        draw(color, Offset.Zero)
    }
    when (style) {
        HandleStyle.Drop -> both { c, o ->
            val r = 10f * unit
            val center = anchor + Offset(s * r, r) + o
            val path = Path().apply {
                addOval(Rect(center, r))
                addRect(Rect(minOf(anchor.x + o.x, center.x), anchor.y + o.y, maxOf(anchor.x + o.x, center.x), center.y))
            }
            drawPath(path, c)
        }
        HandleStyle.Circle -> both { c, o ->
            drawCircle(c, 8f * unit, anchor + Offset(0f, 9f * unit) + o)
        }
        HandleStyle.Pin -> both { c, o ->
            drawLine(c, anchor + o, anchor + Offset(0f, 10f * unit) + o, strokeWidth = 2.2f * unit, cap = StrokeCap.Round)
            drawCircle(c, 5.5f * unit, anchor + Offset(0f, 14f * unit) + o)
        }
        HandleStyle.Caret -> both { c, o ->
            val top = anchor + Offset(0f, -21f * unit) + o
            drawLine(c, top, anchor + o, strokeWidth = 2.4f * unit, cap = StrokeCap.Round)
            drawCircle(c, 5f * unit, if (start) top + Offset(0f, -4f * unit) else anchor + Offset(0f, 4f * unit) + o)
        }
        HandleStyle.Diamond -> both { c, o ->
            val a = anchor + o
            val k = 8f * unit
            val path = Path().apply {
                moveTo(a.x, a.y)
                lineTo(a.x + k, a.y + k)
                lineTo(a.x, a.y + 2 * k)
                lineTo(a.x - k, a.y + k)
                close()
            }
            drawPath(path, c)
        }
        HandleStyle.Bracket -> both { c, o ->
            val a = anchor + o
            val stroke = Stroke(3f * unit, cap = StrokeCap.Round)
            val path = Path().apply {
                moveTo(a.x, a.y - 16f * unit)
                lineTo(a.x, a.y)
                lineTo(a.x - s * 9f * unit, a.y)
            }
            drawPath(path, c, style = stroke)
            drawCircle(c, 4.5f * unit, a + Offset(0f, 7f * unit))
        }
    }
}
