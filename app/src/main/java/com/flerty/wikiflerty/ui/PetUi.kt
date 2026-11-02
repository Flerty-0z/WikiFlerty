package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.pet.Pet
import com.flerty.wikiflerty.pet.PetArt
import com.flerty.wikiflerty.pet.PetLevels
import com.flerty.wikiflerty.pet.PetLook
import com.flerty.wikiflerty.pet.PetSpecies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PetCanvas(species: String, look: PetLook, size: Dp, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
    val transition = rememberInfiniteTransition(label = "pet")
    val breathe by transition.animateFloat(
        0f,
        (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "breathe",
    )
    val blink = remember { Animatable(1f) }
    val hop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(look) {
        if (look == PetLook.Sleep) return@LaunchedEffect
        while (true) {
            delay((2500L..5200L).random())
            blink.animateTo(0.1f, tween(70))
            blink.animateTo(1f, tween(110))
        }
    }
    val tap = if (onTap == null) Modifier else Modifier.liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.95f) {
        scope.launch {
            hop.animateTo(-1f, tween(140))
            hop.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 500f))
        }
        onTap()
    }
    Canvas(
        modifier
            .size(size)
            .then(tap)
            .graphicsLayer { translationY = hop.value * size.toPx() * 0.12f },
    ) {
        drawIntoCanvas { c ->
            PetArt.draw(c.nativeCanvas, species, look, this.size.minDimension, blink.value, kotlin.math.sin(breathe))
        }
    }
}

@Composable
private fun EmojiBadge(emoji: String, color: Color) {
    Box(
        Modifier
            .size(34.dp)
            .clip(appCorner(11))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.08f))))
            .border(1.dp, color.copy(alpha = 0.22f), appCorner(11)),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 17.sp) }
}

@Composable
private fun WellTile(emoji: String, label: String, value: String, level: Float?, color: Color, info: String, pill: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val v by animateFloatAsState(((level ?: 0f) / 100f).coerceIn(0f, 1f), Motion.tween(700), label = "bar")
    val shape = appCorner(18)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Line.copy(alpha = 0.45f))
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .animateContentSize(Motion.tween(240)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EmojiBadge(emoji, color)
            Text(label, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Box(
                Modifier
                    .size(24.dp)
                    .liveClick(CircleShape, sound = FeedbackSound.Toggle, pressedScale = 0.85f) { open = !open }
                    .background(if (open) color.copy(alpha = 0.18f) else Color.Transparent, CircleShape)
                    .border(1.dp, if (open) color.copy(alpha = 0.6f) else AppColors.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("?", color = if (open) color else AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.weight(1f))
            if (pill) {
                Text(
                    value,
                    color = AppColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(AppColors.AccentTint).border(1.dp, AppColors.Accent.copy(alpha = 0.35f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            } else {
                Text(value, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
        if (level != null) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(AppColors.Line)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(v).clip(CircleShape).background(color))
            }
        }
        if (open) {
            Text(
                info,
                color = AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color.copy(alpha = 0.08f), appCorner(12))
                    .border(1.dp, color.copy(alpha = 0.25f), appCorner(12))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

private class XpRule(val emoji: String, val title: String, val text: String, val value: String, val color: Long)

private val XpRules = listOf(
    XpRule("📖", "Страница", "За каждую прочитанную страницу", "+1", 0xFFF2A65A),
    XpRule("📑", "Глава", "Когда дочитываешь главу до конца", "+15", 0xFFF7D774),
    XpRule("📕", "Книга", "Когда отмечаешь книгу прочитанной", "+100", 0xFFFF8A80),
    XpRule("🎯", "Цель дня", "Когда выполняешь дневную цель по времени", "+20", 0xFF7ED3A0),
    XpRule("🔥", "Серия дней", "Опыт за страницы растёт на 2% за каждый день серии, максимум ×1.5", "×1.5", 0xFFFFA25A),
    XpRule("📝", "Заметка", "За каждую заметку или выделение", "+3", 0xFF9FC4FF),
    XpRule("🔤", "Слово", "За каждое слово, добавленное в словарь", "+2", 0xFFB9A2FF),
    XpRule("🃏", "Карточка", "За каждую повторённую карточку", "+1", 0xFF7DD3FC),
    XpRule("🎓", "Учитель", "За верный ответ ИИ-учителю, за почти верный — +2", "+5", 0xFFFFC94D),
    XpRule("🏆", "Достижение", "За каждое новое достижение", "+25", 0xFFFFD76A),
)

@Composable
private fun XpGuideSheet(onDismiss: () -> Unit) {
    AppSheet(onDismiss, title = "Как получать опыт") {
        TileGroup {
            XpRules.forEach { rule ->
                WellTile(rule.emoji, rule.title, rule.value, null, Color(rule.color), rule.text, pill = true)
            }
        }
    }
}

@Composable
private fun TileGroup(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(22)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun XpBar(pet: Pet) {
    val level = pet.level
    val from = PetLevels.total(level)
    val to = PetLevels.total(level + 1)
    val frac by animateFloatAsState(((pet.xp - from).toFloat() / (to - from)).coerceIn(0f, 1f), Motion.tween(800), label = "xp")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text("Ур. $level", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("${pet.xp - from}/${to - from}", color = AppColors.Faint, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(AppColors.Line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(CircleShape).background(Brush.horizontalGradient(listOf(AppColors.Accent, AppColors.AccentLight))))
        }
    }
}

@Composable
fun PetCard(onOpen: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val pet by app.pet.pet.collectAsStateWithLifecycle()
    var adopting by remember { mutableStateOf(false) }
    val shape = appCorner(20)
    val p = pet
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { if (p == null) adopting = true else onOpen() }
            .fieldSurface(shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (p == null) {
            PetCanvas("cat", PetLook.Happy, 64.dp)
            Column(Modifier.weight(1f)) {
                Text("Заведи питомца", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text("Он радуется, когда ты читаешь", color = AppColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(AppIcons.Forward, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
        } else {
            val mood = app.pet.mood(p)
            PetCanvas(p.species.id, app.pet.look(mood), 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.name, color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mood.text, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                XpBar(p)
            }
        }
    }
    if (adopting) AdoptSheet { adopting = false }
}

@Composable
fun AdoptSheet(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    var chosen by remember { mutableStateOf(PetSpecies.Cat) }
    var name by remember { mutableStateOf("") }
    AppSheet(onDismiss, title = "Твой питомец") {
        val close = sheetClose(onDismiss)
        Text(
            "Выбери зверька один раз — он будет с тобой всегда и радоваться твоему чтению.",
            color = AppColors.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        PetSpecies.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    val on = s == chosen
                    val shape = appCorner(18)
                    Column(
                        Modifier
                            .weight(1f)
                            .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.95f) { chosen = s }
                            .then(if (on) Modifier.background(AppColors.AccentTint, shape).border(1.5.dp, AppColors.Accent, shape) else Modifier.fieldSurface(shape))
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PetCanvas(s.id, PetLook.Happy, 76.dp)
                        Text(s.title, color = if (on) AppColors.Accent else AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        Text(chosen.hint, color = AppColors.Faint, fontSize = 12.5.sp, modifier = Modifier.padding(start = 4.dp))
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            singleLine = true,
            textStyle = TextStyle(color = AppColors.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(AppColors.Accent),
            modifier = Modifier.fillMaxWidth().height(50.dp).fieldSurface(appCorner(16)).padding(horizontal = 16.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (name.isEmpty()) Text("Имя питомца", color = AppColors.Faint, fontSize = 16.sp)
                    inner()
                }
            },
        )
        val shape = appCorner(16)
        val ready = name.isNotBlank()
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .liveClick(shape, sound = FeedbackSound.Toggle, enabled = ready) {
                    app.pet.adopt(chosen, name)
                    close()
                }
                .then(if (ready) Modifier.accentHighlight(shape) else Modifier.border(1.dp, AppColors.Line, shape)),
            contentAlignment = Alignment.Center,
        ) { Text("Завести", color = if (ready) accentContent else AppColors.Faint, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
fun PetPage(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val petState by app.pet.pet.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { app.pet.settle() }
            delay(60_000)
        }
    }
    var renaming by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf(false) }
    var hearts by remember { mutableStateOf(0) }
    val pet = petState ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val mood = app.pet.mood(pet)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).liveClick(CircleShape, sound = FeedbackSound.Close, pressedScale = 0.88f, onClick = onBack).fieldSurface(CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Back, "Назад", tint = AppColors.Text, modifier = Modifier.size(20.dp)) }
            Text("Питомец", color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier.fillMaxWidth().fieldSurface(appCorner(24)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(contentAlignment = Alignment.TopCenter) {
                PetCanvas(pet.species.id, app.pet.look(mood), 200.dp) { hearts++ }
                if (hearts > 0) {
                    val rise = remember(hearts) { Animatable(0f) }
                    LaunchedEffect(hearts) { rise.animateTo(1f, tween(900)) }
                    Text(
                        "❤",
                        color = Color(0xFFFF6B81),
                        fontSize = 22.sp,
                        modifier = Modifier.graphicsLayer {
                            translationY = -rise.value * 60f
                            alpha = 1f - rise.value
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(pet.name, color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Box(
                    Modifier.size(30.dp).liveClick(CircleShape, sound = FeedbackSound.Open) { renaming = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Pencil, "Переименовать", tint = AppColors.Faint, modifier = Modifier.size(15.dp)) }
            }
            Text(pet.species.title, color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(mood.text, color = AppColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Box(Modifier.padding(top = 6.dp)) { XpBar(pet) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Самочувствие")
            TileGroup {
            WellTile("🍎", "Сытость", "${pet.food.toInt()}", pet.food, Color(0xFFF2A65A), "Растёт от прочитанных страниц и законченных книг. Без чтения питомец понемногу голодает.")
            WellTile("😊", "Настроение", "${pet.mood.toInt()}", pet.mood, Color(0xFFF7D774), "Поднимают заметки, новые слова, карточки, главы и достижения. При заморозке серии питомец уходит в отпуск и не скучает.")
            WellTile("⚡", "Энергия", "${pet.energy.toInt()}", pet.energy, Color(0xFF7DD3FC), "Восстанавливается сама, ночью быстрее — с 23:00 до 7:00 питомец спит. Падает, если читать с часу до пяти ночи.")
            WellTile(
                "🧠",
                "Мудрость",
                "${pet.counters.books} " + StatsCalc.plural(pet.counters.books, "книга", "книги", "книг"),
                null,
                Color(0xFFFF9AA8),
                "Сколько книг вы прочитали вместе. Каждая книга даёт много опыта.",
            )
            }
        }

        SettingsNavRow(AppIcons.Info, "Как получать опыт", "") { guide = true }
    }
    if (guide) XpGuideSheet { guide = false }
    if (renaming) RenameSheet(pet.name) { renaming = false }
}

@Composable
private fun RenameSheet(current: String, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    var name by remember { mutableStateOf(current) }
    AppSheet(onDismiss, title = "Имя питомца") {
        val close = sheetClose(onDismiss)
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            singleLine = true,
            textStyle = TextStyle(color = AppColors.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(AppColors.Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).fieldSurface(appCorner(16)).padding(horizontal = 16.dp, vertical = 14.dp),
        )
        AppButton("Сохранить", primary = true, modifier = Modifier.fillMaxWidth()) {
            app.pet.rename(name)
            close()
        }
    }
}
