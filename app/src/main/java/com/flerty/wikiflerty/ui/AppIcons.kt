package com.flerty.wikiflerty.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

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
    val TextSize by lazy { icon("text-size", "M3 19L8 5l5 14", "M5 14.5h6", "M14.5 19l3.25-8 3.25 8", "M15.6 16.3h4.3") }
    val LineHeight by lazy { icon("line-height", "M11 6h9M11 12h9M11 18h9", "M5 4v16", "M3 6l2-2 2 2", "M3 18l2 2 2-2") }
    val Margins by lazy { icon("margins", "M4 4v16M20 4v16", "M8 8h8M8 12h8M8 16h5") }
    val Wrap by lazy { icon("wrap", "M4 6h16", "M4 12h13a3 3 0 0 1 0 6h-3", "M16 16l-2 2 2 2", "M4 18h6") }
    val Animation by lazy { icon("animation", "M4 6h10v12H4z", "M17 7.5v9", "M20.5 9.5v5") }
    val Scroll by lazy { icon("scroll", "M6 4h12M6 20h12", "M12 7v10", "M9 14l3 3 3-3") }
    val VolumeKeys by lazy {
        icon("volume-keys", "M9 3h8a1.5 1.5 0 0 1 1.5 1.5v15A1.5 1.5 0 0 1 17 21H9a1.5 1.5 0 0 1-1.5-1.5v-15A1.5 1.5 0 0 1 9 3z", "M4.5 7v5")
    }
    val Flag by lazy { icon("flag", "M5 21V4", "M5 4h12l-2.5 4L17 12H5") }
    val Percent by lazy {
        icon("percent", "M19 5L5 19", "M7 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M17 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z")
    }
    val EdgeZones by lazy { icon("edge-zones", "M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M9 4v16M15 4v16") }
    val Forward by lazy { icon("forward", "M5 12h14", "M13 6l6 6-6 6") }
    val Rotate by lazy { icon("rotate", "M20 12a8 8 0 1 1-2.4-5.7", "M20 4v5h-5") }
    val Fullscreen by lazy { icon("fullscreen", "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5") }
    val Bulb by lazy { icon("bulb", "M12 3a6 6 0 0 0-3.5 10.9V17h7v-3.1A6 6 0 0 0 12 3z", "M9.5 21h5") }
    val Vibration by lazy { icon("vibration", "M8.5 4h7v16h-7z", "M5 8v8M19 8v8") }
    val Palette by lazy {
        icon(
            "palette",
            "M12 3a9 9 0 1 0 0 18c1.4 0 2-.9 2-1.8 0-1.1-1-1.4-1-2.4 0-.9.8-1.6 1.8-1.6H17a4 4 0 0 0 4-4c0-4.6-4-8.2-9-8.2z",
            fills = listOf(
                "M7.5 10.5a1.3 1.3 0 1 0 0 2.6a1.3 1.3 0 1 0 0-2.6z",
                "M10.5 6.5a1.3 1.3 0 1 0 0 2.6a1.3 1.3 0 1 0 0-2.6z",
                "M15 7a1.3 1.3 0 1 0 0 2.6a1.3 1.3 0 1 0 0-2.6z",
            ),
        )
    }
    val Corners by lazy { icon("corners", "M5 20V11a6 6 0 0 1 6-6h9") }
    val Outline by lazy { icon("outline", "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z") }
    val Press by lazy { icon("press", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M12 3v2M12 19v2M3 12h2M19 12h2") }
    val Image by lazy { icon("image", "M5 5h14a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M4 16l5-5 4 4 2-2 5 5", "M15.5 9h.01") }
    val Video by lazy { icon("video", "M4 7h11v10H4z", "M15 10.5l5-3v9l-5-3") }
    val Moon by lazy { icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }
    val Drop by lazy { icon("drop", "M12 3.5l5 6.5a6.2 6.2 0 1 1-10 0z") }
    val Target by lazy {
        icon("target", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", fills = listOf("M12 10.8a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z"))
    }
    val Book by lazy { icon("book", "M5 5.5A2.5 2.5 0 0 1 7.5 3H19v15H7.5A2.5 2.5 0 0 0 5 20.5z", "M5 20.5A2.5 2.5 0 0 0 7.5 23H19") }
    val Translate by lazy { icon("translate", "M4 5h9M8.5 3v2", "M11 5c-.5 4-3 7-6.5 8.5", "M6.5 8.5c1 2 3 3.8 5 4.5", "M13 21l4-9 4 9", "M14.6 17.5h4.8") }
    val Fire by lazy { icon("fire", "M12 3c.8 3.5 5 5 5 10a5 5 0 0 1-10 0c0-2.6 1.4-3.8 2-6 1 1.2 1.6 2 3 2.5-.6-2-.6-4.3 0-6.5z") }
    val Calendar by lazy { icon("calendar", "M5 6h14a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z", "M4 10h16", "M8 3.5v4M16 3.5v4") }
    val Sliders by lazy { icon("sliders", "M4 6h16M4 12h16M4 18h16", "M9 4v4M15 10v4M7 16v4") }
    val Length by lazy { icon("length", "M4 7h16M4 12h11M4 17h7") }
    val Star by lazy { icon("star", "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z") }
    val StarFilled by lazy { icon("star-filled", "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z", fills = listOf("M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z")) }
    val Headphones by lazy {
        icon("headphones", "M4 15v-3a8 8 0 0 1 16 0v3", "M4 15h3.5v5.5H5.5A1.5 1.5 0 0 1 4 19z", "M20 15h-3.5v5.5h2A1.5 1.5 0 0 0 20 19z")
    }
    val Play by lazy { icon("play", fills = listOf("M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z")) }
    val Pause by lazy { icon("pause", fills = listOf("M7 5h3.5v14H7z", "M13.5 5H17v14h-3.5z")) }
    val SkipNext by lazy { icon("skip-next", "M18 6v12", fills = listOf("M6 6.5v11a1 1 0 0 0 1.55.83l8-5.5a1 1 0 0 0 0-1.66l-8-5.5A1 1 0 0 0 6 6.5z")) }
    val SkipPrev by lazy { icon("skip-prev", "M6 6v12", fills = listOf("M18 6.5v11a1 1 0 0 1-1.55.83l-8-5.5a1 1 0 0 1 0-1.66l8-5.5A1 1 0 0 1 18 6.5z")) }
    val Stop by lazy { icon("stop", fills = listOf("M7.5 6h9A1.5 1.5 0 0 1 18 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 16.5v-9A1.5 1.5 0 0 1 7.5 6z")) }
    val Speed by lazy { icon("speed", "M4 16a8 8 0 1 1 16 0", "M12 16l4-5", "M4 16h2M18 16h2") }
    val Voice by lazy { icon("voice", "M12 3a3 3 0 0 0-3 3v5a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z", "M6 11a6 6 0 0 0 12 0", "M12 17v4") }
    val Engine by lazy { icon("engine", "M4 10v4M8 7v10M12 4v16M16 8v8M20 11v2") }
    val ShelfAdd by lazy { icon("shelf-add", "M5 5v15M9.5 5v15", "M3 20h10", "M17.5 8v8M13.5 12h8") }
    val Bookmark by lazy { icon("bookmark", "M7 3h10v18l-5-3.6L7 21z") }
    val BookmarkFilled by lazy { icon("bookmark-filled", "M7 3h10v18l-5-3.6L7 21z", fills = listOf("M7 3h10v18l-5-3.6L7 21z")) }
    val Quote by lazy {
        icon(
            "quote",
            fills = listOf(
                "M4 18v-5.5C4 8.4 6.2 5.6 9.6 5l.6 1.9C8.4 7.6 7.4 9 7.3 11H10v7z",
                "M13.5 18v-5.5c0-4.1 2.2-6.9 5.6-7.5l.6 1.9c-1.8.7-2.8 2.1-2.9 4.1h2.7v7z",
            ),
        )
    }
    val Share by lazy { icon("share", "M12 3v12", "M7.5 7.5L12 3l4.5 4.5", "M5 12v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7") }
    val Trophy by lazy {
        icon("trophy", "M8 4h8v6a4 4 0 0 1-8 0z", "M8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4", "M12 14v4", "M8.5 21h7", "M10 18h4")
    }
    val Lock by lazy { icon("lock", "M7 11h10a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z", "M9 11V8a3 3 0 0 1 6 0v3") }
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
    val Folder by lazy { icon("folder", "M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2.5h8.5A1.5 1.5 0 0 1 21 9v9.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z") }
    val FolderSearch by lazy { icon("folder-search", "M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2.5h8.5A1.5 1.5 0 0 1 21 9v3", "M3 6.5v12A1.5 1.5 0 0 0 4.5 20H11", "M16.5 13a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M21 21l-2.3-2.3") }
    val Search by lazy { icon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-4-4") }
    val Sun by lazy { icon("sun", "M12 7a5 5 0 1 0 0 10a5 5 0 1 0 0-10z", "M12 1v2M12 21v2M1 12h2M21 12h2") }
}
