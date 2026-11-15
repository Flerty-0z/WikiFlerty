package com.flerty.wikiflerty.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BookEditSheet(book: Book, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var description by remember { mutableStateOf(book.customDescription) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var removeCover by remember { mutableStateOf(false) }
    var editing by remember { mutableIntStateOf(-1) }
    var searching by remember { mutableStateOf(false) }
    val original by produceState("", book.id) { value = app.library.info(book).description }
    val shownDescription = description ?: original
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { cover = uri; removeCover = false } }
    val camera = rememberCoverShot { cover = it; removeCover = false }
    val hasFile = remember(book) { app.library.coverFile(book)?.exists() == true }
    val preview by produceState<ImageBitmap?>(null, cover) {
        value = cover?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) }?.asImageBitmap() }.getOrNull()
            }
        }
    }

    AppSheet(onDismiss, title = "Изменить книгу") {
        val close = sheetClose(onDismiss)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(width = 84.dp, height = 126.dp)
                    .clip(appCorner(10))
                    .border(1.dp, AppColors.PanelBorder, appCorner(10)),
            ) {
                val img = preview
                val target: Any = img ?: if (hasFile && !removeCover) "file" else "generated"
                androidx.compose.animation.Crossfade(target, animationSpec = Motion.fade(220), label = "edit-cover") { t ->
                    when (t) {
                        is ImageBitmap -> Image(t, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight())
                        "file" -> SmallCover(app.library.coverFile(book), Modifier.fillMaxWidth().fillMaxHeight())
                        else -> GeneratedCover(title, author, Modifier.fillMaxWidth().fillMaxHeight())
                    }
                }
                CoverRemoveButton(target != "generated" || cover != null, Modifier.align(Alignment.TopEnd)) {
                    if (cover != null) cover = null else removeCover = true
                }
            }
            Column(Modifier.weight(1f).height(126.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CoverButton(AppIcons.Image, "Из галереи", Modifier.weight(1f)) { pick.launch("image/*") }
                CoverButton(AppIcons.Camera, "Сфотографировать", Modifier.weight(1f), onClick = camera)
                CoverButton(AppIcons.Search, "Найти в интернете", Modifier.weight(1f)) { searching = true }
            }
        }
        PaperField(title, "Название", AppIcons.Book) { editing = 0 }
        PaperField(author, "Автор", AppIcons.Pencil) { editing = 1 }
        DescriptionField(shownDescription) { editing = 2 }
        AppButton("Сохранить", primary = true, modifier = Modifier.fillMaxWidth()) {
            if (title.isBlank()) return@AppButton
            val pickedCover = cover
            val changedInfo = title != book.title || author != book.author || (description != null && description != book.customDescription)
            app.appScope.launch {
                if (changedInfo) app.library.editInfo(book.id, title, author, description ?: shownDescription)
                if (pickedCover != null) app.library.setCover(book.id, pickedCover)
                else if (removeCover && hasFile) app.library.removeCover(book.id)
            }
            close()
        }
        if (searching) {
            CoverSearchSheet(title, author, onDismiss = { searching = false }) { uri, found ->
                cover = uri
                if (found != null && shownDescription.isBlank()) description = found
            }
        }
        when (editing) {
            0 -> TextInputSheet("Название", title, "Название книги", onDismiss = { editing = -1 }, icon = AppIcons.Book) { if (it.isNotBlank()) title = it }
            1 -> TextInputSheet("Автор", author, "Автор книги", onDismiss = { editing = -1 }, icon = AppIcons.Pencil, maxLength = 120) { author = it }
            2 -> TextInputSheet("Описание", shownDescription, "О чём книга", onDismiss = { editing = -1 }, maxLength = 4000, multiline = true) { description = it }
        }
    }
}

@Composable
internal fun CoverRemoveButton(visible: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(5.dp),
        enter = androidx.compose.animation.scaleIn(Motion.tween(200), initialScale = 0.6f) + androidx.compose.animation.fadeIn(Motion.fade(180)),
        exit = androidx.compose.animation.scaleOut(Motion.tween(160), targetScale = 0.6f) + androidx.compose.animation.fadeOut(Motion.fade(140)),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .liveClick(CircleShape, sound = FeedbackSound.Trash, pressedScale = 0.88f, onClick = onClick)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Trash, "Убрать обложку", tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
private fun DescriptionField(value: String, onClick: () -> Unit) {
    val shape = appCorner(16)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onClick).fieldSurface(shape).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(AppIcons.Note, null, tint = AppColors.Muted, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        androidx.compose.animation.Crossfade(value.isEmpty(), Modifier.weight(1f).padding(horizontal = 10.dp), animationSpec = Motion.fade(200), label = "desc-field") { empty ->
            Text(
                if (empty) "Описание" else value,
                color = if (empty) AppColors.Faint else AppColors.Text,
                fontSize = if (empty) 16.sp else 14.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(AppIcons.Pencil, null, tint = AppColors.Faint, modifier = Modifier.padding(top = 2.dp).size(15.dp))
    }
}
