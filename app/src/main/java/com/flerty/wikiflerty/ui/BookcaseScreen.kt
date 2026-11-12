package com.flerty.wikiflerty.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.VisibilityThreshold
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    class Add(val onAdd: () -> Unit) : ShelfUnit("add")
    class Prop(val index: Int, key: String) : ShelfUnit(key)
}

class PlankSpec(val key: String, val name: String?, val books: List<Book>, val onAdd: (() -> Unit)?, val onEdit: (() -> Unit)?, val wrap: Boolean)

private class PlankRow(val key: String, val name: String?, val units: List<ShelfUnit>, val onEdit: (() -> Unit)?, val scroll: Boolean)

internal fun <T> placeProps(content: List<T>, tune: KitTune, seed: String, prop: (Int) -> T): List<T> {
    val base = seed.hashCode() and 0x7FFFFFFF
    var placed = 0
    fun next(): T = prop(placed).also { placed++ }
    if (content.isEmpty()) return listOf(next())
    val n = tune.propEvery.coerceIn(1, 6)
    val out = mutableListOf<T>()
    when (PropPlace.of(tune.propPlace)) {
        PropPlace.Between, PropPlace.Custom -> {
            content.forEachIndexed { i, u ->
                out += u
                if ((i + 1) % n == 0 || (content.size < n && i == content.lastIndex)) out += next()
            }
        }
        PropPlace.Scatter -> {
            val r = kotlin.random.Random(base)
            content.forEachIndexed { i, u ->
                if (i > 0 && r.nextInt(n + 1) == 0) out += next()
                out += u
            }
            if (placed == 0) out.add(r.nextInt(out.size + 1), next())
        }
        PropPlace.Edges -> {
            out += next()
            out += content
            out += next()
        }
        PropPlace.Start -> {
            out += next()
            out += content
        }
        PropPlace.End -> {
            out += content
            out += next()
        }
    }
    return out
}

private fun withProps(content: List<ShelfUnit>, tune: KitTune, seed: String): List<ShelfUnit> {
    val base = seed.hashCode() and 0x7FFFFFFF
    return placeProps(content, tune, seed) { k -> ShelfUnit.Prop(base + k * 7, "p:$seed:$k") }
}

private fun arrange(books: List<Book>, onAdd: (() -> Unit)?, live: Boolean, decor: Decor = Decor.None, seed: String = "", behave: Boolean = true, tune: KitTune = KitTune()): List<ShelfUnit> {
    if (!live) {
        val units = books.map<Book, ShelfUnit> { ShelfUnit.Standing(it, lean = false) }.toMutableList()
        if (onAdd != null) units += ShelfUnit.Add(onAdd)
        return units
    }
    val props = tune.props && propCount(decor) > 0
    val content: List<ShelfUnit> = if (!behave) {
        books.map { ShelfUnit.Standing(it, lean = false) }
    } else {
        val standing = books.filter { it.status != ReadStatus.Finished }
            .sortedWith(compareBy<Book> { if (it.status == ReadStatus.Reading) 0 else 1 }.thenByDescending { it.lastReadAt ?: it.addedAt })
        val lying = books.filter { it.status == ReadStatus.Finished }.sortedByDescending { it.finishedAt ?: it.addedAt }
        standing.mapIndexed { i, b -> ShelfUnit.Standing(b, lean = standing.size >= 3 && i == standing.lastIndex) } +
            lying.chunked(4).map { ShelfUnit.Stack(it) }
    }
    val pins = if (PropPlace.of(tune.propPlace) == PropPlace.Custom) tune.pins[seed] else null
    val units = when {
        !props -> content
        pins != null -> pinned(content, pins)
        else -> withProps(content, tune, seed)
    }.toMutableList()
    if (onAdd != null) units += ShelfUnit.Add(onAdd)
    return units
}

private fun anchors(units: List<ShelfUnit>): Map<ShelfUnit, String> {
    var stack = 0
    val out = HashMap<ShelfUnit, String>()
    units.forEach { u ->
        when (u) {
            is ShelfUnit.Standing -> out[u] = "b:" + u.book.id
            is ShelfUnit.Stack -> out[u] = "stack:" + stack++
            else -> Unit
        }
    }
    return out
}

private fun pinned(content: List<ShelfUnit>, pins: List<PropPin>): List<ShelfUnit> {
    val names = anchors(content)
    val known = names.values.toSet()
    val by = pins.groupBy { if (it.after.isEmpty() || it.after in known) it.after else "?" }
    fun props(key: String) = by[key].orEmpty().map { ShelfUnit.Prop(it.variant, "p:" + it.id) }
    val out = mutableListOf<ShelfUnit>()
    out += props("")
    content.forEach { u ->
        out += u
        names[u]?.let { out += props(it) }
    }
    out += props("?")
    return out
}

private fun pinsOf(units: List<ShelfUnit>): List<PropPin> {
    val names = anchors(units)
    var after = ""
    val pins = mutableListOf<PropPin>()
    units.forEach { u ->
        when (u) {
            is ShelfUnit.Prop -> pins += PropPin(u.key.removePrefix("p:"), u.index, after)
            else -> names[u]?.let { after = it }
        }
    }
    return pins
}

private fun unitWidth(u: ShelfUnit): Float = when (u) {
    is ShelfUnit.Standing -> 97f + if (u.lean) 14f else 0f
    is ShelfUnit.Stack -> 160f
    is ShelfUnit.Add -> 84f
    is ShelfUnit.Prop -> 65f
}

private fun rows(planks: List<PlankSpec>, width: Float, live: Boolean, decor: Decor, behave: Boolean, tune: KitTune): List<PlankRow> = planks.flatMap { p ->
    val units = arrange(p.books, p.onAdd, live, decor, p.key, behave, tune)
    if (!p.wrap) return@flatMap listOf(PlankRow(p.key, p.name, units, p.onEdit, scroll = true))
    val out = mutableListOf<MutableList<ShelfUnit>>(mutableListOf())
    var used = 0f
    units.forEach { u ->
        val w = unitWidth(u)
        if (used + w > width && out.last().isNotEmpty()) {
            out += mutableListOf<ShelfUnit>()
            used = 0f
        }
        out.last() += u
        used += w
    }
    out.mapIndexed { i, r -> PlankRow(p.key + "#" + i, if (i == 0) p.name else null, r, p.onEdit, scroll = false) }
}

@Composable
fun BookcaseView(
    planks: List<PlankSpec>,
    onBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
    animationKey: Any? = null,
    live: Boolean = true,
    behave: Boolean = true,
    onMove: ((Book, String, String) -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize().bookcaseWall()) {
        val width = maxWidth.value - 44f
        val decor = AppStyle.kit.decor
        val tune = AppStyle.current.tune()
        val drag = remember { ShelfDrag() }
        val propFeedback = LocalFeedback.current
        androidx.compose.runtime.LaunchedEffect(decor, live) { if (live) propFeedback?.preloadProps(decor) }
        val effective = drag.preview?.let { tune.copy(propPlace = PropPlace.Custom.id, pins = it) } ?: tune
        val list = remember(planks, width, live, decor, behave, effective) { rows(planks, width, live, decor, behave, effective) }
        drag.layout = {
            val out = LinkedHashMap<String, MutableList<ShelfUnit>>()
            list.forEach { r -> out.getOrPut(r.key.substringBefore('#')) { mutableListOf() }.addAll(r.units.filter { it !is ShelfUnit.Add }) }
            out
        }
        val styleStore = (androidx.compose.ui.platform.LocalContext.current.applicationContext as App).style
        drag.onPropDrop = { pins -> styleStore.update { st -> st.withTune(st.kit, st.tune().copy(propPlace = PropPlace.Custom.id, pins = pins)) } }
        val slack = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.toPx() }
        val keepMargin = with(androidx.compose.ui.platform.LocalDensity.current) { 22.dp.toPx() }
        val tickFeedback = LocalFeedback.current
        val rowOf = remember(list) {
            val m = HashMap<String, Int>()
            list.forEachIndexed { i, r -> r.units.forEach { m[it.key] = i } }
            m
        }
        val listStateHolder = rememberLazyListState()
        drag.slotAt = slotAt@{
            val unit = drag.prop ?: return@slotAt null
            val p = drag.pointer ?: return@slotAt null
            val plank = drag.target(drag.lastSlot?.first, keepMargin) ?: return@slotAt null
            val seq = drag.layout()[plank]?.filter { it.key != unit.key } ?: return@slotAt null
            val first = listStateHolder.firstVisibleItemIndex
            var idx = 0
            seq.forEach { u ->
                val r = drag.slots[u.key]?.takeIf { it.first == plank }?.second
                if (r == null) {
                    if ((rowOf[u.key] ?: Int.MAX_VALUE) < first) idx++
                    return@forEach
                }
                val sameRow = p.y >= r.top - slack && p.y <= r.bottom + slack
                if (if (sameRow) r.center.x < p.x else r.bottom < p.y) idx++
            }
            plank to idx
        }
        drag.applySlot = applySlot@{ slot ->
            val unit = drag.prop ?: return@applySlot false
            if (drag.lastSlot == slot) return@applySlot false
            val base = drag.preview ?: return@applySlot false
            drag.lastSlot = slot
            val lay = drag.layout()
            val (plank, idx) = slot
            val seq = lay[plank]?.filter { it.key != unit.key } ?: return@applySlot false
            val next = HashMap(base)
            lay.entries.firstOrNull { e -> e.key != plank && e.value.any { it.key == unit.key } }?.let { from ->
                next[from.key] = pinsOf(from.value.filter { it.key != unit.key })
            }
            next[plank] = pinsOf(seq.toMutableList().apply { add(idx.coerceIn(0, size), unit) })
            if (next == base) return@applySlot false
            drag.preview = next
            drag.appliedAt = System.currentTimeMillis()
            tickFeedback?.play(FeedbackSound.Tick)
            true
        }
        androidx.compose.runtime.LaunchedEffect(drag) {
            while (true) {
                androidx.compose.runtime.snapshotFlow { drag.phase == 1 && drag.prop != null }.first { it }
                var cand: Pair<String, Int>? = null
                var candAt = 0L
                while (drag.phase == 1 && drag.prop != null) {
                    val now = androidx.compose.runtime.withFrameMillis { it }
                    if (drag.autoscroll) {
                        cand = null
                        continue
                    }
                    val c = drag.slotAt()
                    if (c != cand) {
                        cand = c
                        candAt = now
                        continue
                    }
                    if (c == null || now - candAt < 70 || System.currentTimeMillis() - drag.appliedAt < 170) continue
                    drag.applySlot(c)
                }
            }
        }
        val plankOrder = planks.map { it.key }
        val lastOrder = remember { arrayOf<List<String>?>(null) }
        if (lastOrder[0] != null && lastOrder[0] != plankOrder && lastOrder[0]!!.toSet() == plankOrder.toSet()) {
            listStateHolder.requestScrollToItem(listStateHolder.firstVisibleItemIndex, listStateHolder.firstVisibleItemScrollOffset)
        }
        lastOrder[0] = plankOrder
        val seen = remember(animationKey, live) { ShelfSeenStore.of("$animationKey|$live") }
        val listState = listStateHolder
        androidx.compose.runtime.LaunchedEffect(listState, seen) {
            var before = emptySet<Any>()
            androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet() }.collect { now ->
                (before - now).forEach { seen.remove("row:$it") }
                before = now
            }
        }
        val move by androidx.compose.runtime.rememberUpdatedState(onMove)
        drag.enabled = onMove != null && planks.size > 1
        val feedback = LocalFeedback.current
        drag.scrolling = { listStateHolder.isScrollInProgress }
        drag.onDrop = { book, from, to -> move?.invoke(book, from, to) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val zone = with(density) { 44.dp.toPx() }
        val bottomInset = with(density) { 92.dp.toPx() }
        val maxSpeed = with(density) { 0.42.dp.toPx() }
        androidx.compose.runtime.LaunchedEffect(drag, listState) {
            while (true) {
                androidx.compose.runtime.snapshotFlow { drag.phase }.first { it == 1 }
                var armed = false
                var speed = 0f
                var since = 0L
                var lastDir = 0
                var last = 0L
                val delay = if (drag.prop != null) 420L else 280L
                while (drag.phase == 1) {
                    val now = androidx.compose.runtime.withFrameMillis { it }
                    val dt = if (last == 0L) 16f else (now - last).coerceIn(1L, 48L).toFloat()
                    last = now
                    val p = drag.pointer
                    val r = drag.root
                    if (p == null || r == null || !r.isAttached) {
                        speed = 0f
                        drag.autoscroll = false
                        continue
                    }
                    val local = r.windowToLocal(p)
                    val top = zone
                    val bottom = r.size.height - bottomInset - zone
                    val want = when {
                        local.y < top -> -((top - local.y) / zone).coerceIn(0.15f, 1f)
                        local.y > bottom -> ((local.y - bottom) / zone).coerceIn(0.15f, 1f)
                        else -> 0f
                    }
                    val dir = kotlin.math.sign(want).toInt()
                    if (dir == 0) armed = true
                    if (dir != lastDir) {
                        since = now
                        lastDir = dir
                    }
                    val ramp = ((now - since - delay).coerceAtLeast(0L) / 600f).coerceAtMost(1f)
                    val goal = if (armed && dir != 0) want * kotlin.math.abs(want) * maxSpeed * ramp else 0f
                    speed += (goal - speed) * (1f - kotlin.math.exp(-dt / 110f))
                    val step = speed * dt
                    val moved = if (kotlin.math.abs(step) > 0.2f) listState.dispatchRawDelta(step) else 0f
                    drag.autoscroll = kotlin.math.abs(moved) > 0.1f
                }
                drag.autoscroll = false
            }
        }
        androidx.compose.runtime.key(animationKey, live) {
        val shelfState = if (live) rememberShelfState() else LocalShelfState.current
        val kitMoment = if (live) rememberKitMoment() else null
        androidx.compose.runtime.CompositionLocalProvider(LocalShelfSeen provides seen, LocalShelfState provides shelfState, LocalKitMoment provides kitMoment, LocalShelfDrag provides drag) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onGloballyPositioned { drag.root = it }
                    .pointerInput(drag) {
                        awaitPointerEventScope {
                            var id: androidx.compose.ui.input.pointer.PointerId? = null
                            while (true) {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                if (drag.phase != 1) {
                                    id = null
                                    continue
                                }
                                val change = id?.let { i -> event.changes.firstOrNull { it.id == i } }
                                    ?: event.changes.firstOrNull { it.pressed || it.previousPressed }
                                    ?: continue
                                id = change.id
                                val r = drag.root
                                if (r != null && r.isAttached) drag.pointer = r.localToWindow(change.position)
                                event.changes.forEach { it.consume() }
                                if (!change.pressed) {
                                    drag.finish()
                                    id = null
                                }
                            }
                        }
                    },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    itemsIndexed(list, key = { _, r -> r.key }) { index, row ->
                        Box(
                            Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 320f, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold),
                            ),
                        ) { Shelf(row, index, onBook) }
                    }
                    if (footer != null) item(key = "footer") { footer() }
                }
                DragGhost(drag)
                PropGhost(drag)
            }
        }
        }
    }
}

private object ShelfSeenStore {
    private val sets = HashMap<String, MutableSet<Any>>()
    fun of(key: String): MutableSet<Any> = sets.getOrPut(key) { java.util.Collections.synchronizedSet(HashSet()) }
}

private class ShelfDrag {
    var enabled = false
    var book by mutableStateOf<Book?>(null)
    var phase by mutableStateOf(0)
    var from: String = ""
    var landPlank by mutableStateOf("")
    var pointer by mutableStateOf<Offset?>(null)
    var grab: Offset = Offset.Zero
    var dest by mutableStateOf<Offset?>(null)
    var fromSpec: LyingSpec? = null
    var destSpec by mutableStateOf<LyingSpec?>(null)
    var root: androidx.compose.ui.layout.LayoutCoordinates? = null
    var onDrop: (Book, String, String) -> Unit = { _, _, _ -> }
    var scrolling: () -> Boolean = { false }
    val planks = HashMap<String, androidx.compose.ui.geometry.Rect>()
    var prop by mutableStateOf<ShelfUnit.Prop?>(null)
    var preview by mutableStateOf<Map<String, List<PropPin>>?>(null)
    var snapshot: Map<String, List<PropPin>>? = null
    var lastSlot: Pair<String, Int>? = null
    val slots = HashMap<String, Pair<String, androidx.compose.ui.geometry.Rect>>()
    val owners = HashMap<String, Any>()
    var autoscroll = false
    var appliedAt = 0L
    var slotAt: () -> Pair<String, Int>? = { null }
    var applySlot: (Pair<String, Int>) -> Boolean = { false }
    var layout: () -> Map<String, List<ShelfUnit>> = { emptyMap() }
    var onPropDrop: (Map<String, List<PropPin>>) -> Unit = {}

    fun target(keep: String? = null, margin: Float = 0f): String? {
        val p = pointer ?: return null
        if (keep != null && planks.any { (key, r) -> key.substringBefore('#') == keep && p.y >= r.top - margin && p.y <= r.bottom + margin }) return keep
        var best: String? = null
        var dist = Float.MAX_VALUE
        planks.forEach { (row, r) ->
            val key = row.substringBefore('#')
            val d = when {
                p.y < r.top -> r.top - p.y
                p.y > r.bottom -> p.y - r.bottom
                else -> 0f
            }
            if (d < dist) {
                dist = d
                best = key
            }
        }
        return if (dist < 60f) best else null
    }

    fun finish() {
        if (prop != null) {
            if (phase != 1) return
            slotAt()?.let { c -> if (autoscroll || System.currentTimeMillis() - appliedAt >= 170) applySlot(c) }
            val next = preview
            if (next != null && next != snapshot) onPropDrop(next)
            dest = null
            phase = 2
            return
        }
        val b = book ?: return
        if (phase != 1) return
        val target = target()
        val moved = target != null && target != from
        landPlank = if (moved) target!! else from
        dest = null
        destSpec = null
        phase = 2
        if (moved) onDrop(b, from, target!!)
    }

    fun hidden(id: String, plank: String): Boolean = book?.id == id && when (phase) {
        1 -> plank == from
        2 -> plank == landPlank
        else -> false
    }
}

private val LocalShelfDrag = androidx.compose.runtime.staticCompositionLocalOf<ShelfDrag?> { null }

private fun Modifier.slot(key: String, plank: String): Modifier = composed {
    val drag = LocalShelfDrag.current ?: return@composed this
    val owner = remember { Any() }
    androidx.compose.runtime.DisposableEffect(key) {
        onDispose {
            if (drag.owners[key] === owner) {
                drag.owners.remove(key)
                drag.slots.remove(key)
            }
        }
    }
    this.onGloballyPositioned { c ->
        if (c.isAttached) {
            drag.slots[key] = plank to c.boundsInWindow()
            drag.owners[key] = owner
        }
    }
}

@Composable
private fun Modifier.propDraggable(unit: ShelfUnit.Prop, plank: String): Modifier {
    val drag = LocalShelfDrag.current ?: return this
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val feedback = LocalFeedback.current
    val coords = remember { arrayOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val dragging = drag.prop?.key == unit.key && drag.phase != 0
    val was = remember(unit.key) { booleanArrayOf(false) }
    if (dragging) was[0] = true
    val settle = remember(unit.key) { androidx.compose.animation.core.Animatable(1f) }
    val decor = AppStyle.kit.decor
    val kind = propCount(decor).coerceAtLeast(1).let { n -> ((unit.index % n) + n) % n }
    androidx.compose.runtime.LaunchedEffect(dragging) {
        if (!dragging && was[0]) {
            was[0] = false
            feedback?.prop(decor, kind, place = true)
            settle.snapTo(0.84f)
            settle.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 620f))
        }
    }
    return this
        .slot(unit.key, plank)
        .onGloballyPositioned { coords[0] = it }
        .graphicsLayer {
            alpha = if (dragging) 0f else 1f
            val k = settle.value
            scaleY = k
            scaleX = 1f + (1f - k) * 0.5f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        }
        .pointerInput(unit.key, plank) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val c = coords[0] ?: return@detectDragGesturesAfterLongPress
                    if (drag.phase != 0 || drag.scrolling()) return@detectDragGesturesAfterLongPress
                    val snap = drag.layout().mapValues { pinsOf(it.value) }
                    drag.snapshot = snap
                    drag.preview = snap
                    drag.lastSlot = null
                    drag.grab = offset
                    drag.from = plank
                    drag.dest = null
                    drag.pointer = c.positionInWindow() + offset
                    drag.prop = unit
                    drag.phase = 1
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    feedback?.prop(decor, kind, place = false)
                },
                onDragEnd = { drag.finish() },
                onDragCancel = {},
            ) { change, _ -> change.consume() }
        }
}

@Composable
private fun PropGhost(drag: ShelfDrag) {
    val unit = drag.prop ?: return
    val root = drag.root
    val decor = AppStyle.kit.decor
    val n = propCount(decor).coerceAtLeast(1)
    val kind = ((unit.index % n) + n) % n
    val pos = remember(unit.key) { androidx.compose.animation.core.Animatable(Offset.Zero, Offset.VectorConverter) }
    val lift = remember(unit.key) { androidx.compose.animation.core.Animatable(0f) }
    val held = remember(unit.key) { arrayOf(Offset.Zero) }
    val flying = remember(unit.key) { booleanArrayOf(false) }
    val fade = remember(unit.key) { androidx.compose.animation.core.Animatable(1f) }
    val sway = androidx.compose.animation.core.rememberInfiniteTransition(label = "prop-sway")
    val t by sway.animateFloat(
        0f,
        (2 * Math.PI).toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "prop-t",
    )
    val amp = listOf(6f, 3.5f, 9f, 4f)[kind % 4]
    val speed = listOf(1f, 2f, 1f, 1f)[kind % 4]
    val bob = kind % 4 == 3
    val phase = drag.phase
    androidx.compose.runtime.LaunchedEffect(unit.key, phase) {
        if (phase == 1) {
            lift.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 480f))
        }
        if (phase == 2) {
            pos.snapTo(held[0])
            flying[0] = true
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            fun destLocal(): Offset? {
                val d = drag.slots[unit.key]?.second ?: return null
                val r = drag.root?.takeIf { it.isAttached } ?: return null
                val box = r.boundsInWindow()
                if (d.bottom < box.top || d.top > box.bottom) return null
                return r.windowToLocal(d.topLeft)
            }
            val first = destLocal()
            if (first != null && !drag.scrolling()) {
                val spec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 520f, visibilityThreshold = Offset(1f, 1f))
                kotlinx.coroutines.coroutineScope {
                    val flight = launch {
                        kotlinx.coroutines.coroutineScope {
                            launch { lift.animateTo(0f, Motion.tween(220)) }
                            var target: Offset = first
                            var job = launch { pos.animateTo(target, spec) }
                            val until = System.nanoTime() + 900_000_000L
                            while (job.isActive && System.nanoTime() < until) {
                                androidx.compose.runtime.withFrameNanos { }
                                val d = destLocal() ?: break
                                if ((d - target).getDistance() > 1f) {
                                    target = d
                                    job.cancel()
                                    job = launch { pos.animateTo(target, spec) }
                                }
                            }
                            job.join()
                        }
                    }
                    val watch = launch {
                        androidx.compose.runtime.snapshotFlow { drag.scrolling() }.first { it }
                        flight.cancel()
                    }
                    flight.join()
                    watch.cancel()
                }
            } else {
                kotlinx.coroutines.coroutineScope {
                    launch { lift.animateTo(0f, Motion.tween(200)) }
                    launch { fade.animateTo(0f, Motion.tween(200)) }
                }
            }
            drag.phase = 0
            drag.prop = null
            drag.preview = null
            drag.snapshot = null
            drag.pointer = null
        }
    }
    Box(
        Modifier.graphicsLayer {
            val pointer = drag.pointer
            val at = if (drag.phase == 1 && pointer != null && root != null && root.isAttached) {
                (root.windowToLocal(pointer) - drag.grab).also { held[0] = it }
            } else if (flying[0]) {
                pos.value
            } else {
                held[0]
            }
            translationX = at.x
            translationY = at.y + if (bob) kotlin.math.sin(t * 2f) * 3.dp.toPx() * lift.value else 0f
            val sc = 1f + 0.16f * lift.value
            alpha = fade.value
            scaleX = sc
            scaleY = sc
            rotationZ = kotlin.math.sin(t * speed) * amp * lift.value
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        },
    ) {
        ShelfProp(decor, unit.index)
    }
}

@Composable
private fun DragGhost(drag: ShelfDrag) {
    val book = drag.book ?: return
    val root = drag.root
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pos = remember(book.id) { androidx.compose.animation.core.Animatable(Offset.Zero, Offset.VectorConverter) }
    val lift = remember(book.id) { androidx.compose.animation.core.Animatable(0f) }
    val fade = remember(book.id) { androidx.compose.animation.core.Animatable(1f) }
    val form = remember(book.id) { androidx.compose.animation.core.Animatable(if (drag.fromSpec != null) 0f else 1f) }
    val phase = drag.phase
    val held = remember(book.id) { arrayOf(Offset.Zero) }
    val snapped = remember(book.id) { booleanArrayOf(false) }
    val spec = (if (phase == 2) drag.destSpec else null) ?: drag.fromSpec
    val coverW = with(density) { BookWidth.toPx() }
    val coverH = coverW * 1.5f
    val spineW = with(density) { (spec?.width ?: BookWidth).toPx() }
    val spineH = with(density) { (SpineHeight + if (spec?.top == true) 4.dp else 0.dp).toPx() }
    val from = drag.fromSpec
    val fromW = with(density) { (from?.width ?: BookWidth).toPx() }
    val fromH = with(density) { (SpineHeight + if (from?.top == true) 4.dp else 0.dp).toPx() }
    androidx.compose.runtime.LaunchedEffect(book.id, phase) {
        if (phase == 1) {
            fade.snapTo(1f)
            kotlinx.coroutines.coroutineScope {
                if (drag.fromSpec != null) launch { form.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 260f)) }
                launch { lift.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 420f)) }
            }
        }
        if (phase == 2) {
            pos.snapTo(held[0])
            snapped[0] = true
            val dest = kotlinx.coroutines.withTimeoutOrNull(520) {
                androidx.compose.runtime.snapshotFlow { drag.dest }.first { it != null }
            }
            val r = drag.root
            if (dest != null && r != null && r.isAttached && !drag.scrolling()) {
                val local = r.windowToLocal(dest)
                val lying = drag.destSpec != null
                kotlinx.coroutines.coroutineScope {
                    val flight = launch {
                        kotlinx.coroutines.coroutineScope {
                            launch {
                                pos.animateTo(local, androidx.compose.animation.core.spring(dampingRatio = 0.92f, stiffness = 560f, visibilityThreshold = Offset(1.5f, 1.5f)))
                                var last = local
                                val until = System.nanoTime() + 120_000_000L
                                while (System.nanoTime() < until) {
                                    val now = drag.dest?.let { d -> drag.root?.takeIf { it.isAttached }?.windowToLocal(d) } ?: break
                                    if ((now - last).getDistance() < 0.5f) break
                                    last = now
                                    pos.snapTo(now)
                                    androidx.compose.runtime.withFrameNanos { }
                                }
                            }
                            launch { lift.animateTo(0f, Motion.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                            launch { form.animateTo(if (lying) 0f else 1f, Motion.tween(if (lying) 300 else 160, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                        }
                    }
                    val watch = launch {
                        androidx.compose.runtime.snapshotFlow { drag.scrolling() }.first { it }
                        flight.cancel()
                    }
                    flight.join()
                    watch.cancel()
                }
            } else {
                kotlinx.coroutines.coroutineScope {
                    launch { lift.animateTo(0f, Motion.tween(220)) }
                    launch { fade.animateTo(0f, Motion.tween(220)) }
                }
            }
            drag.phase = 0
            drag.book = null
            drag.dest = null
            drag.destSpec = null
            drag.pointer = null
        }
    }
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    Box(
        Modifier.graphicsLayer {
            val f = form.value
            val pointer = drag.pointer
            val at = if (drag.phase == 1 && pointer != null && root != null && root.isAttached) {
                val g = drag.grab
                val grab = if (from != null) Offset(lerp(g.x, g.x / fromW * coverW, f), lerp(g.y, g.y / fromH * coverH, f)) else g
                (root.windowToLocal(pointer) - grab).also { held[0] = it }
            } else if (snapped[0]) {
                pos.value
            } else {
                held[0]
            }
            translationX = at.x
            translationY = at.y
            alpha = fade.value
            val s = 1f + 0.08f * lift.value
            scaleX = s
            scaleY = s
            rotationZ = -4f * lift.value
        },
    ) {
        Box(
            Modifier.graphicsLayer {
                val f = form.value
                alpha = (f * 1.6f - 0.3f).coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                scaleX = lerp(spineW / coverW, 1f, f)
                scaleY = lerp(spineH / coverH, 1f, f)
                shadowElevation = 22.dp.toPx() * lift.value * f
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                clip = true
            },
        ) {
            BookCover(book.id, BookWidth)
        }
        if (spec != null) {
            Box(
                Modifier.graphicsLayer {
                    val f = form.value
                    alpha = (1.3f - f * 1.6f).coerceIn(0f, 1f)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = lerp(1f, coverW / spineW, f)
                    scaleY = lerp(1f, coverH / spineH, f)
                },
            ) {
                LyingFace(book, spec.width, spec.top, spec.pagesOut)
            }
        }
    }
}

@Composable
private fun Modifier.shelfDraggable(book: Book, plank: String, spec: LyingSpec? = null): Modifier {
    val drag = LocalShelfDrag.current ?: return this
    if (!drag.enabled) return this
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val feedback = LocalFeedback.current
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val hidden = drag.hidden(book.id, plank)
    val landing = drag.phase == 2 && drag.book?.id == book.id && drag.landPlank == plank
    val target = if (hidden) (if (drag.phase == 1) 0.22f else 0f) else 1f
    val dimAnim = remember(book.id, plank) { androidx.compose.animation.core.Animatable(target) }
    val wasLanding = remember(book.id, plank) { booleanArrayOf(false) }
    if (landing) wasLanding[0] = true
    val instant = !hidden && wasLanding[0]
    val settle = remember(book.id, plank) { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(target, landing) {
        when {
            landing -> dimAnim.snapTo(0f)
            instant -> {
                dimAnim.snapTo(1f)
                wasLanding[0] = false
                feedback?.play(FeedbackSound.Place)
                settle.snapTo(0.94f)
                settle.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 900f))
            }
            else -> dimAnim.animateTo(target, Motion.tween(if (hidden) 160 else 220))
        }
    }
    val dim = if (instant) 1f else dimAnim.value
    return this
        .onGloballyPositioned { c ->
            coords = c
            if (landing) {
                drag.destSpec = spec
                drag.dest = c.positionInWindow()
            }
        }
        .graphicsLayer {
            alpha = dim
            val k = settle.value
            scaleY = k
            scaleX = 1f + (1f - k) * 0.45f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        }
        .pointerInput(book.id, plank) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val c = coords ?: return@detectDragGesturesAfterLongPress
                    if (drag.phase != 0) return@detectDragGesturesAfterLongPress
                    drag.grab = offset
                    drag.from = plank
                    drag.fromSpec = spec
                    drag.destSpec = null
                    drag.dest = null
                    drag.pointer = c.positionInWindow() + offset
                    drag.book = book
                    drag.phase = 1
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    feedback?.play(FeedbackSound.Pick)
                },
                onDragEnd = { drag.finish() },
                onDragCancel = {},
            ) { change, _ -> change.consume() }
        }
}

private val LocalShelfSeen = androidx.compose.runtime.staticCompositionLocalOf<MutableSet<Any>> { mutableSetOf() }

@Composable
fun NewShelfButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        val shape = appCorner(16)
        Row(
            Modifier
                .liveClick(shape, sound = FeedbackSound.Open, onClick = onClick)
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

private fun Modifier.bookcaseWall(): Modifier = this.drawBehind {
    val decor = AppStyle.kit.decor
    if (decor != Decor.None && decor != Decor.Library) return@drawBehind
    val light = AppStyle.light
    val top = if (light) Color(0xFFE6D7C3) else if (decor == Decor.Library) Color(0xFF241509) else Color(0xFF1D1611)
    val bottom = if (light) Color(0xFFD8C5AC) else if (decor == Decor.Library) Color(0xFF150B05) else Color(0xFF110D0A)
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
private fun Shelf(row: PlankRow, index: Int, onBook: (Book) -> Unit) {
    val drag = LocalShelfDrag.current
    val plank = row.key.substringBefore('#')
    val target = drag?.book != null && drag.phase == 1 && drag.target() == plank && drag.from != plank
    val glow by androidx.compose.animation.core.animateFloatAsState(if (target) 1f else 0f, Motion.tween(180), label = "plank-target")
    if (drag != null) androidx.compose.runtime.DisposableEffect(row.key) { onDispose { drag.planks.remove(row.key) } }
    val accent = AppColors.Accent
    val seenRows = LocalShelfSeen.current
    val freshRow = remember(row.key) { ("row:" + row.key) !in seenRows && (drag == null || drag.phase == 0) }
    androidx.compose.runtime.LaunchedEffect(row.key) { seenRows += "row:" + row.key }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (freshRow) Modifier.scrollReveal() else Modifier)
            .onGloballyPositioned { c -> if (drag != null) drag.planks[row.key] = c.boundsInWindow() }
            .drawBehind {
                if (glow > 0f) {
                    drawRoundRect(
                        accent.copy(alpha = 0.14f * glow),
                        topLeft = Offset(8.dp.toPx(), 0f),
                        size = androidx.compose.ui.geometry.Size(size.width - 16.dp.toPx(), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                    )
                    drawRoundRect(
                        accent.copy(alpha = 0.6f * glow),
                        topLeft = Offset(8.dp.toPx(), 0f),
                        size = androidx.compose.ui.geometry.Size(size.width - 16.dp.toPx(), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    )
                }
            },
    ) {
        val content: @Composable (Int, ShelfUnit) -> Unit = { i, unit ->
            when (unit) {
                is ShelfUnit.Standing -> StandingBook(unit.book, unit.lean, Modifier.dropIn(index, i, unit.key).slot(unit.key, plank).shelfDraggable(unit.book, plank)) { onBook(unit.book) }
                is ShelfUnit.Stack -> BookStack(unit.books, Modifier.dropIn(index, i, unit.key).slot(unit.key, plank), plank, onBook)
                is ShelfUnit.Add -> Box(Modifier.dropIn(index, i, unit.key)) { AddSlot(unit.onAdd) }
                is ShelfUnit.Prop -> ShelfProp(AppStyle.kit.decor, unit.index, Modifier.dropIn(index, i, unit.key).propDraggable(unit, plank))
            }
        }
        if (row.scroll && row.units.size <= 80) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(BookWidth * 1.5f + 14.dp)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                row.units.forEachIndexed { i, unit ->
                    androidx.compose.runtime.key(unit.key) { Box(Modifier.animatePlacement()) { content(i, unit) } }
                }
            }
        } else if (row.scroll) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(BookWidth * 1.5f + 14.dp),
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                itemsIndexed(row.units, key = { _, u -> u.key }) { i, unit ->
                    Box(
                        Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 380f, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold),
                            fadeOutSpec = Motion.tween(180),
                        ),
                    ) { content(i, unit) }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(BookWidth * 1.5f + 14.dp).padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                row.units.forEachIndexed { i, unit -> androidx.compose.runtime.key(unit.key) { Box(Modifier.animatePlacement()) { content(i, unit) } } }
            }
        }
        Box(Modifier.plankIn(index, "plank:" + row.key)) { Plank(row.name, target, row.onEdit) }
    }
}

@Composable
private fun Modifier.animatePlacement(): Modifier {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val holder = remember { arrayOfNulls<androidx.compose.animation.core.Animatable<androidx.compose.ui.unit.IntOffset, androidx.compose.animation.core.AnimationVector2D>>(1) }
    val target = remember { arrayOf(androidx.compose.ui.unit.IntOffset.Zero) }
    val placed = remember { booleanArrayOf(false) }
    return this
        .onPlaced { c ->
            val p = c.positionInParent()
            target[0] = androidx.compose.ui.unit.IntOffset(p.x.toInt(), p.y.toInt())
            placed[0] = true
        }
        .offset {
            if (!placed[0]) return@offset androidx.compose.ui.unit.IntOffset.Zero
            val t = target[0]
            val anim = holder[0] ?: androidx.compose.animation.core.Animatable(t, androidx.compose.ui.unit.IntOffset.VectorConverter).also { holder[0] = it }
            if (anim.targetValue != t) {
                scope.launch {
                    anim.animateTo(t, androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 340f, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold))
                }
            }
            anim.value - t
        }
}

@Composable
private fun Modifier.dropIn(shelf: Int, item: Int, id: Any): Modifier {
    val seen = LocalShelfSeen.current
    val dragging = LocalShelfDrag.current?.let { it.phase != 0 } == true
    val fresh = remember { id !in seen && !dragging }
    val p = remember { androidx.compose.animation.core.Animatable(if (Motion.enabled && fresh) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        seen += id
        if (p.value < 1f) p.animateTo(1f, Motion.tween(480, 120 + shelf.coerceAtMost(4) * 90 + item.coerceAtMost(8) * 55, androidx.compose.animation.core.FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
    }
}

@Composable
private fun Modifier.plankIn(shelf: Int, id: Any): Modifier {
    val seen = LocalShelfSeen.current
    val dragging = LocalShelfDrag.current?.let { it.phase != 0 } == true
    val fresh = remember { id !in seen && !dragging }
    val p = remember { androidx.compose.animation.core.Animatable(if (Motion.enabled && fresh) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        seen += id
        if (p.value < 1f) p.animateTo(1f, Motion.tween(460, 40 + shelf.coerceAtMost(4) * 110, androidx.compose.animation.core.FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
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
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.96f, onLongClick = if (LocalShelfDrag.current?.enabled == true) null else onClick, onClick = onClick),
    ) {
        BookCover(book.id, BookWidth)
    }
}

@Composable
private fun BookStack(books: List<Book>, modifier: Modifier = Modifier, plank: String = "", onBook: (Book) -> Unit) {
    Column(modifier.padding(start = 6.dp, end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val order = books.reversed()
        order.forEachIndexed { i, b ->
            val h = b.id.hashCode() and 0x7FFFFFFF
            val width = 114.dp + (h % 26).dp
            val shift = (((h shr 5) and 0x0F) - 7).dp
            val pages = i > 0 && (h shr 9) % 3 == 0
            LyingBook(b, width, top = i == 0, pagesOut = pages, modifier = Modifier.offset(x = shift).shelfDraggable(b, plank, LyingSpec(width, i == 0, pages))) { onBook(b) }
        }
    }
}

@Composable
private fun LyingBook(book: Book, width: Dp, top: Boolean, pagesOut: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = appCorner(3)
    LyingFace(book, width, top, pagesOut, modifier.liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.97f, onLongClick = if (LocalShelfDrag.current?.enabled == true) null else onClick, onClick = onClick))
}

private class LyingSpec(val width: Dp, val top: Boolean, val pagesOut: Boolean)

@Composable
private fun LyingFace(book: Book, width: Dp, top: Boolean, pagesOut: Boolean, modifier: Modifier = Modifier) {
    val tones = rememberCoverTones(book)
    val main = tones[tones.size / 2]
    val board = 4.dp
    Column(modifier.width(width)) {
        if (top) {
            Box(
                Modifier.fillMaxWidth().height(board).drawBehind {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(5.dp.toPx(), 0f)
                        lineTo(size.width - 3.dp.toPx(), 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, Brush.horizontalGradient(tones.map { lighten(it, 0.28f) }))
                    drawPath(path, Color(0x22FFFFFF))
                },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(SpineHeight)
                .drawBehind {
                    if (pagesOut) {
                        val b = 3.dp.toPx()
                        drawRect(Brush.verticalGradient(listOf(Color(0xFFEDE3CC), Color(0xFFD2C3A3))), Offset(0f, b), androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height - 2 * b))
                        val line = Color(0x26000000)
                        var y = b + 2.dp.toPx()
                        while (y < size.height - b - 1f) {
                            drawLine(line, Offset(2.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), 0.7f)
                            y += 2.2.dp.toPx()
                        }
                        drawRect(main, size = androidx.compose.ui.geometry.Size(size.width, b))
                        drawRect(darken(main, 0.6f), Offset(0f, size.height - b), androidx.compose.ui.geometry.Size(size.width, b))
                        drawRect(darken(main, 0.75f), Offset(size.width - 4.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
                    } else {
                        drawRect(Brush.horizontalGradient(tones))
                        drawRect(Brush.verticalGradient(0f to Color(0x30FFFFFF), 0.32f to Color(0x14FFFFFF), 0.55f to Color(0x00000000), 1f to Color(0x66000000)))
                        val band = Color(0xA6E7B877)
                        val w = 2.dp.toPx()
                        listOf(7.dp.toPx(), 11.dp.toPx()).forEach { x ->
                            drawRect(band, Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                            drawRect(band, Offset(size.width - x - w, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                        }
                    }
                },
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
private fun Plank(name: String?, highlighted: Boolean, onEdit: (() -> Unit)?) {
    val light = AppStyle.light
    val decor = AppStyle.kit.decor
    val accent = AppColors.Accent
    val ink = AppColors.Text
    val wood = when {
        decor == Decor.Library -> if (light) listOf(Color(0xFF9A6A3C), Color(0xFF7A4E28), Color(0xFF5A3518)) else listOf(Color(0xFF7A4A24), Color(0xFF5E381A), Color(0xFF40240F))
        light -> listOf(Color(0xFFC9A375), Color(0xFFB08656), Color(0xFF8E6538))
        else -> listOf(Color(0xFFA8743F), Color(0xFF8A5A30), Color(0xFF6B4322))
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(20.dp)
                .then(if (decor == Decor.Comic) Modifier.hardShadow(shape, 4.dp) else Modifier)
                .then(if (onEdit != null) Modifier.liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.99f, onLongClick = onEdit, onClick = onEdit) else Modifier)
                .drawBehind {
                    when (decor) {
                        Decor.Zen -> {
                            val seg = 46.dp.toPx()
                            drawRoundRect(if (light) Color(0xFFA7B37A) else Color(0xFF6E7A48), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                            var x = seg
                            while (x < size.width) {
                                drawLine(if (light) Color(0xFF7D8A52) else Color(0xFF4E5A30), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                                x += seg
                            }
                            drawRect(Color.White.copy(alpha = 0.18f), Offset(0f, size.height * 0.18f), androidx.compose.ui.geometry.Size(size.width, size.height * 0.16f))
                        }
                        Decor.Comic -> {
                            drawRect(Color(0xFFFFCC00))
                            drawRect(Color(0xFF111111), style = Stroke(2.5.dp.toPx()))
                        }
                        Decor.News -> {
                            drawRect(ink, size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()))
                            drawRect(ink, Offset(0f, size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                            if (highlighted) drawRect(accent.copy(alpha = 0.12f))
                        }
                        Decor.Clay -> {
                            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                            drawRoundRect(if (highlighted) AppColors.AccentLight else accent, cornerRadius = r)
                            drawRoundRect(
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.18f))),
                                cornerRadius = r,
                            )
                        }
                        Decor.Sea -> {
                            val core = size.height * 0.55f
                            drawRoundRect(accent.copy(alpha = if (highlighted) 0.3f else 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                            drawRoundRect(
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = if (light) 0.85f else 0.35f), Color.White.copy(alpha = if (light) 0.35f else 0.08f))),
                                Offset(0f, (size.height - core) / 2),
                                androidx.compose.ui.geometry.Size(size.width, core),
                                androidx.compose.ui.geometry.CornerRadius(core / 2),
                            )
                        }
                        else -> {
                            drawRoundRect(Brush.verticalGradient(wood), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
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
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (name != null) {
                val plaque = decor == Decor.Library
                Text(
                    if (decor == Decor.News) name.uppercase() else name,
                    color = when {
                        plaque -> Color(0xFF3A2A12)
                        decor == Decor.Comic -> Color(0xFF111111)
                        decor == Decor.News || decor == Decor.Sea -> AppColors.Text
                        decor == Decor.Clay -> AppColors.OnAccent
                        decor == Decor.Zen -> if (light) Color(0xFF2B3A18) else Color(0xFFEDEFE0)
                        light -> Color(0xFF3A2612)
                        else -> Color(0xFFF7EBD8)
                    },
                    fontSize = 11.sp,
                    fontWeight = if (decor == Decor.Comic || decor == Decor.News || decor == Decor.Clay) FontWeight.ExtraBold else FontWeight.SemiBold,
                    letterSpacing = if (decor == Decor.News) 1.5.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .then(
                            when {
                                plaque -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFFF0D48A), Color(0xFFB8873F))), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).padding(horizontal = 10.dp)
                                else -> Modifier
                            },
                        ),
                )
            }
        }
        val moment = LocalKitMoment.current
        if (moment != null && decor != Decor.None && moment.season != Season.Summer) {
            val seed = remember(name) { (name ?: "shelf").hashCode() }
            val falling = (moment.season == Season.Autumn || moment.season == Season.Spring) && AppStyle.current.tune().particles
            val tick = throttledClock(falling, 3600)
            val motion = Motion.enabled
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(0.dp)) {
                val ph = 20.dp.toPx()
                translate(0f, -ph) {
                    plankSeason(decor, moment.season, light, seed, ph)
                    if (falling) {
                        tick.value
                        plankFall(decor, moment.season, seed, System.currentTimeMillis(), motion)
                    }
                }
            }
        }
        if (decor == Decor.None || decor == Decor.Library || decor == Decor.Zen || decor == Decor.Comic) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(12.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x80000000), Color(0x00000000)))),
            )
        }
    }
}

@Composable
private fun rememberCoverTones(book: Book): List<Color> {
    val app = LocalContext.current.applicationContext as App
    val fallback = remember(book.id) {
        val hues = listOf(0xFF3B5F7A, 0xFF8A3B2B, 0xFF5F7A4E, 0xFF7A4FA0, 0xFF2C2F5A, 0xFFB2763A, 0xFF1F6F8B, 0xFF6E2F3F)
        val c = Color(hues[(book.id.hashCode() and 0x7FFFFFFF) % hues.size])
        listOf(darken(c, 0.8f), c, lighten(c, 0.1f), c, darken(c, 0.8f))
    }
    val file = app.library.coverFile(book)
    val tones by produceState(CoverCache.peekTones(file) ?: fallback, file) {
        if (file == null || CoverCache.peekTones(file) != null) return@produceState
        val t = withContext(Dispatchers.IO) { CoverCache.load(file, 64)?.let { coverTones(it.asAndroidBitmap()) } }
        if (t != null) {
            CoverCache.putTones(file, t)
            value = t
        }
    }
    return tones
}

fun warmCovers(files: List<java.io.File>, grid: Boolean) {
    files.take(36).forEachIndexed { i, file ->
        if (!file.exists()) return@forEachIndexed
        CoverCache.load(file, 160)
        if (CoverCache.peekTones(file) == null) {
            CoverCache.load(file, 64)?.let { coverTones(it.asAndroidBitmap()) }?.let { CoverCache.putTones(file, it) }
        }
        if (grid && i < 12) CoverCache.load(file)
    }
}

private fun coverTones(bitmap: android.graphics.Bitmap): List<Color>? {
    if (bitmap.width == 0 || bitmap.height == 0) return null
    val bands = 7
    return (0 until bands).map { band ->
        val y0 = bitmap.height * band / bands
        val y1 = (bitmap.height * (band + 1) / bands).coerceAtLeast(y0 + 1)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        var y = y0
        while (y < y1) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
                x += 3
            }
            y += 2
        }
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / n.coerceAtLeast(1)).toInt(), (g / n.coerceAtLeast(1)).toInt(), (b / n.coerceAtLeast(1)).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.25f).coerceIn(0.12f, 0.85f)
        hsv[2] = hsv[2].coerceIn(0.22f, 0.82f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
}

private fun darken(c: Color, factor: Float): Color = Color(red = c.red * factor, green = c.green * factor, blue = c.blue * factor, alpha = c.alpha)

private fun lighten(c: Color, amount: Float): Color = Color(
    red = c.red + (1f - c.red) * amount,
    green = c.green + (1f - c.green) * amount,
    blue = c.blue + (1f - c.blue) * amount,
    alpha = c.alpha,
)

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
