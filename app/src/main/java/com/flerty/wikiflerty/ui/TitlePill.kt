package com.flerty.wikiflerty.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TitleIcons: List<Pair<List<String>, () -> ImageVector>> = listOf(
    listOf("поддерж") to { AppIcons.Heart },
    listOf("синхрон", "код синхрон") to { AppIcons.Cloud },
    listOf("qr") to { AppIcons.Qr },
    listOf("фокус") to { AppIcons.Moon },
    listOf("цвет") to { AppIcons.Palette },
    listOf("форма", "кноп") to { AppIcons.Corners },
    listOf("фон") to { AppIcons.Image },
    listOf("иконк") to { AppIcons.Grid },
    listOf("анимац") to { AppIcons.Animation },
    listOf("шрифт", "текст") to { AppIcons.TextSize },
    listOf("темп", "скорост") to { AppIcons.Speed },
    listOf("сеанс", "время", "таймер", "секундомер") to { AppIcons.Clock },
    listOf("оценк", "отзыв") to { AppIcons.StarFilled },
    listOf("ответ", "ии", "нейросет", "учител", "чат", "пересказ", "тест") to { AppIcons.Sparkle },
    listOf("обновлен") to { AppIcons.Download },
    listOf("о книге", "книг", "полк") to { AppIcons.Book },
    listOf("сбо", "журнал") to { AppIcons.Info },
    listOf("атмосфер") to { AppIcons.Fire },
    listOf("выделен", "под «⋯»") to { AppIcons.Select },
    listOf("перевод") to { AppIcons.Translate },
    listOf("громкост", "звук", "микс") to { AppIcons.Sound },
    listOf("файл", "копи", "экспорт", "импорт", "резерв") to { AppIcons.Backup },
    listOf("напомин", "уведомл") to { AppIcons.Bell },
    listOf("заморозк") to { AppIcons.Snow },
    listOf("серия") to { AppIcons.Fire },
    listOf("цел") to { AppIcons.Target },
    listOf("заметк", "цитат") to { AppIcons.Note },
    listOf("закладк") to { AppIcons.Bookmark },
    listOf("глав", "оглавл") to { AppIcons.Toc },
    listOf("поиск", "найти", "скан") to { AppIcons.Search },
    listOf("слов", "перевод") to { AppIcons.Translate },
    listOf("герои", "геро", "персонаж", "автор") to { AppIcons.Voice },
    listOf("истори") to { AppIcons.Clock },
    listOf("достижен") to { AppIcons.Trophy },
    listOf("показать", "фильтр", "вид", "сортиров") to { AppIcons.Sliders },
    listOf("опци", "настройк") to { AppIcons.Options },
    listOf("тема") to { AppIcons.Theme },
    listOf("панел") to { AppIcons.Panel },
    listOf("жест") to { AppIcons.Gesture },
    listOf("экран", "яркост") to { AppIcons.Screen },
    listOf("вслух", "голос") to { AppIcons.Headphones },
    listOf("листан", "прокрут") to { AppIcons.Scroll },
    listOf("ключ", "модел") to { AppIcons.Lock },
    listOf("переход") to { AppIcons.Forward },
    listOf("удалить") to { AppIcons.Trash },
    listOf("изменить", "редакт", "нов") to { AppIcons.Pencil },
)

fun titleIcon(title: String): ImageVector {
    val t = title.lowercase()
    return TitleIcons.firstOrNull { (keys, _) -> keys.any { t.contains(it) } }?.second?.invoke() ?: AppIcons.Bookmark
}

@Composable
fun TitlePill(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: TextUnit = 15.sp,
    maxWidth: Dp = 300.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tint = AppColors.Accent
    val shape = appCorner(16)
    Row(
        modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(AppColors.Field)
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(appCorner(9))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0.08f))))
                .border(1.dp, tint.copy(alpha = 0.2f), appCorner(9)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon ?: titleIcon(title), null, tint = tint, modifier = Modifier.size(15.dp)) }
        Text(
            title,
            color = AppColors.Text,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f, fill = false),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = size, stepSize = 0.5.sp),
        )
        trailing?.invoke()
    }
}

@Composable
fun TabbedFrame(
    title: String,
    icon: ImageVector? = null,
    inset: Boolean = true,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val decor = AppStyle.kit.decor
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().sheetReveal().fieldSurface(appCorner(18)).kitFrame()) {
        Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            var side by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
            if (trailing != null) androidx.compose.foundation.layout.Spacer(Modifier.width(with(density) { side.toDp() }))
            Box(Modifier.weight(1f).padding(horizontal = if (trailing != null) 6.dp else if (decor == Decor.Zen) 30.dp else 0.dp), contentAlignment = Alignment.Center) {
                val label = if (decor == Decor.News) title.uppercase() else title
                if (decor == Decor.Comic) {
                    val bubble = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    Box(
                        Modifier
                            .graphicsLayer { rotationZ = -1.5f }
                            .clip(bubble)
                            .background(androidx.compose.ui.graphics.Color(0xFFFFCC00))
                            .border(2.dp, androidx.compose.ui.graphics.Color(0xFF111111), bubble)
                            .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                    ) { HeaderLabel(label, icon = icon, size = 15.sp, textColor = androidx.compose.ui.graphics.Color(0xFF111111)) }
                } else {
                    HeaderLabel(label, icon = icon, size = 15.sp)
                }
            }
            if (trailing != null) Box(Modifier.onSizeChanged { side = it.width }) { trailing() }
        }
        if (trailing == null) KitSeal(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        }
        KitDivider()
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().then(if (inset) Modifier.padding(horizontal = 14.dp, vertical = 6.dp) else Modifier)) { content() }
    }
}

@Composable
fun PageBar(title: String, onBack: () -> Unit, icon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.88f, onClick = onBack).fieldSurface(CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Back, "Назад", tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { HeaderLabel(title, icon = icon, size = 17.sp) }
            Box(Modifier.widthIn(min = 42.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
        }
        SheetDivider()
    }
}

@Composable
fun HeaderLabel(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, size: TextUnit = 16.sp, textColor: androidx.compose.ui.graphics.Color? = null) {
    val tint = AppColors.Accent
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .clip(appCorner(9))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0.08f))))
                .border(1.dp, tint.copy(alpha = 0.2f), appCorner(9)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon ?: titleIcon(title), null, tint = tint, modifier = Modifier.size(16.dp)) }
        Text(
            title,
            color = textColor ?: AppColors.Text,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f, fill = false),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = size, stepSize = 0.5.sp),
        )
    }
}

fun Modifier.bleed(horizontal: Dp): Modifier = this.then(
    androidx.compose.ui.Modifier.layout { measurable, constraints ->
        val extra = (horizontal * 2).roundToPx()
        val max = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        val placeable = measurable.measure(constraints.copy(minWidth = (constraints.minWidth + extra).coerceAtMost(max), maxWidth = max))
        layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
    },
)

@Composable
fun SheetDivider(horizontal: Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().bleed(horizontal).height(1.dp).background(AppColors.PanelBorder))
}
