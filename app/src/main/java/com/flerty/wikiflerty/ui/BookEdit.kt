package com.flerty.wikiflerty.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
    var editing by remember { mutableIntStateOf(-1) }
    var searching by remember { mutableStateOf(false) }
    val original by produceState("", book.id) { value = app.library.info(book).description }
    val shownDescription = description ?: original
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) cover = uri }
    val camera = rememberCoverShot { cover = it }
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
                androidx.compose.animation.Crossfade(img, animationSpec = Motion.fade(220), label = "edit-cover") { bmp ->
                    if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight())
                    else if (app.library.coverFile(book)?.exists() == true) SmallCover(app.library.coverFile(book), Modifier.fillMaxWidth().fillMaxHeight())
                    else GeneratedCover(title, author, Modifier.fillMaxWidth().fillMaxHeight())
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
            }
            close()
        }
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
