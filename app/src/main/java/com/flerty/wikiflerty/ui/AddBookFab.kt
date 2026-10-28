package com.flerty.wikiflerty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AddBookFab(onFile: () -> Unit, onScan: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 45f else 0f, Motion.spring(), label = "fabTurn")
    BackHandler(enabled = open) { open = false }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FabItem(open, delay = 1, icon = AppIcons.Search, text = "Скан") {
            open = false
            onScan()
        }
        FabItem(open, delay = 0, icon = AppIcons.Book, text = "Файл") {
            open = false
            onFile()
        }
        val shape = appCorner(18)
        Row(
            Modifier
                .heightIn(min = 56.dp)
                .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.92f) { open = !open }
                .then(if (open) Modifier.panelSurface(shape, elevation = 6.dp) else Modifier.accentHighlight(shape))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val tint = if (open) AppColors.Text else accentContent
            Icon(AppIcons.Plus, null, tint = tint, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn })
            Text(if (open) "Закрыть" else "Книга", color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun FabItem(visible: Boolean, delay: Int, icon: ImageVector, text: String, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.fade(180 + delay * 60)) +
            slideInVertically(Motion.spring()) { it * (1 + delay) / 2 } +
            scaleIn(Motion.spring(), initialScale = 0.7f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
        exit = fadeOut(Motion.fade(140)) +
            slideOutVertically(Motion.tween(180)) { it / 2 } +
            scaleOut(Motion.tween(180), targetScale = 0.8f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
    ) {
        val shape = appCorner(16)
        Row(
            Modifier
                .heightIn(min = 46.dp)
                .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.92f, onClick = onClick)
                .panelSurface(shape, elevation = 6.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(19.dp))
            Text(text, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
