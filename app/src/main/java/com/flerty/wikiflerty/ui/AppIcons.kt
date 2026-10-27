package com.flerty.wikiflerty.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Outline icons drawn on a 24×24 grid, matching the design mockups. */
object AppIcons {
    private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList(), width: Float = 2f): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        strokes.forEach { d ->
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        fills.forEach { d ->
            builder.addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
        }
        return builder.build()
    }

    val Back by lazy { icon("back", "M15 6l-6 6 6 6") }
    val Close by lazy { icon("close", "M6 6l12 12M18 6L6 18", width = 2.2f) }
    val Chevron by lazy { icon("chevron", "M9 6l6 6-6 6") }
    val Check by lazy { icon("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.4f) }
    val Toc by lazy { icon("toc", "M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01") }
    val Theme by lazy {
        icon("theme", "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", fills = listOf("M12 4a8 8 0 0 1 0 16z"))
    }
    val Pages by lazy { icon("pages", "M4 5h7v14H4zM13 5h7v14h-7z") }
    val Note by lazy { icon("note", "M5 3h10l4 4v14H5z", "M9 11h6M9 15h6") }
    val Options by lazy {
        icon("options", "M4 7h10M18 7h2M4 17h4M12 17h8", "M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z")
    }
    val Panel by lazy { icon("panel", "M4 4h16v5H4z", "M4 15h16v5H4z") }
    val Gesture by lazy {
        icon("gesture", "M9 12V5a2 2 0 0 1 4 0v6", "M13 10a2 2 0 0 1 4 0v4a6 6 0 0 1-6 6h-1a5 5 0 0 1-4-2l-2-3a2 2 0 0 1 3-2l1 1")
    }
    val Screen by lazy {
        icon(
            "screen",
            "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
            "M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5",
        )
    }
    val Sound by lazy { icon("sound", "M4 10v4h4l5 4V6L8 10H4z", "M16 9a4 4 0 0 1 0 6", "M18.5 6.5a8 8 0 0 1 0 11") }
    val Backup by lazy { icon("backup", "M12 3v12", "M7 10l5 5 5-5", "M4 16v4h16v-4") }
    val Heart by lazy { icon("heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z") }
    val HeartFilled by lazy {
        icon("heart-filled", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z",
            fills = listOf("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"))
    }
    val Grid by lazy { icon("grid", "M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h7v7H4z", "M13 13h7v7h-7z") }
    val ListView by lazy { icon("list", "M4 5h4v4H4z", "M4 15h4v4H4z", "M11 7h9M11 17h9") }
    val Sort by lazy { icon("sort", "M7 4v16M4 17l3 3 3-3", "M13 6h7M13 12h5M13 18h3") }
    val Chart by lazy { icon("chart", "M4 20h16", "M7 16v-5M12 16V7M17 16v-8") }
    val Shelf by lazy { icon("shelf", "M5 4v16M10 4v16", "M14.5 5.5l4 14", "M3 20h18") }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Clock by lazy { icon("clock", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 7v5l3 2") }
    val Info by lazy { icon("info", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 11v5M12 8h.01") }
    val Bookmark by lazy { icon("bookmark", "M7 3h10v18l-5-3.6L7 21z") }
    val Sparkle by lazy {
        icon("sparkle", fills = listOf("M12 2l2.1 6.1L20 10l-5.9 1.9L12 18l-2.1-6.1L4 10l5.9-1.9z"))
    }
    val Copy by lazy {
        icon(
            "copy",
            "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z",
            "M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3",
        )
    }
    val Pencil by lazy { icon("pencil", "M4 20h4L19 9l-4-4L4 16v4z", "M13.5 6.5l4 4") }
    val Trash by lazy { icon("trash", "M4 7h16", "M10 11v6M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3") }
    val Send by lazy { icon("send", "M12 19V5M5 12l7-7 7 7", width = 2.4f) }
    val Eye by lazy {
        icon("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z")
    }
    val EyeOff by lazy {
        icon("eye-off", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M4 4l16 16")
    }
    val Search by lazy { icon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-4-4") }
    val Sun by lazy { icon("sun", "M12 7a5 5 0 1 0 0 10a5 5 0 1 0 0-10z", "M12 1v2M12 21v2M1 12h2M21 12h2") }
}
