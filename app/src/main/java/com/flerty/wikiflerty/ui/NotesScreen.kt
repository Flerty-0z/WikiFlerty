package com.flerty.wikiflerty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.data.Note
import com.flerty.wikiflerty.reader.NoteCard

@Composable
fun NotesScreen(viewModel: LibraryViewModel, modifier: Modifier = Modifier) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var colorFilter by remember { mutableStateOf<Long?>(null) }
    var toDelete by remember { mutableStateOf<Note?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var bookFilter by remember { mutableStateOf<String?>(null) }
    var bookSheet by remember { mutableStateOf(false) }

    val titles = books.associate { it.id to it.title }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.flerty.wikiflerty.App
    val libraryPrefs by app.libraryPrefs.prefs.collectAsStateWithLifecycle()
    val inPages = libraryPrefs.progressInPages
    val positions = books.associate { it.id to it.positions }
    val usedColors = notes.map { it.color }.distinct()
    val bookOrderAll = books.map { it.id }
    val noteBooks = notes.map { it.bookId }.distinct()
        .sortedBy { id -> bookOrderAll.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
    if (bookFilter != null && bookFilter !in noteBooks) bookFilter = null
    val choices = noteBooks.map { id -> BookChoice(id, titles[id], notes.count { it.bookId == id }) }
    val filtered = notes
        .filter { bookFilter == null || it.bookId == bookFilter }
        .filter { colorFilter == null || it.color == colorFilter }
        .filter {
            query.isBlank() ||
                it.text.contains(query, ignoreCase = true) ||
                it.note.contains(query, ignoreCase = true) ||
                (titles[it.bookId]?.contains(query, ignoreCase = true) ?: false)
        }
    val bookOrder = books.map { it.id }
    val groups = filtered
        .groupBy { it.bookId }
        .toList()
        .sortedBy { (bookId, _) -> bookOrder.indexOf(bookId).let { if (it < 0) Int.MAX_VALUE else it } }

    if (notes.isEmpty()) {
        Column(
            modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Заметок пока нет", color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Выдели текст в книге → «Заметка» или «Маркер».",
                color = AppColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "search") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp),
                    cursorBrush = SolidColor(AppColors.Accent),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .fieldSurface(appCorner(24))
                        .padding(horizontal = 16.dp),
                    decorationBox = { inner ->
                        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Search, null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
                            Box(Modifier.padding(start = 10.dp).weight(1f)) {
                                if (query.isEmpty()) Text("Поиск по заметкам", color = AppColors.Faint, fontSize = 15.sp)
                                inner()
                            }
                        }
                    },
                )
                Box(
                    Modifier
                        .size(48.dp)
                        .liveClick(CircleShape, sound = FeedbackSound.Open) { exporting = true }
                        .fieldSurface(CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Send, "Экспорт", tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
            }
        }
        if (noteBooks.size > 1) {
            item(key = "books") {
                BookChoiceButton(
                    selectedId = bookFilter,
                    allIds = noteBooks,
                    fallbackTitle = bookFilter?.let { titles[it] },
                    count = if (bookFilter == null) notes.size else notes.count { it.bookId == bookFilter },
                ) { bookSheet = true }
            }
        }
        if (usedColors.size > 1) {
            item(key = "colors") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val shape = appCorner(18)
                    Box(
                        Modifier
                            .height(36.dp)
                            .liveClick(shape, sound = FeedbackSound.Toggle) { colorFilter = null }
                            .background(if (colorFilter == null) AppColors.Accent else AppColors.Field)
                            .border(1.dp, AppColors.PanelBorder, shape)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Все · ${notes.size}",
                            color = if (colorFilter == null) AppColors.OnAccent else AppColors.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    usedColors.forEach { color ->
                        val selected = colorFilter == color
                        Box(
                            Modifier
                                .size(36.dp)
                                .liveClick(CircleShape, sound = FeedbackSound.Toggle) {
                                    colorFilter = if (selected) null else color
                                }
                                .border(2.dp, if (selected) AppColors.Text else Color.Transparent, CircleShape)
                                .semantics { contentDescription = "Фильтр по цвету" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color)))
                        }
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                Text("Ничего не найдено", color = AppColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 16.dp))
            }
        }
        groups.forEach { (bookId, bookNotes) ->
            item(key = "book-$bookId") {
                BookTag(
                    bookId,
                    titles[bookId],
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    coverWidth = 40.dp,
                ) { CountBadge(bookNotes.size) }
            }
            items(bookNotes.sortedBy { it.progression }, key = { it.id }) { note ->
                Box(motionItem()) {
                    NoteCard(
                        note,
                        onOpen = { viewModel.openNote(note) },
                        onEdit = { toDelete = note },
                        editIcon = AppIcons.Trash,
                        editLabel = "Удалить заметку",
                        pages = if (inPages) positions[note.bookId] ?: 0 else 0,
                        onToggleUnit = { app.libraryPrefs.update { it.copy(progressInPages = !it.progressInPages) } },
                    )
                }
            }
        }
    }

    if (bookSheet) {
        BookPickerSheet(
            title = "Книги",
            choices = choices,
            total = notes.size,
            selectedId = bookFilter,
            onSelect = { bookFilter = it },
            onDismiss = { bookSheet = false },
        )
    }

    if (exporting) {
        var scope by remember { mutableStateOf(bookFilter ?: "all") }
        var scopeSheet by remember { mutableStateOf(false) }
        val withNotes = books.filter { b -> notes.any { it.bookId == b.id } }
        val chosen = { if (scope == "all") notes else notes.filter { it.bookId == scope } }
        val base = if (scope == "all") "Заметки" else (titles[scope] ?: "Заметки").take(40)
        ExportSheet(
            title = "Экспорт заметок",
            onDismiss = { exporting = false },
            formats = listOf(
                ExportFormat("md", "Markdown", "Цитаты с заметками по книгам. Открывается в Obsidian, Notion и любом редакторе", "$base ${exportStamp()}.md", "text/markdown") {
                    com.flerty.wikiflerty.data.Exporter.notesMarkdown(chosen(), books)
                },
                ExportFormat("txt", "Простой текст", "Удобно переслать в мессенджер или вставить в документ", "$base ${exportStamp()}.txt", "text/plain") {
                    com.flerty.wikiflerty.data.Exporter.notesText(chosen(), books)
                },
            ),
            extra = {
                val selected = scope.takeIf { it != "all" }
                BookChoiceButton(
                    selectedId = selected,
                    allIds = withNotes.map { it.id },
                    fallbackTitle = selected?.let { titles[it] },
                    count = if (selected == null) notes.size else notes.count { it.bookId == selected },
                ) { scopeSheet = true }
                if (scopeSheet) {
                    BookPickerSheet(
                        title = "Книги",
                        choices = withNotes.map { b -> BookChoice(b.id, b.title, notes.count { it.bookId == b.id }) },
                        total = notes.size,
                        selectedId = selected,
                        onSelect = { scope = it ?: "all" },
                        onDismiss = { scopeSheet = false },
                    )
                }
            },
        )
    }

    toDelete?.let { note ->
        ConfirmSheet(
            title = "Удалить заметку?",
            text = "«${note.text.take(120)}»",
            confirm = "Удалить",
            onConfirm = { viewModel.deleteNote(note) },
            onDismiss = { toDelete = null },
        )
    }
}
