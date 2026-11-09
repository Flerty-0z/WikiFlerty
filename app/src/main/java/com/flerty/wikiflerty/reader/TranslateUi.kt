package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.Motion
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.markdown
import com.flerty.wikiflerty.ui.panelSurface
import com.flerty.wikiflerty.ui.revealScroll

data class TranslateInfo(
    val text: String,
    val target: String,
    val word: Boolean,
    val loading: Boolean = true,
    val result: TranslationResult? = null,
    val error: String? = null,
    val saved: Boolean = false,
)

class TranslateActions(
    val onTarget: (String) -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    val onCopy: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
internal fun TranslatePopup(info: TranslateInfo, actions: TranslateActions, modifier: Modifier) {
    var picking by remember { mutableStateOf(false) }
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .panelSurface(appCorner(22), elevation = 18.dp)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)
            .animateContentSize(Motion.tween(260)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (info.word) AppIcons.TextSize else AppIcons.Translate, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            Text(
                if (info.word) "Словарь" else "Перевод",
                color = AppColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
            Box(Modifier.weight(1f))
            val chip = appCorner(12)
            Row(
                Modifier
                    .height(34.dp)
                    .liveClick(chip, sound = FeedbackSound.Toggle) { picking = !picking }
                    .fieldSurface(chip, selected = picking)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(Translator.title(info.target), color = AppColors.Text, fontSize = 13.sp, maxLines = 1)
                Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(12.dp))
            }
            RoundIconButton(AppIcons.Close, "Закрыть", sound = FeedbackSound.Close, onClick = actions.onClose)
        }
        AnimatedVisibility(
            visible = picking,
            enter = expandVertically(Motion.tween(240)) + fadeIn(Motion.fade(200)),
            exit = shrinkVertically(Motion.tween(200)) + fadeOut(Motion.fade(140)),
        ) {
            Row(
                Modifier.padding(end = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Translator.languages.forEach { lang ->
                    val selected = lang.code == info.target
                    val shape = appCorner(12)
                    Box(
                        Modifier
                            .height(36.dp)
                            .liveClick(shape, sound = FeedbackSound.Toggle) {
                                picking = false
                                if (!selected) actions.onTarget(lang.code)
                            }
                            .fieldSurface(shape, selected = selected)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(lang.title, color = if (selected) AppColors.Accent else AppColors.Text, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
        Text(
            info.text,
            color = AppColors.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 10.dp),
        )
        val state = when {
            info.loading -> 0
            info.result != null -> 1
            else -> 2
        }
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(140)) },
            label = "translate",
        ) { s ->
            when (s) {
                0 -> Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                1 -> {
                    val result = info.result
                    if (result != null) {
                        Text(
                            markdown(if (info.word) result.card().ifBlank { "Значение не найдено" } else result.text),
                            color = AppColors.Text,
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            modifier = Modifier.padding(end = 10.dp).heightIn(max = 280.dp).revealScroll(),
                        )
                    }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
                    Icon(AppIcons.Alert, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
                    Text(info.error ?: "Не удалось перевести", color = AppColors.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    RoundIconButton(AppIcons.Rotate, "Повторить", sound = FeedbackSound.Toggle, onClick = actions.onRetry)
                }
            }
        }
        AnimatedVisibility(
            visible = info.result != null && !(info.word && info.result.empty),
            enter = expandVertically(Motion.tween(240)) + fadeIn(Motion.fade(200)),
            exit = shrinkVertically(Motion.tween(200)) + fadeOut(Motion.fade(140)),
        ) {
            Row(Modifier.padding(end = 10.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shape = appCorner(16)
                Box(
                    Modifier
                        .size(48.dp)
                        .liveClick(shape, sound = FeedbackSound.Toggle, onClick = actions.onCopy)
                        .fieldSurface(shape),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Copy, "Копировать", tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .liveClick(shape, sound = FeedbackSound.Toggle, enabled = !info.saved, onClick = actions.onSave)
                        .then(if (info.saved) Modifier.fieldSurface(shape) else Modifier.accentHighlight(shape)),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(info.saved, transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(140)) }, label = "saved") { saved ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (saved) {
                                Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                                Text("В словаре", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(AppIcons.TextSize, null, tint = accentContent, modifier = Modifier.size(18.dp))
                                Text("В словарь", color = accentContent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
