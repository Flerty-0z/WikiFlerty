package com.flerty.wikiflerty.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FoundBook(val uri: Uri, val name: String, val folder: String, val size: Long, val known: Boolean)

object BookScanner {
    private val extensions = listOf(".epub", ".fb2", ".fb2.zip", ".txt")
    private const val MIN_TXT = 10 * 1024L
    private const val MAX_DEPTH = 8

    suspend fun scan(context: Context, tree: Uri, knownNames: Set<String>): List<FoundBook> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FoundBook>()
        val root = DocumentsContract.getTreeDocumentId(tree)
        walk(context, tree, root, "", 0, knownNames, result)
        result.sortedWith(compareBy({ it.known }, { it.name.lowercase() }))
    }

    private fun walk(context: Context, tree: Uri, docId: String, path: String, depth: Int, known: Set<String>, out: MutableList<FoundBook>) {
        if (depth > MAX_DEPTH || out.size >= 2000) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val folders = mutableListOf<Pair<String, String>>()
        runCatching {
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    if (name.startsWith(".")) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders += id to name
                        continue
                    }
                    val lower = name.lowercase()
                    if (extensions.none { lower.endsWith(it) }) continue
                    if (lower.endsWith(".txt") && size < MIN_TXT) continue
                    val base = baseName(name)
                    out += FoundBook(
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                        name = name,
                        folder = path,
                        size = size,
                        known = base in known,
                    )
                }
            }
        }
        folders.forEach { (id, name) -> walk(context, tree, id, if (path.isEmpty()) name else "$path/$name", depth + 1, known, out) }
    }

    fun baseName(name: String): String =
        name.lowercase().removeSuffix(".zip").substringBeforeLast('.').removeSuffix(".fb2").trim()
}

private sealed interface ScanStage {
    data object Pick : ScanStage
    data object Scanning : ScanStage
    data class Found(val folder: String) : ScanStage
}

@Composable
fun FolderScanSheet(onDismiss: () -> Unit, onImport: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var stage by remember { mutableStateOf<ScanStage>(ScanStage.Pick) }
    var tree by remember { mutableStateOf<Uri?>(null) }
    val found = remember { mutableStateListOf<FoundBook>() }
    val checked = remember { mutableStateListOf<Uri>() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            tree = uri
        }
    }
    LaunchedEffect(tree) {
        val t = tree ?: return@LaunchedEffect
        stage = ScanStage.Scanning
        val known = app.library.books.value.map { it.title.lowercase().trim() }.toSet()
        val list = BookScanner.scan(context, t, known)
        found.clear()
        found.addAll(list)
        checked.clear()
        checked.addAll(list.filter { !it.known }.map { it.uri })
        stage = ScanStage.Found(folderLabel(t))
    }

    AppSheet(onDismiss = onDismiss, title = "Найти книги") {
        val close = sheetClose(onDismiss)
        when (val s = stage) {
            ScanStage.Pick -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Выбери папку — например, «Download» или «Books». Найдём EPUB, FB2 и TXT во всех вложенных папках.",
                    color = AppColors.Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                AppButton("Выбрать папку", primary = true, modifier = Modifier.fillMaxWidth()) { launcher.launch(null) }
            }
            ScanStage.Scanning -> Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                Text("Ищу книги…", color = AppColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            }
            is ScanStage.Found -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    s.folder,
                    icon = AppIcons.Folder,
                    count = found.size,
                    action = {
                        ActionPill("Другая", AppIcons.FolderSearch) { launcher.launch(null) }
                    },
                )
                if (found.isEmpty()) {
                    Text("Книг в этой папке не нашлось", color = AppColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    val fresh = found.filter { !it.known }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val all = checked.size == found.size
                        ActionPill(if (all) "Снять все" else "Выбрать все", AppIcons.Check) {
                            checked.clear()
                            if (!all) checked.addAll(found.map { it.uri })
                        }
                        if (fresh.size != found.size) InfoChip("Уже есть: ${found.size - fresh.size}", AppColors.Muted, AppIcons.Book)
                    }
                    LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(found, key = { it.uri.toString() }) { book ->
                            FoundRow(book, book.uri in checked) {
                                if (book.uri in checked) checked.remove(book.uri) else checked.add(book.uri)
                            }
                        }
                    }
                }
                AppButton(
                    if (checked.isEmpty()) "Ничего не выбрано" else "Добавить: ${checked.size}",
                    primary = checked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (checked.isNotEmpty()) {
                        onImport(found.filter { it.uri in checked }.map { it.uri })
                        close()
                    }
                }
            }
        }
    }
}

@Composable
private fun FoundRow(book: FoundBook, checked: Boolean, onToggle: () -> Unit) {
    val shape = appCorner(14)
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.98f, onClick = onToggle)
            .fieldSurface(shape, selected = checked)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .then(if (checked) Modifier.background(AppColors.Accent) else Modifier.border(1.5.dp, AppColors.Faint, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                book.name.substringBeforeLast('.').removeSuffix(".fb2"),
                color = AppColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(formatLabel(book.name), sizeLabel(book.size), book.folder.ifEmpty { null }, if (book.known) "уже в библиотеке" else null)
                    .joinToString(" · "),
                color = AppColors.Faint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatLabel(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".epub") -> "EPUB"
        lower.endsWith(".fb2.zip") || lower.endsWith(".fb2") -> "FB2"
        else -> "TXT"
    }
}

private fun sizeLabel(size: Long): String? = when {
    size <= 0 -> null
    size < 1024 * 1024 -> "${(size / 1024).coerceAtLeast(1)} КБ"
    else -> String.format(java.util.Locale.US, "%.1f МБ", size / 1048576.0)
}

private fun folderLabel(tree: Uri): String {
    val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull().orEmpty()
    val path = id.substringAfter(':', id).trimEnd('/')
    return path.substringAfterLast('/').ifEmpty { "Память телефона" }
}
