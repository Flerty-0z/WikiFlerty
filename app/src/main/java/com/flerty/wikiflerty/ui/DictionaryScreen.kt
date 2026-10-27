package com.flerty.wikiflerty.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.data.DictionaryEntry

/** The user's dictionary: words saved from the AI chat, with explanation and the sentence from the book. */
@Composable
fun DictionaryScreen(viewModel: LibraryViewModel, modifier: Modifier = Modifier) {
    val entries by viewModel.dictionary.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<DictionaryEntry?>(null) }

    if (entries.isEmpty()) {
        Column(
            modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Словарь пуст", color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Выдели непонятное слово в книге, нажми ✦ и выбери «Объяснить» или «Определение». " +
                    "Под ответом нажми «В словарь» — слово сохранится сюда вместе с объяснением и цитатой.",
                color = AppColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        return
    }

    val filtered = entries.filter {
        query.isBlank() || it.word.contains(query, true) || it.definition.contains(query, true)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "search") {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(AppColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .fieldSurface(appCorner(24))
                    .padding(horizontal = 16.dp),
                decorationBox = { inner ->
                    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Search, null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
                        Box(Modifier.padding(start = 10.dp).weight(1f)) {
                            if (query.isEmpty()) Text("Поиск · слов: ${entries.size}", color = AppColors.Faint, fontSize = 15.sp)
                            inner()
                        }
                    }
                },
            )
        }
        items(filtered, key = { it.id }) { entry ->
            val open = openId == entry.id
            val shape = appCorner(16)
            Column(
                Modifier
                    .fillMaxWidth()
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { openId = if (open) null else entry.id }
                    .fieldSurface(shape)
                    .animateContentSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(entry.word, color = AppColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif)
                if (open) {
                    SelectionContainer {
                        Text(markdown(entry.definition), color = Color(0xFFE2E3E6), fontSize = 15.sp, lineHeight = 22.sp)
                    }
                } else {
                    Text(
                        markdown(entry.definition),
                        color = AppColors.Muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (entry.context.isNotBlank()) {
                            Text(
                                "«${entry.context}»",
                                color = Color(0xFFBDBBB5),
                                fontFamily = FontFamily.Serif,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        entry.bookTitle?.let { Text(it, color = AppColors.Faint, fontSize = 12.sp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (entry.bookId != null && entry.locator != null) {
                                val buttonShape = appCorner(12)
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .liveClick(buttonShape, sound = FeedbackSound.Open) { viewModel.openDictionaryEntry(entry) }
                                        .border(1.dp, AppColors.Accent.copy(alpha = 0.33f), buttonShape),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Открыть в книге", color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                            }
                            val deleteShape = appCorner(12)
                            Box(
                                Modifier
                                    .height(42.dp)
                                    .liveClick(deleteShape, sound = FeedbackSound.Close) { toDelete = entry }
                                    .border(1.dp, Color(0x55F08A8A), deleteShape)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Icon(AppIcons.Trash, "Удалить слово", tint = Color(0xFFF08A8A), modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить «${entry.word}»?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDictionaryEntry(entry)
                    toDelete = null
                    if (openId == entry.id) openId = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}
