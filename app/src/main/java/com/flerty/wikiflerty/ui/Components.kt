package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.panelSurface(shape: Shape, elevation: Dp = 14.dp): Modifier = this
    .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(AppColors.PanelTop, AppColors.PanelBottom)))
    .then(
        if (AppStyle.current.outline) {
            Modifier.border(1.dp, Brush.verticalGradient(listOf(AppColors.EdgeTop, AppColors.PanelBorder)), shape)
        } else {
            Modifier
        },
    )

fun Modifier.accentHighlight(shape: Shape): Modifier = when (AppStyle.buttons) {
    ButtonLook.Filled -> this.background(AppColors.Accent)
    ButtonLook.Outline -> this.border(1.5.dp, AppColors.Accent, shape)
    ButtonLook.Soft -> this.background(AppColors.AccentSoft).border(1.dp, AppColors.Accent.copy(alpha = 0.25f), shape)
}

val accentContent: Color get() = if (AppStyle.buttons == ButtonLook.Filled) AppColors.OnAccent else AppColors.Accent

fun Modifier.fieldSurface(shape: Shape, selected: Boolean = false, accent: Color = AppColors.Accent): Modifier = this
    .clip(shape)
    .background(AppColors.Field)
    .border(if (selected) 2.dp else 1.dp, if (selected) accent else AppColors.PanelBorder, shape)

@Composable
fun Modifier.liveClick(
    shape: Shape,
    sound: FeedbackSound = FeedbackSound.Tap,
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && AppStyle.current.pressEffect) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val feedback = LocalFeedback.current
    val view = LocalView.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .then(
            if (onLongClick == null) {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled,
                ) {
                    feedback?.play(sound, view)
                    onClick()
                }
            } else {
                Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    onLongClick = {
                        feedback?.play(FeedbackSound.Toggle)
                        onLongClick()
                    },
                ) {
                    feedback?.play(sound, view)
                    onClick()
                }
            },
        )
}
