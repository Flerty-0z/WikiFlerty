package com.flerty.wikiflerty.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BookCover(bookId: String?, width: Dp, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId }
    val file = book?.let { app.library.coverFile(it) }
    val cover by produceState<ImageBitmap?>(null, file) {
        value = file?.takeIf { it.exists() }?.let { withContext(Dispatchers.IO) { CoverCache.load(it, 160) } }
    }
    val coverShape = appCorner(6)
    Box(
        modifier
            .size(width = width, height = width * 1.5f)
            .shadow(4.dp, coverShape, ambientColor = Color(0x66503010), spotColor = Color(0x66503010))
            .clip(coverShape)
            .background(AppColors.Field)
            .background(AppColors.Accent.copy(alpha = 0.16f))
            .border(1.dp, AppColors.PanelBorder, coverShape),
        contentAlignment = Alignment.Center,
    ) {
        val image = cover
        if (image != null) {
            Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(AppIcons.Book, null, tint = AppColors.Accent, modifier = Modifier.size(width * 0.5f))
        }
    }
}

@Composable
fun CoverStack(bookIds: List<String>, width: Dp, modifier: Modifier = Modifier) {
    val shown = bookIds.take(4)
    if (shown.isEmpty()) {
        BookCover(null, width, modifier)
        return
    }
    val step = width * 0.42f
    val angles = listOf(-8f, 5f, -3f, 7f)
    Box(modifier.size(width = width + step * (shown.size - 1) + 4.dp, height = width * 1.5f + 6.dp), contentAlignment = Alignment.CenterStart) {
        shown.forEachIndexed { i, id ->
            BookCover(
                id,
                width,
                Modifier
                    .offset(x = step * i)
                    .graphicsLayer { rotationZ = if (shown.size == 1) 0f else angles[i] },
            )
        }
    }
}

@Composable
fun BookChoiceButton(selectedId: String?, allIds: List<String>, fallbackTitle: String?, count: Int, onClick: () -> Unit) {
    val shape = appCorner(16)
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick)
            .fieldSurface(shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectedId == null) CoverStack(allIds, 26.dp) else BookCover(selectedId, 30.dp)
        Text(
            if (selectedId == null) "Все книги" else bookTitle(selectedId, fallbackTitle),
            color = AppColors.Text,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CountBadge(count)
        Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp).rotate(90f))
    }
}

@Composable
fun bookTitle(bookId: String?, fallback: String?): String {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    return books.firstOrNull { it.id == bookId }?.title ?: fallback ?: "Удалённая книга"
}

data class BookChoice(val id: String, val title: String?, val count: Int)

@Composable
fun BookPickerSheet(
    title: String,
    choices: List<BookChoice>,
    total: Int,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss, title = title) {
        val close = sheetClose(onDismiss)
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                BookChoiceRow(selectedId == null, total, { CoverStack(choices.map { it.id }, 28.dp) }, "Все книги") {
                    onSelect(null)
                    close()
                }
            }
            items(choices.size, key = { choices[it].id }) { i ->
                val c = choices[i]
                BookChoiceRow(selectedId == c.id, c.count, { BookCover(c.id, 34.dp) }, bookTitle(c.id, c.title)) {
                    onSelect(c.id)
                    close()
                }
            }
        }
    }
}

@Composable
private fun BookChoiceRow(selected: Boolean, count: Int, cover: @Composable () -> Unit, title: String, onClick: () -> Unit) {
    val shape = appCorner(14)
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.98f, onClick = onClick)
            .background(if (selected) AppColors.Accent.copy(alpha = 0.14f) else AppColors.Line.copy(alpha = 0.45f))
            .border(1.dp, if (selected) AppColors.Accent.copy(alpha = 0.45f) else AppColors.PanelBorder, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cover()
        Text(
            title,
            color = if (selected) AppColors.Accent else AppColors.Text,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CountBadge(count)
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun BookTag(
    bookId: String?,
    fallbackTitle: String?,
    modifier: Modifier = Modifier,
    coverWidth: Dp = 34.dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId }
    val title = book?.title ?: fallbackTitle ?: "Удалённая книга"
    val shape = appCorner(14)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Line.copy(alpha = 0.55f))
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BookCover(bookId, coverWidth)
        Text(
            title,
            color = AppColors.Text,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
        )
        trailing()
    }
}
