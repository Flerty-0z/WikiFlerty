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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    com.flerty.wikiflerty.ui.TabbedFrame(title) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmbientSound.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sound ->
                    val on = sound in mix
                    val shape = appCorner(16)
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 76.dp)
                            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.94f) { player.toggle(sound) }
                            .then(if (on) Modifier.background(AppColors.AccentTint, shape).border(1.5.dp, AppColors.Accent, shape) else Modifier.fieldSurface(shape))
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AmbientEmoji(sound.emoji, on)
                        Text(
                            sound.title,
                            color = if (on) AppColors.Accent else AppColors.Text,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.5.sp, stepSize = 0.5.sp),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    }

    if (mix.isNotEmpty()) {
        SettingsGroup("Громкость") {
            Column(Modifier.padding(vertical = 6.dp)) {
                mix.forEach { (sound, volume) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sound.emoji, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                        Text(sound.title, color = AppColors.Text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.width(96.dp))
                        Slider(
                            value = volume,
                            onValueChange = { player.setVolume(sound, it) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors = SliderDefaults.colors(thumbColor = AppColors.Accent, activeTrackColor = AppColors.Accent, inactiveTrackColor = AppColors.Line),
                        )
                    }
                }
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

    if (mix.isNotEmpty()) {
        val shape = appCorner(14)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).liveClick(shape, sound = FeedbackSound.Close) { player.stopAll() }.border(1.dp, AppColors.Line, shape),
            contentAlignment = Alignment.Center,
        ) { Text("Выключить всё", color = AppColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
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
