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
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.panelSurface(shape: Shape, elevation: Dp = 14.dp): Modifier = when (AppStyle.kit.decor) {
    Decor.Comic -> this.hardShadow(shape, 5.dp).clip(shape).background(AppColors.Panel).border(2.5.dp, AppColors.PanelBorder, shape)
    Decor.Terminal -> this.clip(shape).background(AppColors.Panel).border(1.5.dp, AppColors.Accent.copy(alpha = 0.6f), shape)
    Decor.Cosmos -> this.shadow(elevation, shape, ambientColor = Color(0x669262FF), spotColor = Color(0x669262FF)).clip(shape)
        .background(Brush.verticalGradient(listOf(AppColors.PanelTop.copy(alpha = 0.92f), AppColors.PanelBottom.copy(alpha = 0.92f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), AppColors.PanelBorder)), shape)
    else -> this.basePanelSurface(shape, elevation)
}

private fun Modifier.basePanelSurface(shape: Shape, elevation: Dp): Modifier = this
    .shadow(elevation, shape, ambientColor = if (AppStyle.light) Color(0x55A07850) else Color.Black, spotColor = if (AppStyle.light) Color(0x55A07850) else Color.Black)
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

fun Modifier.hardShadow(shape: Shape, offset: Dp = 4.dp, color: Color = Color(0xFF0A0A0A)): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val d = offset.toPx()
    translate(d, d) { drawOutline(outline, color) }
}

fun Modifier.kitCover(shape: Shape): Modifier = when (AppStyle.kit.decor) {
    Decor.Comic -> this.hardShadow(shape, 4.dp).border(2.dp, Color(0xFF111111), shape)
    Decor.Cabin -> this.border(3.dp, Color(0xFFFFF3E2), shape)
    Decor.Terminal -> this.border(1.5.dp, AppColors.Accent, shape)
    Decor.Library -> this.border(1.dp, Color(0xFFE0B04A).copy(alpha = 0.65f), shape)
    Decor.Cosmos -> this.shadow(10.dp, shape, ambientColor = AppColors.Accent, spotColor = AppColors.Accent)
    else -> this
}

fun Modifier.fieldSurface(shape: Shape, selected: Boolean = false, accent: Color = AppColors.Accent): Modifier = when (AppStyle.kit.decor) {
    Decor.Comic -> this.hardShadow(shape, 4.dp).clip(shape).background(AppColors.Field).border(if (selected) 3.dp else 2.dp, if (selected) accent else AppColors.PanelBorder, shape)
    Decor.Terminal -> this.clip(shape).background(AppColors.Field).border(if (selected) 2.dp else 1.dp, if (selected) accent else accent.copy(alpha = 0.38f), shape)
    Decor.Cosmos -> this.clip(shape).background(AppColors.Field.copy(alpha = 0.66f))
        .border(if (selected) 2.dp else 1.dp, if (selected) SolidColor(accent) else Brush.verticalGradient(listOf(Color.White.copy(alpha = if (AppStyle.light) 0.9f else 0.2f), AppColors.PanelBorder)), shape)
    Decor.Library -> this.clip(shape).background(Brush.verticalGradient(listOf(AppColors.PanelTop, AppColors.Field)))
        .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color(0xFFE0B04A).copy(alpha = if (AppStyle.light) 0.45f else 0.22f), shape)
    Decor.Zen -> this.clip(shape).background(AppColors.Field).border(if (selected) 1.5.dp else 0.8.dp, if (selected) accent else AppColors.PanelBorder.copy(alpha = 0.8f), shape)
    Decor.Cabin -> this.clip(shape).background(AppColors.Field).drawBehind {
        val inset = 5.dp.toPx()
        val outline = androidx.compose.foundation.shape.RoundedCornerShape(14.dp).createOutline(Size(size.width - inset * 2, size.height - inset * 2), layoutDirection, this)
        translate(inset, inset) {
            drawOutline(outline, (if (selected) accent else AppColors.Muted).copy(alpha = 0.35f), style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))))
        }
    }.border(if (selected) 2.dp else 1.dp, if (selected) accent else AppColors.PanelBorder, shape)
    else -> this.baseFieldSurface(shape, selected, accent)
}

private fun Modifier.baseFieldSurface(shape: Shape, selected: Boolean, accent: Color): Modifier = this
    .then(if (AppStyle.light) Modifier.shadow(3.dp, shape, ambientColor = Color(0x33A07850), spotColor = Color(0x33A07850)) else Modifier)
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
        targetValue = if (pressed) Motion.pressScale(pressedScale) else 1f,
        animationSpec = if (Motion.enabled) spring(dampingRatio = if (Motion.kind == MotionStyle.Strict) 0.9f else 0.5f, stiffness = Spring.StiffnessMedium) else androidx.compose.animation.core.snap(),
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
