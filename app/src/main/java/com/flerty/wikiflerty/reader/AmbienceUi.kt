package com.flerty.wikiflerty.reader

import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.system.AmbientPresets
import com.flerty.wikiflerty.system.AmbientSound
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.SettingsGroup
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick

@Composable
fun AmbienceOptions(title: String = "Атмосфера") {
    val app = LocalContext.current.applicationContext as App
    val player = app.ambient
    val mix by player.mix.collectAsStateWithLifecycle()

    com.flerty.wikiflerty.ui.TabbedFrame(
        title,
        inset = false,
        trailing = {
            val active = mix.isNotEmpty()
            val alpha by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0.35f, com.flerty.wikiflerty.ui.Motion.tween(220), label = "amb-stop")
            Box(
                Modifier
                    .size(34.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .liveClick(androidx.compose.foundation.shape.CircleShape, sound = FeedbackSound.Close, enabled = active) { player.stopAll() }
                    .background(AppColors.Field, androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, AppColors.PanelBorder, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(com.flerty.wikiflerty.ui.AppIcons.Stop, "Выключить всё", tint = AppColors.Muted, modifier = Modifier.size(16.dp)) }
        },
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AmbientSound.entries.size) { i -> AmbientTile(AmbientSound.entries[i], mix[AmbientSound.entries[i]], player) }
        }
    }

    SettingsGroup("Громкость") {
        Column(Modifier.padding(vertical = 4.dp)) {
            AmbientSound.entries.forEachIndexed { index, sound ->
                if (index > 0) com.flerty.wikiflerty.ui.ListDivider()
                VolumeRow(sound, mix[sound], player)
            }
        }
    }

    SettingsGroup("Готовые миксы") {
        Row(Modifier.padding(vertical = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val saved = player.saved
            if (mix.isEmpty() && saved.isNotEmpty()) MixChip("Мой микс", "▶", false) { player.applyMix(saved) }
            AmbientPresets.forEach { preset ->
                MixChip(preset.title, preset.emoji, preset.mix.keys == mix.keys) { player.preset(preset) }
            }
        }
    }

}

@Composable
private fun AmbientTile(sound: AmbientSound, volume: Float?, player: com.flerty.wikiflerty.system.AmbientPlayer) {
    val on = volume != null
    val shape = appCorner(18)
    val level by androidx.compose.animation.core.animateFloatAsState(volume ?: 0f, com.flerty.wikiflerty.ui.Motion.tween(220), label = "amb-level")
    val tint by androidx.compose.animation.animateColorAsState(if (on) AppColors.AccentTint else AppColors.Field, com.flerty.wikiflerty.ui.Motion.tween(260), label = "amb-bg")
    val edge by androidx.compose.animation.animateColorAsState(if (on) AppColors.Accent else AppColors.PanelBorder, com.flerty.wikiflerty.ui.Motion.tween(260), label = "amb-edge")
    val accent = AppColors.Accent
    Box(
        Modifier
            .width(88.dp)
            .height(118.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.95f) { player.toggle(sound) }
            .background(tint, shape)
            .drawBehind {
                if (level > 0f) {
                    val h = size.height * level
                    drawRect(accent.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width, h))
                    drawRect(accent.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
                }
            }
            .border(if (on) 1.5.dp else 1.dp, edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
            AmbientEmoji(sound.emoji, on)
            com.flerty.wikiflerty.ui.FitText(sound.title, if (on) AppColors.Accent else AppColors.Text, 12.5.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            Text(
                if (on) "${(level * 100).roundToInt()}%" else "выкл",
                color = if (on) AppColors.Accent else AppColors.Faint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun VolumeRow(sound: AmbientSound, volume: Float?, player: com.flerty.wikiflerty.system.AmbientPlayer) {
    val on = volume != null
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.45f, com.flerty.wikiflerty.ui.Motion.tween(220), label = "amb-row")
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    val stored = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.6f) }
    if (volume != null && dragging == null) stored.floatValue = volume
    Row(Modifier.height(44.dp).graphicsLayer { this.alpha = alpha }, verticalAlignment = Alignment.CenterVertically) {
        Text(sound.emoji, fontSize = 18.sp, modifier = Modifier.width(30.dp))
        com.flerty.wikiflerty.ui.FitText(sound.title, if (on) AppColors.Text else AppColors.Muted, 13.sp, Modifier.width(70.dp))
        com.flerty.wikiflerty.ui.KitSlider(
            value = dragging ?: stored.floatValue,
            onValueChange = { v ->
                dragging = v
                player.level(sound, v.coerceAtLeast(0.02f))
            },
            onValueChangeFinished = {
                dragging?.let { stored.floatValue = it }
                dragging = null
            },
            modifier = Modifier.weight(1f).height(36.dp),
        )
        Text(
            if (on) "${((dragging ?: volume ?: 0f) * 100).roundToInt()}%" else "—",
            color = if (on) AppColors.Accent else AppColors.Faint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun MixChip(label: String, emoji: String?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = appCorner(18)
    Row(
        modifier
            .height(36.dp)
            .liveClick(shape, sound = FeedbackSound.Toggle, onClick = onClick)
            .background(if (selected) AppColors.Accent else Color.Transparent, shape)
            .border(1.dp, if (selected) AppColors.Accent else AppColors.Line, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (emoji != null) Text(emoji, fontSize = 14.sp)
        Text(label, color = if (selected) AppColors.OnAccent else AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun AmbientEmoji(emoji: String, on: Boolean) {
    val level by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, com.flerty.wikiflerty.ui.Motion.tween(400), label = "amb-on")
    val idle = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val breath: androidx.compose.runtime.State<Float> = if (level > 0f && com.flerty.wikiflerty.ui.Motion.enabled) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "amb").animateFloat(
            0f,
            1f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "amb-breath",
        )
    } else {
        idle
    }
    val accent = AppColors.Accent
    Box(
        Modifier
            .size(40.dp)
            .drawBehind {
                if (level > 0f && com.flerty.wikiflerty.ui.Motion.enabled) {
                    val b = breath.value
                    drawCircle(accent.copy(alpha = 0.10f * level + 0.10f * level * b), size.minDimension * (0.42f + 0.08f * b))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            emoji,
            fontSize = 24.sp,
            modifier = Modifier.graphicsLayer {
                val b = if (com.flerty.wikiflerty.ui.Motion.enabled) breath.value * level else 0f
                scaleX = 1f + 0.08f * b
                scaleY = 1f + 0.08f * b
                translationY = -2f * density * b
            },
        )
    }
}
