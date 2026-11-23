package com.flerty.wikiflerty.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AutoImport {
    private const val EVERY = 30 * 60_000L
    private val lock = Mutex()
    private val _folder = MutableStateFlow<Uri?>(null)
    val folder: StateFlow<Uri?> = _folder
    @Volatile private var loaded = false

    data class Info(val count: Int = 0, val recent: List<String> = emptyList())

    private val _info = MutableStateFlow(Info())
    val info: StateFlow<Info> = _info
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _shelves = MutableStateFlow(false)
    val shelves: StateFlow<Boolean> = _shelves

    private fun prefs(c: Context) = c.getSharedPreferences("auto_import", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        _folder.value = p.getString("tree", null)?.let(Uri::parse)
        _shelves.value = p.getBoolean("folderShelves", false)
        _info.value = Info(p.getInt("count", 0), p.getString("recent", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
    }

    fun set(c: Context, tree: Uri?) {
        load(c)
        val old = _folder.value
        if (old != null && old != tree) runCatching { c.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs(c).edit().putString("tree", tree?.toString()).remove("seen").remove("count").remove("recent").remove("folders").remove("links").putLong("last", 0).apply()
        _info.value = Info()
        _folder.value = tree
    }

    suspend fun run(app: App, force: Boolean = false) = lock.withLock {
        load(app)
        val tree = _folder.value ?: return@withLock
        val p = prefs(app)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong("last", 0) < EVERY) return@withLock
        val granted = app.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        if (!granted) return@withLock
        p.edit().putLong("last", now).apply()
        _busy.value = true
        try {
            val known = app.library.books.value.map { it.title.lowercase().trim() }.toSet()
            val found = runCatching { BookScanner.scan(app, tree, known) }.getOrNull() ?: return@withLock
            val seen = p.getStringSet("seen", emptySet()).orEmpty()
            val fresh = found.filter { !it.known && it.uri.toString() !in seen }
            val recent = (fresh.map { it.name.substringBeforeLast('.') } + _info.value.recent).distinct().take(3)
            p.edit().putStringSet("seen", seen + found.map { it.uri.toString() }).putInt("count", found.size).putString("recent", recent.joinToString("\n")).apply()
            _info.value = Info(found.size, recent)
            val folders = shelfNames(found)
            p.edit().putString("folders", org.json.JSONObject(folders as Map<*, *>).toString()).apply()
            if (_shelves.value) placeAll(app, found, folders)
            if (fresh.isNotEmpty()) app.imports.add(fresh.map { it.uri })
        } finally {
            _busy.value = false
        }
    }

    fun setShelves(app: App, on: Boolean) {
        load(app)
        prefs(app).edit().putBoolean("folderShelves", on).apply()
        _shelves.value = on
        if (on) app.appScope.launch { run(app, force = true) }
    }

    private fun shelfNames(found: List<FoundBook>): Map<String, String> {
        val paths = found.map { it.folder }.filter { it.isNotBlank() }.distinct()
        val last = paths.groupBy { it.substringAfterLast('/') }
        val names = paths.associateWith { path ->
            val leaf = path.substringAfterLast('/')
            if ((last[leaf]?.size ?: 0) > 1 && path.contains('/')) path.substringBeforeLast('/').substringAfterLast('/') + " · " + leaf else leaf
        }
        return found.filter { it.folder.isNotBlank() }.associate { it.uri.toString() to names.getValue(it.folder) }
    }

    private fun linked(c: Context): MutableMap<String, String> {
        val j = runCatching { org.json.JSONObject(prefs(c).getString("links", "{}") ?: "{}") }.getOrElse { org.json.JSONObject() }
        return j.keys().asSequence().associateWith { j.optString(it) }.toMutableMap()
    }

    private suspend fun placeAll(app: App, found: List<FoundBook>, folders: Map<String, String>) {
        val links = linked(app)
        val books = app.library.books.value
        val byId = books.associateBy { it.id }
        val byTitle = books.groupBy { it.title.lowercase().trim() }
        val byPrint = books.filter { it.fingerprint != null }.associateBy { it.fingerprint!! }
        val used = mutableSetOf<String>()
        var hashed = 0
        var learned = false
        for (f in found) {
            val key = f.uri.toString()
            val shelf = folders[key] ?: continue
            var book = links[key]?.let { byId[it] } ?: byTitle[BookScanner.baseName(f.name)]?.singleOrNull()
            if (book == null && hashed < 600 && f.size in 1..(250L shl 20)) {
                hashed++
                book = digest(app, f.uri)?.let { byPrint[it] }
            }
            if (book == null) continue
            if (links[key] != book.id) {
                links[key] = book.id
                learned = true
            }
            used += shelf
            val fresh = app.library.books.value.firstOrNull { it.id == book.id } ?: book
            if (shelf !in fresh.shelves) app.library.setShelves(fresh.id, fresh.shelves + shelf)
        }
        if (learned) prefs(app).edit().putString("links", org.json.JSONObject(links as Map<*, *>).toString()).apply()
        register(app, used)
    }

    private fun digest(app: App, uri: Uri): String? = runCatching {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        app.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        } ?: return@runCatching null
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun register(app: App, names: Set<String>) {
        if (names.isEmpty()) return
        app.libraryPrefs.update { p -> if (names.all { it in p.shelves }) p else p.copy(shelves = (p.shelves + names).distinct()) }
    }

    suspend fun imported(app: App, uri: String, book: com.flerty.wikiflerty.data.Book) {
        load(app)
        val p = prefs(app)
        if (p.getString("tree", null) == null) return
        val links = linked(app)
        links[uri] = book.id
        p.edit().putString("links", org.json.JSONObject(links as Map<*, *>).toString()).apply()
        if (!_shelves.value) return
        val folders = runCatching { org.json.JSONObject(p.getString("folders", "{}") ?: "{}") }.getOrNull() ?: return
        val shelf = folders.optString(uri).takeIf { it.isNotBlank() } ?: return
        val fresh = app.library.books.value.firstOrNull { it.id == book.id } ?: book
        if (shelf !in fresh.shelves) app.library.setShelves(fresh.id, fresh.shelves + shelf)
        register(app, setOf(shelf))
    }
}

@Composable
internal fun AutoImportRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    AutoImport.load(context)
    val folder by AutoImport.folder.collectAsState()
    val info by AutoImport.info.collectAsState()
    val busy by AutoImport.busy.collectAsState()
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            AutoImport.set(context, uri)
            scope.launch { AutoImport.run(app, force = true) }
        }
    }
    val on = folder != null
    val line = AppColors.PanelBorder
    val fill = AppColors.Accent.copy(alpha = 0.07f).compositeOver(AppColors.PanelTop)
    val accentText = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f).compositeOver(AppColors.Accent)
    val cardShape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    Box(modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .liveClick(cardShape, sound = FeedbackSound.Open, pressedScale = 1f) { pick.launch(null) }
                .background(fill)
                .border(1.dp, line, cardShape)
                .animateContentSize(Motion.tween(280))
                .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    val names = if (on) info.recent else emptyList()
                    for (i in 0 until 4) {
                        val name = names.getOrNull(i)
                        AnimatedContent(
                            name,
                            transitionSpec = {
                                (fadeIn(Motion.tween(240, 60 * i)) + scaleIn(Motion.tween(280, 60 * i), initialScale = 0.6f)) togetherWith fadeOut(Motion.fade(140))
                            },
                            label = "auto-spine",
                        ) { n ->
                            if (n != null) {
                                Box(Modifier.size(22.dp, 30.dp).clip(RoundedCornerShape(4.dp)).background(generatedCoverColor(n, "")))
                            } else {
                                Box(Modifier.size(22.dp, 30.dp).drawBehind {
                                    drawRoundRect(
                                        line,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                                    )
                                })
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                    Text("Автоимпорт", color = AppColors.Text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    AnimatedContent(
                        when {
                            !on -> "Выбери папку"
                            busy -> "ищу новые…"
                            else -> "${info.count} " + StatsCalc.plural(info.count, "книга", "книги", "книг") + " · следит"
                        },
                        transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(140)) },
                        label = "auto-sub",
                    ) { t ->
                        Text(t, color = AppColors.Faint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.padding(top = 12.dp, bottom = 10.dp).fillMaxWidth().height(1.dp).background(AppColors.Line))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (on) "При запуске" else "Новые книги добавятся сами",
                    color = AppColors.Muted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(if (on) "Сменить ›" else "Выбрать ›", color = accentText, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (on) {
                val shelvesOn by AutoImport.shelves.collectAsState()
                Box(Modifier.padding(top = 10.dp, bottom = 8.dp).fillMaxWidth().height(1.dp).background(AppColors.Line))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Полки по папкам", color = AppColors.Text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("Каждая подпапка станет полкой", color = AppColors.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    KitSwitch(shelvesOn, { AutoImport.setShelves(app, it) })
                }
            }
        }
        val tabShape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp)
        Row(
            Modifier
                .padding(start = 14.dp)
                .offset(y = (-15).dp)
                .height(23.dp)
                .drawBehind {
                    val r = 9.dp.toPx()
                    val w = size.width
                    val h = size.height
                    drawRoundRect(fill, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), size = androidx.compose.ui.geometry.Size(w, h + r))
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, h + 1.dp.toPx())
                        lineTo(0f, r)
                        quadraticTo(0f, 0f, r, 0f)
                        lineTo(w - r, 0f)
                        quadraticTo(w, 0f, w, r)
                        lineTo(w, h + 1.dp.toPx())
                    }
                    drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
                .clip(tabShape)
                .animateContentSize(Motion.tween(260))
                .padding(start = 10.dp, end = if (on) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(AppIcons.Folder, null, tint = accentText, modifier = Modifier.size(13.dp))
            AnimatedContent(folder, transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(140)) }, label = "auto-tab") { f ->
                Text(if (f == null) "Папка" else folderLabel(f), color = accentText, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
            }
            androidx.compose.animation.AnimatedVisibility(on, enter = fadeIn(Motion.fade(200)) + scaleIn(Motion.tween(220), initialScale = 0.6f), exit = fadeOut(Motion.fade(140)) + scaleOut(Motion.tween(160), targetScale = 0.6f)) {
                Box(
                    Modifier.size(22.dp).liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.85f) { AutoImport.set(context, null) },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Close, "Выключить автоимпорт", tint = AppColors.Muted, modifier = Modifier.size(11.dp)) }
            }
        }
    }
}
