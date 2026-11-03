package com.flerty.wikiflerty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

data class StatCell(val label: String, val value: String, val hint: String? = null, val icon: ImageVector? = null)

@Composable
fun StatPanel(title: String?, cells: List<StatCell>, modifier: Modifier = Modifier, icon: ImageVector? = null, columns: Int = 2) {
    if (title != null) {
        TabbedFrame(title, icon = icon, inset = false, modifier = modifier) { StatGrid(cells, columns) }
    } else {
        Box(modifier.fillMaxWidth().fieldSurface(appCorner(18))) { StatGrid(cells, columns) }
    }
}

private class GridLines {
    val xs = mutableListOf<Float>()
    val ys = mutableListOf<Float>()
}

@Composable
fun StatGrid(cells: List<StatCell>, columns: Int = 2) {
    val lines = remember { GridLines() }
    val color = AppColors.PanelBorder
    Layout(
        content = { cells.forEach { StatCellView(it, compact = columns > 2) } },
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val stroke = 1.dp.toPx()
                lines.xs.forEach { x -> drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke) }
                lines.ys.forEach { y -> drawLine(color, Offset(0f, y), Offset(size.width, y), stroke) }
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val cell = width / columns
        val placeables = measurables.mapIndexed { i, m ->
            val w = if (i % columns == columns - 1) width - cell * (columns - 1) else cell
            m.measure(Constraints(minWidth = w, maxWidth = w))
        }
        val rows = placeables.chunked(columns)
        val heights = rows.map { r -> r.maxOf { it.height } }
        lines.xs.clear()
        lines.ys.clear()
        if (placeables.size > 1) for (c in 1 until columns) lines.xs += (cell * c).toFloat()
        var acc = 0
        heights.dropLast(1).forEach { h ->
            acc += h
            lines.ys += acc.toFloat()
        }
        layout(width, heights.sum()) {
            var y = 0
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, p -> p.place(cell * c, y) }
                y += heights[r]
            }
        }
    }
}

@Composable
private fun StatCellView(cell: StatCell, compact: Boolean) {
    val muted = AppColors.Muted
    Column(
        Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (cell.icon != null) Icon(cell.icon, null, tint = AppColors.Accent, modifier = Modifier.size(15.dp))
            FitText(cell.label, AppColors.Muted, if (compact) 11.sp else 12.sp, weight = FontWeight.SemiBold)
        }
        val value = remember(cell.value, muted) { styledValue(cell.value, muted) }
        Text(
            value,
            color = AppColors.Text,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = if (compact) 21.sp else 25.sp, stepSize = 0.5.sp),
        )
        if (cell.hint != null) {
            Text(cell.hint, color = AppColors.Faint, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2)
        }
    }
}

private fun styledValue(value: String, muted: androidx.compose.ui.graphics.Color): AnnotatedString {
    val parts = value.split(" ")
    if (parts.none { p -> p.any { it.isDigit() } }) return AnnotatedString(value)
    return buildAnnotatedString {
        parts.forEachIndexed { i, p ->
            if (i > 0) append(" ")
            if (p.any { it.isDigit() }) append(p) else withStyle(SpanStyle(fontSize = 0.6.em, color = muted, fontWeight = FontWeight.Bold)) { append(p) }
        }
    }
}
