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
    val file = book?.let { app.library.coverFile(it) }
    val cover by produceState<ImageBitmap?>(null, file) {
        value = file?.takeIf { it.exists() }?.let { withContext(Dispatchers.IO) { CoverCache.load(it, 160) } }
    }
    val shape = appCorner(14)
    val coverShape = appCorner(6)
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
        Box(
            Modifier
                .size(width = coverWidth, height = coverWidth * 1.5f)
                .shadow(4.dp, coverShape, ambientColor = Color(0x66503010), spotColor = Color(0x66503010))
                .clip(coverShape)
                .background(AppColors.Accent.copy(alpha = 0.16f))
                .border(1.dp, AppColors.PanelBorder, coverShape),
            contentAlignment = Alignment.Center,
        ) {
            val image = cover
            if (image != null) {
                Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(AppIcons.Book, null, tint = AppColors.Accent, modifier = Modifier.size(coverWidth * 0.5f))
            }
        }
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
