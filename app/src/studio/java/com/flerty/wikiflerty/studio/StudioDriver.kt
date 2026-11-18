package com.flerty.wikiflerty.studio

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class StudioDriver : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var touches: TouchLayer? = null

    override fun onServiceConnected() {
        instance = this
        runCatching {
            val layer = TouchLayer(this)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(layer, params)
            touches = layer
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        job?.cancel()
        touches?.let { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        if (instance === this) instance = null
        super.onDestroy()
    }

    val busy: Boolean get() = job?.isActive == true

    fun stop() {
        job?.cancel()
    }

    fun run(scenario: Scenario, showTouches: Boolean, onDone: (Boolean, String) -> Unit) {
        job?.cancel()
        touches?.show = showTouches
        marks = org.json.JSONArray()
        screen = ""
        startedAt = SystemClock.uptimeMillis()
        job = scope.launch {
            var failure: String? = null
            for ((i, step) in scenario.steps.withIndex()) {
                val begin = SystemClock.uptimeMillis()
                lastRect = null
                val ok = runCatching { perform(step) }.getOrElse { android.util.Log.w(TAG, "step ${i + 1} crashed", it); false }
                val end = SystemClock.uptimeMillis()
                marks.put(
                    org.json.JSONObject()
                        .put("i", i + 1)
                        .put("do", step.kind)
                        .put("text", step.text ?: "")
                        .put("start", begin - startedAt)
                        .put("end", end - startedAt)
                        .put("ok", ok)
                        .apply { lastRect?.let { put("rect", org.json.JSONArray().put(it.left).put(it.top).put(it.right).put(it.bottom)) } }
                        .apply { step.json.optString("mark").takeIf { it.isNotEmpty() }?.let { put("mark", it) } },
                )
                android.util.Log.i(TAG, "step ${i + 1} ${step.label()} ok=$ok ${end - begin}ms rect=$lastRect")
                if (!ok && !step.optional) {
                    val text = screenText()
                    screen += "=== шаг ${i + 1}: ${step.label()}\n$text\n"
                    android.util.Log.w(TAG, "failed at ${i + 1}")
                    if (failure == null) failure = "Шаг ${i + 1}: ${step.label()}" else failure += ", ${i + 1}"
                    if (!scenario.lenient) break
                }
                if (step.after > 0 && step.kind != "pause") delay(step.after)
            }
            onDone(failure == null, failure ?: "Готово")
        }
    }

    @Volatile
    var marks = org.json.JSONArray()
        private set

    @Volatile
    var startedAt = 0L
        private set

    @Volatile
    var screen = ""
        private set

    private var lastRect: Rect? = null

    fun screenText(): String {
        val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList()).ifEmpty { listOfNotNull(rootInActiveWindow) }
        val out = StringBuilder()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            node ?: return
            val t = node.text?.toString()?.trim().orEmpty()
            val d = node.contentDescription?.toString()?.trim().orEmpty()
            val r = Rect()
            node.getBoundsInScreen(r)
            val sw = resources.displayMetrics.widthPixels
            if ((t.isNotEmpty() || d.isNotEmpty()) && node.isVisibleToUser && r.right > 0 && r.left < sw && r.width() > 0) {
                out.append("  ".repeat(depth.coerceAtMost(12))).append(if (t.isNotEmpty()) "«${t.take(70)}»" else "").append(if (d.isNotEmpty()) " desc«$d»" else "")
                    .append(if (node.isClickable) " click" else "").append(" ").append(r.toShortString()).append('\n')
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        roots.forEach { out.append("[window ").append(it.packageName).append("]\n"); walk(it, 0) }
        return out.toString()
    }

    private suspend fun perform(step: Step): Boolean {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        return when (step.kind) {
            "launch" -> {
                val intent = Intent().setClassName(this, "com.flerty.wikiflerty.ui.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (step.json.optBoolean("fresh", false)) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)
                delay(step.ms.takeIf { it > 0 } ?: 1500)
                true
            }
            "pause" -> {
                delay(step.ms)
                true
            }
            "open" -> openBook(step.text.orEmpty())
            "wait" -> find(step) != null
            "gone" -> {
                val end = SystemClock.uptimeMillis() + step.timeout
                while (SystemClock.uptimeMillis() < end) {
                    if (findOnce(step) == null) return true
                    delay(300)
                }
                false
            }
            "tap" -> {
                if (step.json.optBoolean("click", false)) return click(step)
                val seek = step.json.optInt("seek", 0)
                if (seek > 0) {
                    repeat(seek) {
                        if (findOnce(step) != null) return@repeat
                        swipe(0.85f * w, 0.55f * h, 0.15f * w, 0.55f * h, 450)
                        delay(1300)
                    }
                }
                val r = find(step) ?: return false
                tap(r.exactCenterX() + step.dx * r.width(), r.exactCenterY() + step.dy * r.height(), 70)
            }
            "home" -> home()
            "dump" -> {
                screen += "=== dump ${step.text.orEmpty()}\n${screenText()}\n"
                true
            }
            "long" -> {
                val r = find(step) ?: return false
                tap(r.exactCenterX() + step.dx * r.width(), r.exactCenterY() + step.dy * r.height(), step.ms.takeIf { it > 0 } ?: 800)
            }
            "tapAt" -> tap(step.x * w, step.y * h, 70)
            "longAt" -> tap(step.x * w, step.y * h, step.ms.takeIf { it > 0 } ?: 800)
            "swipe" -> swipe(step.x * w, step.y * h, step.x2 * w, step.y2 * h, step.ms.takeIf { it > 0 } ?: 450)
            "drag" -> {
                val r = find(step) ?: return false
                swipe(r.exactCenterX(), r.exactCenterY(), step.x2 * w, step.y2 * h, step.ms.takeIf { it > 0 } ?: 900, hold = 600)
            }
            "scrollTo" -> {
                repeat(step.times.coerceAtLeast(1) * 6) {
                    if (findOnce(step) != null) return true
                    swipe(0.5f * w, step.y * h, 0.5f * w, step.y2 * h, step.ms.takeIf { it > 0 } ?: 500)
                    delay(700)
                }
                findOnce(step) != null
            }
            "show" -> {
                repeat(4) {
                    if (findOnce(step) != null) return true
                    tap(step.x * w, step.y * h, 70)
                    val end = SystemClock.uptimeMillis() + 2500
                    while (SystemClock.uptimeMillis() < end) {
                        if (findOnce(step) != null) return true
                        delay(250)
                    }
                }
                false
            }
            "hide" -> {
                repeat(4) {
                    if (findOnce(step) == null) return true
                    tap(step.x * w, step.y * h, 70)
                    delay(1500)
                }
                findOnce(step) == null
            }
            "backIf" -> {
                if (find(step) != null) performGlobalAction(GLOBAL_ACTION_BACK)
                true
            }
            "pick" -> {
                val name = step.text.orEmpty()
                val folder = step.json.optString("folder", "WikiFlerty Studio")
                if (find(sub(name, 3000)) == null) {
                    if (find(sub("Загрузки|Downloads|Скачанные", 800)) == null) click(sub("Показать корневые папки|Show roots", 2500, desc = true))
                    delay(900)
                    click(sub("Загрузки|Downloads|Скачанные", 3000))
                    delay(1300)
                    if (find(sub(name, 1500)) == null) {
                        click(sub(folder, 3000))
                        delay(1300)
                    }
                }
                click(sub(name, step.timeout))
            }
            "tree" -> {
                val folder = step.text ?: "WikiFlerty Studio"
                delay(1200)
                if (find(sub(folder, 1500)) == null) {
                    if (find(sub("Download", 1200)) == null) {
                        if (find(sub("Загрузки|Скачанные", 800)) == null) click(sub("Показать корневые папки|Show roots", 2500, desc = true))
                        delay(900)
                        click(sub("Загрузки|Скачанные|Downloads", 3000))
                    } else {
                        click(sub("Download", 1500))
                    }
                    delay(1500)
                }
                if (!click(sub(folder, 4000))) return false
                delay(1300)
                if (!click(sub("Использовать эту папку|Use this folder|Выбрать эту папку", 4000, exact = true))) return false
                delay(1500)
                click(sub("Разрешить|Allow", 4000, exact = true))
                delay(1200)
                find(sub("Использовать эту папку|Use this folder", 600, exact = true)) == null
            }
            "back" -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                true
            }
            "type" -> {
                val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
                val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text.orEmpty()) }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            "repeat" -> {
                repeat(step.times.coerceAtLeast(1)) {
                    swipe(step.x * w, step.y * h, step.x2 * w, step.y2 * h, step.ms.takeIf { it > 0 } ?: 450)
                    delay(step.json.optLong("gap", 900))
                }
                true
            }
            "record" -> {
                if (step.json.optString("state") == "stop") StudioRecorder.stop(this) else Unit
                true
            }
            else -> false
        }
    }

    private suspend fun find(step: Step): Rect? {
        val end = SystemClock.uptimeMillis() + step.timeout
        while (SystemClock.uptimeMillis() < end) {
            findOnce(step)?.let { lastRect = it; return it }
            delay(250)
        }
        return null
    }

    private var lastNode: AccessibilityNodeInfo? = null

    private fun findOnce(step: Step): Rect? {
        val wanted = step.text?.split('|')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: return null
        val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList()).ifEmpty { listOfNotNull(rootInActiveWindow) }
        val region = step.json.optJSONArray("region")
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        for (option in wanted) {
            val hits = ArrayList<Pair<Rect, AccessibilityNodeInfo>>()
            roots.forEach { root -> collect(root, option, step.exact, hits, step.json.optBoolean("desc", false), step.json.optBoolean("textonly", false)) }
            val onScreen = hits.filter { (r, _) -> r.exactCenterX() in 0f..w.toFloat() && r.exactCenterY() in 0f..h.toFloat() }
            val inside = if (region == null) onScreen else onScreen.filter { (r, _) ->
                val cx = r.exactCenterX() / w
                val cy = r.exactCenterY() / h
                cx >= region.optDouble(0) && cy >= region.optDouble(1) && cx <= region.optDouble(2) && cy <= region.optDouble(3)
            }
            inside.getOrNull(step.index)?.let { lastNode = it.second; return it.first }
        }
        return null
    }

    private fun collect(node: AccessibilityNodeInfo?, wanted: String, exact: Boolean, out: MutableList<Pair<Rect, AccessibilityNodeInfo>>, descOnly: Boolean = false, textOnly: Boolean = false) {
        node ?: return
        val values = when {
            descOnly -> listOfNotNull(node.contentDescription?.toString())
            textOnly -> listOfNotNull(node.text?.toString())
            else -> listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        }
        if (values.any { v -> val t = v.trim().lowercase(); if (exact) t == wanted else t.contains(wanted) } && node.isVisibleToUser) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) out += r to node
        }
        for (i in 0 until node.childCount) collect(node.getChild(i), wanted, exact, out, descOnly, textOnly)
    }

    private suspend fun click(step: Step): Boolean {
        val r = find(step) ?: return false
        var node = lastNode
        while (node != null && !node.isClickable) node = node.parent
        touches?.tap(r.exactCenterX(), r.exactCenterY(), 70)
        if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return tap(r.exactCenterX(), r.exactCenterY(), 70)
    }

    private fun sub(text: String, timeout: Long, desc: Boolean = false, exact: Boolean = false) =
        Step(org.json.JSONObject().put("text", text).put("timeout", timeout).put("desc", desc).put("textonly", !desc).put("exact", exact))

    private suspend fun home(): Boolean {
        val shelf = sub("Моя полка", 900)
        repeat(6) {
            if (find(shelf) != null) return true
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(900)
        }
        return find(sub("Моя полка", 3000)) != null
    }

    private suspend fun openBook(title: String): Boolean {
        val app = application as com.flerty.wikiflerty.App
        val wanted = title.trim().lowercase()
        val book = app.library.books.value.firstOrNull { it.title.trim().lowercase() == wanted }
            ?: app.library.books.value.firstOrNull { it.title.lowercase().contains(wanted) }
            ?: return false
        if (app.prepareReader(book).isFailure) return false
        startActivity(Intent().setClassName(this, "com.flerty.wikiflerty.reader.ReaderActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private suspend fun tap(x: Float, y: Float, duration: Long): Boolean {
        if (lastRect == null) lastRect = Rect(x.toInt() - 1, y.toInt() - 1, x.toInt() + 1, y.toInt() + 1)
        touches?.tap(x, y, duration)
        val path = Path().apply { moveTo(x, y) }
        return gesture(GestureDescription.StrokeDescription(path, 0, duration))
    }

    private suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long, hold: Long = 0): Boolean {
        touches?.swipe(x1, y1, x2, y2, duration + hold)
        if (hold > 0 && android.os.Build.VERSION.SDK_INT >= 26) return holdAndMove(x1, y1, x2, y2, duration, hold)
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return gesture(GestureDescription.StrokeDescription(path, 0, duration))
    }

    @androidx.annotation.RequiresApi(26)
    private suspend fun holdAndMove(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long, hold: Long): Boolean {
        val press = GestureDescription.StrokeDescription(Path().apply { moveTo(x1, y1) }, 0, hold, true)
        if (!gesture(press)) return false
        val move = press.continueStroke(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, 0, duration, false)
        return gesture(move)
    }

    private suspend fun gesture(stroke: GestureDescription.StrokeDescription): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
    }

    companion object {
        const val TAG = "WFStudio"

        @Volatile
        var instance: StudioDriver? = null
            private set

        fun enabled(context: Context): Boolean {
            val list = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return list.split(':').any { it.startsWith(context.packageName + "/") && it.endsWith("StudioDriver") }
        }

        fun toast(context: Context, text: String) {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }
}

private class TouchLayer(context: Context) : View(context) {
    var show = true
    private val density = context.resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFFFFFFFF.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    private class Mark(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val start: Long, val hold: Long, val swipe: Boolean)

    private val marks = ArrayList<Mark>()

    fun tap(x: Float, y: Float, hold: Long) {
        if (!show) return
        post {
            marks += Mark(x, y, x, y, SystemClock.uptimeMillis(), hold, false)
            invalidate()
        }
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        if (!show) return
        post {
            marks += Mark(x1, y1, x2, y2, SystemClock.uptimeMillis(), duration, true)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val it = marks.iterator()
        while (it.hasNext()) {
            val m = it.next()
            val life = m.hold + 420
            val t = (now - m.start).toFloat()
            if (t > life) {
                it.remove()
                continue
            }
            val moving = (t / m.hold.coerceAtLeast(1)).coerceIn(0f, 1f)
            val ease = 1f - (1f - moving) * (1f - moving)
            val x = m.x1 + (m.x2 - m.x1) * (if (m.swipe) ease else 1f)
            val y = m.y1 + (m.y2 - m.y1) * (if (m.swipe) ease else 1f)
            val fade = if (t < m.hold) 1f else 1f - (t - m.hold) / 420f
            val pop = (t / 220f).coerceIn(0f, 1f)
            val r = density * (16f + 8f * pop + if (t > m.hold) 12f * (1f - fade) else 0f)
            fill.alpha = (70 * fade).toInt()
            ring.alpha = (210 * fade).toInt()
            canvas.drawCircle(x, y, r, fill)
            canvas.drawCircle(x, y, r, ring)
        }
        if (marks.isNotEmpty()) postInvalidateOnAnimation()
    }
}
