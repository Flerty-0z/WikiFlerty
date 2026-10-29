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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
fun DictionaryScreen(viewModel: LibraryViewModel, modifier: Modifier = Modifier) {
    val entries by viewModel.dictionary.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<DictionaryEntry?>(null) }
    var studying by rememberSaveable { mutableStateOf(false) }
    var cardSettings by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var wordFilter by rememberSaveable { mutableStateOf("all") }
    var filterSheet by remember { mutableStateOf(false) }

    if (studying && entries.isNotEmpty()) {
        FlashcardSession(onClose = { studying = false }, modifier = modifier)
        return
    }

    if (entries.isEmpty()) {
        Column(
            modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Словарь пуст", color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Выдели слово в книге → ✦ → «В словарь» под ответом.",
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
        (query.isBlank() || it.word.contains(query, true) || it.definition.contains(query, true)) &&
            when (wordFilter) {
                "new" -> it.srs.isNew
                "learn" -> it.srs.isLearning
                "review" -> it.srs.isReview
                "done" -> it.srs.isMastered
                else -> true
            }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "study") {
            StudyCard(entries, onStart = { studying = true }, onSettings = { cardSettings = true })
        }
        item(key = "filters") {
            val shape = appCorner(22)
            val current = wordFilters(entries).first { it.id == wordFilter }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { filterSheet = true }
                    .fieldSurface(shape)
                    .padding(start = 14.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(current.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(current.icon, null, tint = current.color, modifier = Modifier.size(15.dp)) }
                Text(current.title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                Text(
                    current.count.toString(),
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.clip(appCorner(10)).background(AppColors.Line).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp).rotate(90f))
            }
        }
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
                                if (query.isEmpty()) Text("Поиск · слов: ${entries.size}", color = AppColors.Faint, fontSize = 15.sp)
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
        items(filtered, key = { it.id }) { entry ->
            val open = openId == entry.id
            val shape = appCorner(16)
            Column(
                motionItem()
                    .fillMaxWidth()
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { openId = if (open) null else entry.id }
                    .fieldSurface(shape)
                    .animateContentSize(Motion.tween(260))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.word,
                        color = AppColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    val (badge, badgeColor) = srsBadge(entry.srs)
                    Row(
                        Modifier.clip(CircleShape).background(badgeColor.copy(alpha = 0.14f)).padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(srsIcon(entry.srs), null, tint = badgeColor, modifier = Modifier.size(12.dp))
                        Text(badge, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                if (open) {
                    SelectionContainer {
                        Text(markdown(entry.definition), color = AppColors.Text, fontSize = 15.sp, lineHeight = 22.sp)
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
                                color = AppColors.Text.copy(alpha = 0.75f),
                                fontFamily = FontFamily.Serif,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (entry.bookId != null || entry.bookTitle != null) BookTag(entry.bookId, entry.bookTitle)
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
                            ) { Icon(AppIcons.Trash, "Удалить слово", tint = AppColors.Bad, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (cardSettings) FlashcardSettingsSheet(onDismiss = { cardSettings = false })

    if (filterSheet) {
        AppSheet(onDismiss = { filterSheet = false }, title = "Показать") {
            val close = sheetClose { filterSheet = false }
            SettingsGroup(null) {
                wordFilters(entries).forEach { f ->
                    val selected = f.id == wordFilter
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .liveClick(appCorner(12), sound = FeedbackSound.Toggle, pressedScale = 0.98f) {
                                wordFilter = f.id
                                close()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(f.color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(f.icon, null, tint = f.color, modifier = Modifier.size(17.dp)) }
                        Text(
                            f.title,
                            color = if (selected) AppColors.Accent else AppColors.Text,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
                        )
                        Text(f.count.toString(), color = AppColors.Faint, fontSize = 14.sp)
                        Box(Modifier.padding(start = 6.dp).size(22.dp), contentAlignment = Alignment.Center) {
                            if (selected) Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (exporting) {
        ExportSheet(
            title = "Экспорт словаря",
            onDismiss = { exporting = false },
            formats = listOf(
                ExportFormat("anki", "Для Anki", "Файл для импорта в Anki: слово на лицевой стороне, значение и цитата на обороте", "WikiFlerty Anki ${exportStamp()}.txt", "text/plain") {
                    com.flerty.wikiflerty.data.Exporter.wordsAnki(entries)
                },
                ExportFormat("md", "Markdown", "Список слов со значениями и цитатами", "Словарь ${exportStamp()}.md", "text/markdown") {
                    com.flerty.wikiflerty.data.Exporter.wordsMarkdown(entries)
                },
                ExportFormat("csv", "Таблица CSV", "Открывается в Excel и Google Таблицах", "Словарь ${exportStamp()}.csv", "text/csv") {
                    com.flerty.wikiflerty.data.Exporter.wordsCsv(entries)
                },
            ),
        )
    }

    toDelete?.let { entry ->
        ConfirmSheet(
            title = "Удалить слово?",
            text = "«${entry.word}» и его карточка удалятся из словаря.",
            confirm = "Удалить",
            onConfirm = {
                viewModel.deleteDictionaryEntry(entry)
                if (openId == entry.id) openId = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

private data class WordFilter(val id: String, val title: String, val count: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

private fun wordFilters(entries: List<DictionaryEntry>): List<WordFilter> = listOf(
    WordFilter("all", "Все слова", entries.size, AppIcons.Translate, AppColors.Accent),
    WordFilter("new", "Новые", entries.count { it.srs.isNew }, AppIcons.Sparkle, AppColors.Info),
    WordFilter("learn", "Учу", entries.count { it.srs.isLearning }, AppIcons.Bulb, AppColors.Warn),
    WordFilter("review", "Повтор", entries.count { it.srs.isReview }, AppIcons.Rotate, AppColors.Good),
    WordFilter("done", "Выучено", entries.count { it.srs.isMastered }, AppIcons.Check, AppColors.Good),
)
