package com.flerty.wikiflerty.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TitleIcons: List<Pair<List<String>, () -> ImageVector>> = listOf(
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
    listOf("громкост", "звук", "атмосфер", "микс") to { AppIcons.Sound },
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
