package com.flerty.wikiflerty.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal class HoldScroll(private val state: LazyListState) : NestedScrollConnection {
    private val active get() = state.canScrollForward || state.canScrollBackward
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (active) available.copy(x = 0f) else Offset.Zero
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (active) available.copy(x = 0f) else Velocity.Zero
}

internal fun Modifier.fadingEdges(state: LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = 22.dp.toPx()
        if (state.canScrollBackward) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, edge), size = Size(size.width, edge), blendMode = BlendMode.DstIn)
        if (state.canScrollForward) drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), size.height - edge, size.height), topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge), blendMode = BlendMode.DstIn)
    }

@Composable
fun RibbonMore(rest: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .liveClick(appCorner(12), sound = FeedbackSound.Expand, pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Text("Дальше", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text("· ещё $rest", color = AppColors.Faint, fontSize = 13.sp, maxLines = 1)
        Icon(AppIcons.Chevron, null, tint = AppColors.Accent, modifier = Modifier.size(15.dp).graphicsLayer { rotationZ = 90f })
    }
}

@Composable
fun <T> Ribbon(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    preview: Int = 6,
    maxHeight: Dp = 360.dp,
    spacing: Dp = 0.dp,
    divider: Boolean = false,
    row: @Composable (index: Int, item: T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().animateContentSize(Motion.tween(320))) {
        if (!open || items.size <= preview) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                items.take(preview).forEachIndexed { i, item ->
                    key(key(item)) {
                        if (divider && i > 0) ListDivider()
                        row(i, item)
                    }
                }
            }
            if (items.size > preview) {
                if (divider) ListDivider()
                RibbonMore(items.size - preview) { open = true }
            }
        } else {
            val state = rememberLazyListState()
            val hold = remember(state) { HoldScroll(state) }
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = maxHeight).nestedScroll(hold).fadingEdges(state),
                state = state,
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                itemsIndexed(items, key = { _, item -> key(item) }) { i, item ->
                    Column(Modifier.animateItem()) {
                        if (divider && i > 0) ListDivider()
                        row(i, item)
                    }
                }
            }
        }
    }
}
