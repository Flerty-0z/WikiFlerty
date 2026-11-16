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

    private fun prefs(c: Context) = c.getSharedPreferences("auto_import", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        _folder.value = prefs(c).getString("tree", null)?.let(Uri::parse)
    }

    fun set(c: Context, tree: Uri?) {
        load(c)
        val old = _folder.value
        if (old != null && old != tree) runCatching { c.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs(c).edit().putString("tree", tree?.toString()).remove("seen").putLong("last", 0).apply()
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
        val known = app.library.books.value.map { it.title.lowercase().trim() }.toSet()
        val found = runCatching { BookScanner.scan(app, tree, known) }.getOrNull() ?: return@withLock
        val seen = p.getStringSet("seen", emptySet()).orEmpty()
        val fresh = found.filter { !it.known && it.uri.toString() !in seen }
        p.edit().putStringSet("seen", seen + found.map { it.uri.toString() }).apply()
        if (fresh.isNotEmpty()) app.imports.add(fresh.map { it.uri })
    }
}

@Composable
internal fun AutoImportRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    AutoImport.load(context)
    val folder by AutoImport.folder.collectAsState()
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            AutoImport.set(context, uri)
            scope.launch { AutoImport.run(app, force = true) }
        }
    }
    val shape = appCorner(16)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { pick.launch(null) }
            .fieldSurface(shape)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val on = folder != null
        Box(
            Modifier.size(36.dp).background(AppColors.Accent.copy(alpha = if (on) 0.18f else 0.1f), appCorner(11)).border(1.dp, AppColors.Accent.copy(alpha = 0.35f), appCorner(11)),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.FolderSearch, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Автоимпорт из папки", color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AnimatedContent(folder, transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(140)) }, label = "auto-folder") { f ->
                Text(
                    if (f == null) "Выбери папку — новые книги добавятся сами" else "Папка «${folderLabel(f)}» · проверка при запуске",
                    color = AppColors.Faint,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(on, enter = fadeIn(Motion.fade(200)), exit = fadeOut(Motion.fade(160))) {
            Box(
                Modifier.size(40.dp).liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.88f) { AutoImport.set(context, null) },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, "Выключить автоимпорт", tint = AppColors.Muted, modifier = Modifier.size(16.dp)) }
        }
    }
}
