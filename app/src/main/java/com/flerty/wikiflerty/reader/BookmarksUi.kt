package com.flerty.wikiflerty.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.data.Bookmark
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.LocalFeedback
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.motionItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class BookmarkActions(
    val onToggle: () -> Unit,
    val onOpen: (Bookmark) -> Unit,
    val onDelete: (Bookmark) -> Unit,
)

internal val NavSheets = listOf(ReaderSheet.Toc, ReaderSheet.Notes, ReaderSheet.Bookmarks)

@Composable
internal fun NavTabs(current: ReaderSheet, onSelect: (ReaderSheet) -> Unit, modifier: Modifier = Modifier) {
    val outer = appCorner(14)
    val inner = appCorner(10)
    BoxWithConstraints(
        modifier
            .height(46.dp)
            .clip(outer)
            .background(AppColors.Field)
            .border(1.dp, AppColors.PanelBorder, outer)
            .padding(4.dp),
    ) {
        val index = NavSheets.indexOf(current).coerceAtLeast(0)
        val slot = maxWidth / NavSheets.size
        val offset by animateDpAsState(slot * index, Motion.tween(280), label = "tab")
        Box(
            Modifier
                .offset(x = offset)
                .width(slot)
                .fillMaxHeight()
                .clip(inner)
                .background(AppColors.AccentTintStrong)
                .border(1.dp, AppColors.Accent.copy(alpha = 0.3f), inner),
        )
        Row(Modifier.fillMaxSize()) {
            NavSheets.forEach { sheet ->
                val selected = sheet == current
                val color by animateColorAsState(if (selected) AppColors.Text else AppColors.Muted, label = "tabText")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .liveClick(inner, sound = FeedbackSound.Toggle, pressedScale = 0.9f) { if (!selected) onSelect(sheet) },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        when (sheet) {
                            ReaderSheet.Notes -> if (selected) AppIcons.StarFilled else AppIcons.Star
                            ReaderSheet.Bookmarks -> AppIcons.Bookmark
                            else -> AppIcons.Toc
                        },
                        contentDescription = sheet.title,
                        tint = if (selected) AppColors.Accent else color,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BookmarkList(bookmarks: List<Bookmark>, actions: BookmarkActions, modifier: Modifier = Modifier) {
    if (bookmarks.isEmpty()) {
        Row(
            Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(appCorner(14)).background(AppColors.Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Bookmark, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Пока нет закладок", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Нажми ленточку на верхней панели", color = AppColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(bookmarks, key = { it.id }) { mark ->
            BookmarkRow(
                bookmark = mark,
                onOpen = { actions.onOpen(mark) },
                onDelete = { actions.onDelete(mark) },
                modifier = motionItem(),
            )
        }
    }
}

private val DeleteColor = Color(0xFFF08A8A)

@Composable
private fun BookmarkRow(bookmark: Bookmark, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val offset = remember(bookmark.id) { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val feedback = LocalFeedback.current
    val shape = appCorner(16)
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.coerceAtLeast(1) }) {
        val reveal = (-offset.value / width).coerceIn(0f, 1f)
        Row(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(DeleteColor.copy(alpha = 0.10f + 0.22f * reveal))
                .padding(end = 22.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Trash,
                null,
                tint = DeleteColor,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    val s = 0.7f + 0.5f * (reveal / 0.35f).coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                },
            )
        }
        Row(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(bookmark.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value < -size.width * 0.35f) {
                                    feedback?.play(FeedbackSound.Close)
                                    offset.animateTo(-size.width * 1.1f, Motion.tween(200))
                                    onDelete()
                                } else {
                                    offset.animateTo(0f, Motion.spring())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, Motion.spring()) } },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + amount).coerceIn(-size.width.toFloat(), 0f)) }
                    }
                }
                .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onOpen)
                .fieldSurface(shape)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.BookmarkFilled, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    bookmark.chapter ?: "Закладка",
                    color = AppColors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bookmark.snippet.isNotBlank()) {
                    Text(
                        bookmark.snippet,
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${(bookmark.progression * 100).roundToInt().coerceIn(0, 100)}% · ${bookmarkDate(bookmark.createdAt)}",
                    color = AppColors.Faint,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onDelete)
                    .semantics { contentDescription = "Удалить закладку" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Trash, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
private val yearFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))

internal fun bookmarkDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "сегодня"
        date == today.minusDays(1) -> "вчера"
        date.year == today.year -> dayFormat.format(date)
        else -> yearFormat.format(date)
    }
}

@Composable
internal fun BookmarkButton(active: Boolean, onClick: () -> Unit) {
    var pulse by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            scale.snapTo(0.55f)
            scale.animateTo(1f, Motion.spring())
        }
    }
    val tint by animateColorAsState(if (active) AppColors.Accent else AppColors.Text, label = "mark")
    Box(
        Modifier
            .size(44.dp)
            .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.88f) {
                pulse++
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (active) AppIcons.BookmarkFilled else AppIcons.Bookmark,
            contentDescription = if (active) "Убрать закладку" else "Добавить закладку",
            tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        )
    }
}

@Composable
internal fun PageRibbon(modifier: Modifier = Modifier) {
    val top = AppColors.AccentDark
    val bottom = AppColors.Accent
    Canvas(modifier.size(width = 16.dp, height = 28.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width / 2f, size.height * 0.74f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(top, bottom)))
    }
}
