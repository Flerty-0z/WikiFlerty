package com.flerty.wikiflerty.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.data.CharacterMap
import com.flerty.wikiflerty.data.MapPerson
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CharMapState {
    var map by mutableStateOf<CharacterMap?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selected by mutableStateOf<String?>(null)
}

class CharMapBridge(
    val state: CharMapState,
    val avatarOf: (String) -> File?,
    val onLoad: () -> Unit,
    val onBuild: () -> Unit,
    val onTalk: (MapPerson) -> Unit,
)

private val GroupColors = listOf(
    0xFFF2A65A, 0xFF7DD3FC, 0xFFA78BFA, 0xFF8BD3A5, 0xFFF49AC1, 0xFFF7D774, 0xFF5EEAD4, 0xFFF08A8A,
).map { Color(it) }

private class Layout(val pos: Map<String, Offset>)

private fun layout(map: CharacterMap): Layout {
    val people = map.people
    val n = people.size
    if (n == 0) return Layout(emptyMap())
    val rnd = java.util.Random(people.joinToString { it.key }.hashCode().toLong())
    val p = Array(n) { i ->
        val a = 2 * Math.PI * i / n
        floatArrayOf((300 + 200 * Math.cos(a)).toFloat() + rnd.nextFloat() * 10, (300 + 200 * Math.sin(a)).toFloat() + rnd.nextFloat() * 10)
    }
    val idx = people.withIndex().associate { it.value.key to it.index }
    val edges = map.links.mapNotNull { l -> val a = idx[l.from.lowercase()]; val b = idx[l.to.lowercase()]; if (a != null && b != null) a to b else null }
    val groups = people.map { it.group.lowercase() }
    val ideal = 150f
    repeat(500) { step ->
        val t = 1f - step / 500f
        val d = Array(n) { floatArrayOf(0f, 0f) }
        for (i in 0 until n) for (j in i + 1 until n) {
            var dx = p[i][0] - p[j][0]
            var dy = p[i][1] - p[j][1]
            var dist = sqrt(dx * dx + dy * dy)
            if (dist < 1f) { dx = 1f; dy = 0f; dist = 1f }
            val f = ideal * ideal / dist * (if (groups[i] == groups[j] && groups[i].isNotBlank()) 0.7f else 1.1f)
            d[i][0] += dx / dist * f; d[i][1] += dy / dist * f
            d[j][0] -= dx / dist * f; d[j][1] -= dy / dist * f
        }
        edges.forEach { (a, b) ->
            val dx = p[a][0] - p[b][0]
            val dy = p[a][1] - p[b][1]
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val f = dist * dist / ideal
            d[a][0] -= dx / dist * f; d[a][1] -= dy / dist * f
            d[b][0] += dx / dist * f; d[b][1] += dy / dist * f
        }
        for (i in 0 until n) {
            d[i][0] += (300 - p[i][0]) * 0.6f
            d[i][1] += (300 - p[i][1]) * 0.6f
            val len = sqrt(d[i][0] * d[i][0] + d[i][1] * d[i][1]).coerceAtLeast(0.01f)
            val move = minOf(len, 30f * t + 1f)
            p[i][0] += d[i][0] / len * move
            p[i][1] += d[i][1] / len * move
        }
    }
    val minX = p.minOf { it[0] }; val maxX = p.maxOf { it[0] }
    val minY = p.minOf { it[1] }; val maxY = p.maxOf { it[1] }
    val w = (maxX - minX).coerceAtLeast(1f); val h = (maxY - minY).coerceAtLeast(1f)
    return Layout(people.withIndex().associate { (i, person) ->
        person.key to Offset(if (n == 1) 0.5f else (p[i][0] - minX) / w, if (n == 1) 0.5f else (p[i][1] - minY) / h)
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterMapContent(bridge: CharMapBridge, modifier: Modifier = Modifier) {
    val state = bridge.state
    androidx.compose.runtime.LaunchedEffect(Unit) { bridge.onLoad() }
    val map = state.map
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (map == null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Graph, null, tint = AppColors.Accent, modifier = Modifier.size(40.dp))
                Text("Кто кому кто", color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "ИИ нарисует героев и связи между ними по прочитанной части — без спойлеров",
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "● до ${map.place} · ${map.people.size} " + com.flerty.wikiflerty.ui.StatsCalc.plural(map.people.size, "герой", "героя", "героев"),
                    color = AppColors.Good,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (map.people.any { it.fresh } || map.links.any { it.fresh }) {
                    Text("✨ новое", color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            MapGraph(map, bridge)
            val groups = map.people.map { it.group }.filter { it.isNotBlank() }.distinct()
            if (groups.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEachIndexed { i, g ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(GroupColors[i % GroupColors.size]))
                            Text(g, color = AppColors.Muted, fontSize = 11.5.sp, maxLines = 1)
                        }
                    }
                }
            }
            map.people.firstOrNull { it.key == state.selected }?.let { PersonCard(it, map, bridge) }
        }
        state.error?.let { Text(it, color = AppColors.Bad, fontSize = 13.sp, lineHeight = 18.sp) }
        val shape = appCorner(16)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .liveClick(shape, sound = FeedbackSound.Open, enabled = !state.loading, onClick = bridge.onBuild)
                .accentHighlight(shape),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accentContent)
                Text("Рисую карту…", color = accentContent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
            } else {
                Icon(if (map == null) AppIcons.Graph else AppIcons.Rotate, null, tint = accentContent, modifier = Modifier.size(18.dp))
                Text(if (map == null) "Построить карту" else "Обновить по прочитанному", color = accentContent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun MapGraph(map: CharacterMap, bridge: CharMapBridge) {
    val state = bridge.state
    val positions by produceState<Map<String, Offset>?>(null, map) {
        value = withContext(Dispatchers.Default) { layout(map).pos }
    }
    val groupIndex = remember(map) { map.people.map { it.group.lowercase() }.filter { it.isNotBlank() }.distinct() }
    var scale by remember(map) { mutableFloatStateOf(1f) }
    var pan by remember(map) { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = AppColors.Muted, fontSize = 10.sp)
    val lineColor = AppColors.Line
    val freshColor = AppColors.Accent
    val pill = AppColors.Panel
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(appCorner(20))
            .fieldSurface(appCorner(20))
            .pointerInput(map) {
                detectTransformGestures { _, p, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.6f, 3f)
                    pan += p
                }
            },
    ) {
        val pos = positions
        if (pos == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = AppColors.Accent, strokeWidth = 2.dp)
            return@BoxWithConstraints
        }
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val pad = with(density) { 48.dp.toPx() }
        fun at(key: String): Offset {
            val o = pos[key] ?: Offset(0.5f, 0.5f)
            return Offset(pad + o.x * (wPx - 2 * pad), pad + o.y * (hPx - 2 * pad))
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = pan.x
                translationY = pan.y
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                map.links.forEach { l ->
                    val a = at(l.from.lowercase())
                    val b = at(l.to.lowercase())
                    val sel = state.selected != null && (l.from.lowercase() == state.selected || l.to.lowercase() == state.selected)
                    drawLine(
                        if (l.fresh || sel) freshColor else lineColor,
                        a,
                        b,
                        strokeWidth = (if (sel) 2.5.dp else 1.5.dp).toPx(),
                        pathEffect = if (l.fresh) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                    )
                    if (l.label.isNotBlank()) {
                        val m = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                        val text = measurer.measure(l.label.take(22), labelStyle)
                        val tl = Offset(m.x - text.size.width / 2f - 6, m.y - text.size.height / 2f - 2)
                        drawRoundRect(pill, tl, androidx.compose.ui.geometry.Size(text.size.width + 12f, text.size.height + 4f), androidx.compose.ui.geometry.CornerRadius(20f))
                        drawText(text, topLeft = Offset(tl.x + 6, tl.y + 2))
                    }
                }
            }
            map.people.forEach { person ->
                val c = at(person.key)
                val color = groupIndex.indexOf(person.group.lowercase()).takeIf { it >= 0 }?.let { GroupColors[it % GroupColors.size] } ?: AppColors.Accent
                PersonNode(
                    person,
                    color,
                    selected = state.selected == person.key,
                    avatar = bridge.avatarOf(person.name),
                    modifier = Modifier.offset { IntOffset((c.x - with(density) { 45.dp.toPx() }).roundToInt(), (c.y - with(density) { 24.dp.toPx() }).roundToInt()) },
                ) { state.selected = if (state.selected == person.key) null else person.key }
            }
        }
    }
}

@Composable
private fun PersonNode(person: MapPerson, color: Color, selected: Boolean, avatar: File?, modifier: Modifier, onClick: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, avatar) {
        value = avatar?.takeIf { it.exists() }?.let { f -> withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() } }
    }
    Column(modifier.width(90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier
                    .size(48.dp)
                    .liveClick(CircleShape, sound = FeedbackSound.Tap, pressedScale = 0.9f, onClick = onClick)
                    .background(color.copy(alpha = 0.2f))
                    .border(if (selected) 3.dp else 2.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val img = image
                if (img != null) {
                    Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Text(person.name.take(1).uppercase(), color = color, fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
            }
            if (person.fresh) {
                Text("✨", fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
            }
        }
        Text(
            person.name,
            color = if (selected) AppColors.Accent else AppColors.Text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun PersonCard(person: MapPerson, map: CharacterMap, bridge: CharMapBridge) {
    val shape = appCorner(18)
    Column(
        Modifier.fillMaxWidth().fieldSurface(shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(person.name, color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(person.role, person.group).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = AppColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(34.dp).liveClick(CircleShape, sound = FeedbackSound.Close) { bridge.state.selected = null }.background(AppColors.Line),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, "Закрыть", tint = AppColors.Muted, modifier = Modifier.size(15.dp)) }
        }
        if (person.about.isNotBlank()) Text(person.about, color = AppColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
        val related = map.links.filter { it.from.equals(person.name, true) || it.to.equals(person.name, true) }
        related.forEach { l ->
            val other = if (l.from.equals(person.name, true)) l.to else l.from
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(other, color = AppColors.Text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Text("— ${l.label}", color = AppColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (l.fresh) Text("✨", fontSize = 11.sp)
            }
        }
        val btn = appCorner(14)
        Row(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .liveClick(btn, sound = FeedbackSound.Open) { bridge.onTalk(person) }
                .border(1.dp, AppColors.Accent.copy(alpha = 0.5f), btn),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Voice, null, tint = AppColors.Accent, modifier = Modifier.size(17.dp))
            Text("Поговорить", color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
