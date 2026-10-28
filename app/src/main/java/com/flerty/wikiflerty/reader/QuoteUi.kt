package com.flerty.wikiflerty.reader

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.AppStyle
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuoteActions(
    val onShare: (QuoteDraft, QuoteLook, QuoteFormat) -> Unit,
    val onSave: (QuoteDraft, QuoteLook, QuoteFormat) -> Unit,
) {
    var look by mutableStateOf(QuoteLook.App)
    var format by mutableStateOf(QuoteFormat.Square)
    var busy by mutableStateOf(false)
}

private val PreviewHeight = 250.dp

@Composable
internal fun QuoteContent(draft: QuoteDraft, actions: QuoteActions, modifier: Modifier = Modifier) {
    val base = AppStyle.base
    val accent = AppStyle.current.accent
    val look = actions.look
    val format = actions.format
    val cover by produceState<Bitmap?>(null, draft.coverPath) {
        value = withContext(Dispatchers.IO) { QuoteCard.loadCover(draft.coverPath) }
    }
    val preview by produceState<ImageBitmap?>(null, draft, look, format, cover, base, accent) {
        val theme = QuoteTheme(base, accent.toInt())
        value = withContext(Dispatchers.Default) {
            runCatching {
                QuoteCard.render(draft, look, format, theme, cover, QuoteCard.previewScale(format)).asImageBitmap()
            }.getOrNull()
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val width by animateDpAsState(PreviewHeight * (format.width.toFloat() / format.height), Motion.tween(300), label = "quoteWidth")
        Box(Modifier.fillMaxWidth().height(PreviewHeight), contentAlignment = Alignment.Center) {
            val shape = appCorner(16)
            Box(
                Modifier
                    .size(width = width, height = PreviewHeight)
                    .shadow(12.dp, shape)
                    .clip(shape)
                    .background(AppColors.Field)
                    .semantics { contentDescription = "Превью цитаты" },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = preview, animationSpec = Motion.fade(260), label = "quotePreview") { image ->
                    if (image != null) {
                        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuoteLook.entries.forEach { option ->
                LookSwatch(option, selected = option == look, modifier = Modifier.weight(1f)) { actions.look = option }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(appCorner(14))
                .background(AppColors.Field)
                .border(1.dp, AppColors.PanelBorder, appCorner(14))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QuoteFormat.entries.forEach { option ->
                FormatChip(option, selected = option == format, modifier = Modifier.weight(1f)) { actions.format = option }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shape = appCorner(14)
            Row(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle, enabled = !actions.busy) { actions.onSave(draft, look, format) }
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Backup, "Сохранить в галерею", tint = AppColors.Accent, modifier = Modifier.size(24.dp))
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .liveClick(shape, sound = FeedbackSound.Open, enabled = !actions.busy) { actions.onShare(draft, look, format) }
                    .background(Brush.verticalGradient(listOf(AppColors.AccentLight, AppColors.Accent))),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actions.busy) {
                    CircularProgressIndicator(color = AppColors.OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Icon(AppIcons.Share, "Поделиться", tint = AppColors.OnAccent, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LookSwatch(look: QuoteLook, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) AppColors.Accent else AppColors.PanelBorder, label = "lookRing")
    val shape = appCorner(14)
    val accent = AppColors.Accent
    val background = AppColors.Background
    val brush = when (look) {
        QuoteLook.App -> Brush.linearGradient(listOf(accent.copy(alpha = 0.55f).compositeOver(background), background, background))
        QuoteLook.Accent -> Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.2f).compositeOver(accent), Color.Black.copy(alpha = 0.75f).compositeOver(accent)))
        QuoteLook.Paper -> Brush.verticalGradient(listOf(Color(0xFFF6EFDF), Color(0xFFEADFC7)))
        QuoteLook.Night -> Brush.linearGradient(listOf(Color(0xFF07080A), Color(0xFF07080A), accent.copy(alpha = 0.35f).compositeOver(Color(0xFF07080A))))
    }
    val mark = when (look) {
        QuoteLook.Paper -> Color(0xFF2B2620)
        QuoteLook.Accent -> Color.White
        else -> accent
    }
    Column(
        modifier.liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.94f, onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .background(brush)
                .border(if (selected) 2.dp else 1.dp, ring, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Quote, null, tint = mark, modifier = Modifier.size(20.dp))
        }
        Text(
            look.title,
            color = if (selected) AppColors.Text else AppColors.Muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun FormatChip(format: QuoteFormat, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = appCorner(10)
    val bg by animateColorAsState(if (selected) AppColors.AccentTintStrong else Color.Transparent, label = "formatBg")
    val tint = if (selected) AppColors.Text else AppColors.Muted
    Row(
        modifier
            .height(40.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.96f, onClick = onClick)
            .background(bg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val ratio = format.width.toFloat() / format.height
            val h = size.height
            val w = h * ratio
            val stroke = 1.6.dp.toPx()
            drawRoundRect(
                color = tint,
                topLeft = Offset((size.width - w) / 2 + stroke / 2, stroke / 2),
                size = Size(w - stroke, h - stroke),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(stroke),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(format.title, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
