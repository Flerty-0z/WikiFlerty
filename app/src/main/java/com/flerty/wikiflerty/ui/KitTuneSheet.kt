package com.flerty.wikiflerty.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App

private class PreviewItem(val key: String, val book: Int)

@Composable
fun KitTuneSheet(kit: Kit, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val store = app.style
    val style by store.style.collectAsStateWithLifecycle()
    val tune = style.tune(kit.id)
    val custom = kit.id in style.tunes
    fun set(next: KitTune) = store.update { it.withTune(kit.id, next) }
    AppSheet(onDismiss, title = kit.title) {
        TabbedFrame("Атмосфера", icon = AppIcons.Sun) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                SettingSwitch("Время суток и сезоны", tune.living, icon = AppIcons.Sun) { v -> set(tune.copy(living = v)) }
                AnimatedVisibility(
                    visible = tune.living,
                    enter = expandVertically(Motion.tween(240)) + fadeIn(Motion.fade(200)),
                    exit = shrinkVertically(Motion.tween(200)) + fadeOut(Motion.fade(150)),
                ) {
                    SettingSwitch("Листья, снег и искры", tune.particles, icon = AppIcons.Snow) { v -> set(tune.copy(particles = v)) }
                }
            }
        }
        if (propCount(kit.decor) > 0) {
            TabbedFrame("Декор на полках", icon = AppIcons.Shelf) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShelfPreview(tune)
                    SettingSwitch("Предметы на полках", tune.props, icon = AppIcons.Fire) { v -> set(tune.copy(props = v)) }
                    AnimatedVisibility(
                        visible = tune.props,
                        enter = expandVertically(Motion.tween(260)) + fadeIn(Motion.fade(220)),
                        exit = shrinkVertically(Motion.tween(220)) + fadeOut(Motion.fade(160)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                            ) {
                                itemsIndexed(PropPlace.entries) { _, place ->
                                    PlaceTile(place, selected = place.id == tune.propPlace) { set(tune.copy(propPlace = place.id)) }
                                }
                            }
                            val stepped = PropPlace.of(tune.propPlace).let { it == PropPlace.Between || it == PropPlace.Scatter }
                            AnimatedVisibility(
                                visible = stepped,
                                enter = expandVertically(Motion.tween(220)) + fadeIn(Motion.fade(180)),
                                exit = shrinkVertically(Motion.tween(200)) + fadeOut(Motion.fade(140)),
                            ) {
                                EveryStepper(tune.propEvery) { v -> set(tune.copy(propEvery = v)) }
                            }
                        }
                    }
                }
            }
        }
        val shape = appCorner(14)
        val fade by animateFloatAsState(if (custom) 1f else 0.45f, Motion.fade(200), label = "reset")
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .graphicsLayer { alpha = fade }
                .liveClick(shape, sound = FeedbackSound.Uncheck, enabled = custom) { store.update { it.withTune(kit.id, null) } }
                .border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape),
            contentAlignment = Alignment.Center,
        ) { ActionLabel("Сбросить по умолчанию", AppColors.Accent, fontSize = 14.sp, iconSize = 20.dp, icon = AppIcons.Rotate) }
    }
}

@Composable
private fun ShelfPreview(tune: KitTune) {
    val items = remember(tune) {
        val books = List(7) { PreviewItem("b$it", it) }
        if (!tune.props) books else placeProps(books, tune, "preview") { k -> PreviewItem("p$k", -1) }
    }
    val accent = AppColors.Accent
    val tones = listOf(accent, accent.copy(alpha = 0.55f), AppColors.Muted, accent.copy(alpha = 0.8f), AppColors.Faint, accent.copy(alpha = 0.4f), AppColors.Text.copy(alpha = 0.6f))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(appCorner(14))
            .background(AppColors.Field)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        LazyRow(
            Modifier.fillMaxWidth().height(48.dp),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            items(items.size, key = { items[it].key }) { i ->
                val item = items[i]
                Box(Modifier.animateItem(fadeInSpec = Motion.fade(220), placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f), fadeOutSpec = Motion.fade(160))) {
                    if (item.book >= 0) {
                        val h = listOf(40, 34, 44, 37, 42, 32, 39)[item.book % 7]
                        Box(
                            Modifier
                                .size(width = 13.dp, height = h.dp)
                                .clip(appCorner(3))
                                .background(tones[item.book % tones.size]),
                        )
                    } else {
                        Canvas(Modifier.size(width = 14.dp, height = 22.dp)) { prop(accent) }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(appCorner(2)).background(accent.copy(alpha = 0.35f)))
    }
}

private fun DrawScope.prop(color: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(color.copy(alpha = 0.85f), Offset(w * 0.2f, h * 0.45f), Size(w * 0.6f, h * 0.55f), CornerRadius(2f * density))
    val leaf = Path().apply {
        moveTo(w * 0.5f, h * 0.45f)
        cubicTo(w * 0.05f, h * 0.3f, w * 0.15f, 0f, w * 0.5f, h * 0.05f)
        cubicTo(w * 0.85f, 0f, w * 0.95f, h * 0.3f, w * 0.5f, h * 0.45f)
    }
    drawPath(leaf, color.copy(alpha = 0.55f))
}

@Composable
private fun PlaceTile(place: PropPlace, selected: Boolean, onClick: () -> Unit) {
    val shape = appCorner(14)
    val edge by animateColorAsState(if (selected) AppColors.Accent else AppColors.PanelBorder, Motion.fade(200), label = "place-edge")
    val fill by animateColorAsState(if (selected) AppColors.AccentTintStrong else AppColors.Field, Motion.fade(200), label = "place-fill")
    val accent = AppColors.Accent
    val muted = AppColors.Faint
    Column(
        Modifier
            .width(92.dp)
            .clip(shape)
            .background(fill)
            .border(if (selected) 1.5.dp else 1.dp, edge, shape)
            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.94f, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(width = 64.dp, height = 20.dp)) {
            val pattern = when (place) {
                PropPlace.Between -> "bbpbbpb"
                PropPlace.Scatter -> "bpbbbpb"
                PropPlace.Edges -> "pbbbbbp"
                PropPlace.Start -> "pbbbbbb"
                PropPlace.End -> "bbbbbbp"
            }
            val step = size.width / pattern.length
            pattern.forEachIndexed { i, c ->
                val x = step * i + step * 0.2f
                if (c == 'b') {
                    drawRoundRect(muted, Offset(x, size.height * 0.15f), Size(step * 0.6f, size.height * 0.85f), CornerRadius(1.5f * density))
                } else {
                    drawCircle(accent, step * 0.32f, Offset(x + step * 0.3f, size.height * 0.62f))
                }
            }
        }
        Text(
            place.title,
            color = if (selected) AppColors.Text else AppColors.Muted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EveryStepper(value: Int, onChange: (Int) -> Unit) {
    val shape = appCorner(12)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(appCorner(14))
            .background(AppColors.Field)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingIcon(AppIcons.Sliders, 30.dp)
        Text("Через каждые", color = AppColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(36.dp).liveClick(shape, sound = FeedbackSound.Tick, enabled = value > 1) { onChange(value - 1) }.border(1.dp, AppColors.PanelBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (value > 1) AppColors.Accent else AppColors.Faint
            Canvas(Modifier.size(14.dp)) { drawLine(tint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.2f * density) }
        }
        androidx.compose.animation.AnimatedContent(
            targetState = value,
            transitionSpec = {
                val up = targetState > initialState
                (androidx.compose.animation.slideInVertically(Motion.tween(180)) { if (up) it else -it } + fadeIn(Motion.fade(160))) togetherWith
                    (androidx.compose.animation.slideOutVertically(Motion.tween(180)) { if (up) -it else it } + fadeOut(Motion.fade(120)))
            },
            label = "every",
        ) { v ->
            Text(
                "$v ${if (v == 1) "книгу" else if (v in 2..4) "книги" else "книг"}",
                color = AppColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
        }
        Box(
            Modifier.size(36.dp).liveClick(shape, sound = FeedbackSound.Tick, enabled = value < 6) { onChange(value + 1) }.border(1.dp, AppColors.PanelBorder, shape),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Plus, null, tint = if (value < 6) AppColors.Accent else AppColors.Faint, modifier = Modifier.size(16.dp)) }
    }
}
