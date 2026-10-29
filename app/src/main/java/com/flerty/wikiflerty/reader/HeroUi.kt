package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flerty.wikiflerty.data.Hero
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.ConfirmSheet
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.SettingSwitch
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.fieldSurface
import com.flerty.wikiflerty.ui.liveClick
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HeroUiState {
    val heroes = mutableStateListOf<Hero>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)
    var current by mutableStateOf<Hero?>(null)
    var noSpoilers by mutableStateOf(true)
    var input by mutableStateOf("")
    var avatarVersion by mutableIntStateOf(0)
    var place by mutableStateOf("")
    val chat = AiChatState()
}

class HeroBridge(
    val state: HeroUiState,
    val author: () -> String,
    val avatarFile: (String) -> File,
    val onFind: () -> Unit,
    val onOpen: (Hero) -> Unit,
    val onAdd: (String) -> Unit,
    val onRemove: (Hero) -> Unit,
    val onAvatar: (Hero) -> Unit,
    val onSpoilers: (Boolean) -> Unit,
    val onSend: (String) -> Unit,
    val onStop: () -> Unit,
    val onRetry: () -> Unit,
    val onVoice: () -> Unit,
    val onReset: () -> Unit = {},
)

@Composable
fun HeroAvatar(bridge: HeroBridge, hero: Hero, size: Dp, editable: Boolean = true, onEdit: (() -> Unit)? = null) {
    val file = bridge.avatarFile(hero.id)
    val version = bridge.state.avatarVersion
    val image by produceState<ImageBitmap?>(null, file, version) {
        value = withContext(Dispatchers.IO) {
            if (file.exists()) runCatching { android.graphics.BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() else null
        }
    }
    Box(Modifier.size(size)) {
        val click = if (editable) Modifier.liveClick(CircleShape, sound = FeedbackSound.Open, pressedScale = 0.9f) { (onEdit ?: { bridge.onAvatar(hero) })() } else Modifier
        Box(
            Modifier
                .fillMaxSize()
                .then(click)
                .clip(CircleShape)
                .background(Color(0xFF0E0C0A))
                .border(1.dp, AppColors.PanelBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = image
            if (bmp != null) {
                Image(bmp, hero.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(AppIcons.Pencil, "Поставить аватар", tint = Color(0xFFE8DED2), modifier = Modifier.size(size * 0.38f))
            }
        }
        if (editable && image != null && size >= 40.dp) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(size * 0.36f)
                    .clip(CircleShape)
                    .background(Color(0xFF0E0C0A))
                    .border(1.5.dp, AppColors.Background, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Pencil, null, tint = Color(0xFFE8DED2), modifier = Modifier.size(size * 0.18f)) }
        }
    }
}

@Composable
fun HeroesContent(bridge: HeroBridge, modifier: Modifier = Modifier) {
    val state = bridge.state
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var toRemove by remember { mutableStateOf<Hero?>(null) }
    LaunchedEffect(Unit) {
        if (state.heroes.none { !it.custom } && !state.loading && !state.searched) bridge.onFind()
    }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (state.noSpoilers) "ИИ нашёл героев в прочитанной части" else "Герои книги",
            color = AppColors.Muted,
            fontSize = 12.5.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(Modifier.fillMaxWidth().fieldSurface(appCorner(16)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            SettingSwitch(
                "Без спойлеров",
                state.noSpoilers,
                hint = "Герой знает только то, что ты прочитал",
                icon = AppIcons.Lock,
            ) { bridge.onSpoilers(it) }
        }
        if (state.loading) {
            Row(
                Modifier.fillMaxWidth().fieldSurface(appCorner(16)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                Text("Ищу героев в книге…", color = AppColors.Muted, fontSize = 14.sp)
            }
        }
        state.error?.let { err ->
            Column(
                Modifier.fillMaxWidth().clip(appCorner(14)).background(AppColors.Bad.copy(alpha = 0.12f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(err, color = AppColors.Bad, fontSize = 13.sp, lineHeight = 18.sp)
                Text(
                    "Попробовать ещё раз",
                    color = AppColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.liveClick(appCorner(8), onClick = bridge.onFind).padding(vertical = 4.dp),
                )
            }
        }
        state.heroes.forEach { hero ->
            HeroRow(bridge, hero, onLong = { toRemove = hero })
        }
        if (!state.loading && state.searched && state.heroes.isNotEmpty()) {
            Text(
                "Найти заново",
                color = AppColors.Faint,
                fontSize = 12.sp,
                modifier = Modifier.liveClick(appCorner(8), sound = FeedbackSound.Tap, onClick = bridge.onFind).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        val dashed = appCorner(16)
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clip(dashed)
                .border(1.5.dp, AppColors.Line, dashed)
                .liveClick(dashed, sound = FeedbackSound.Open, pressedScale = 0.98f) { adding = !adding }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(AppColors.Field), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Plus, null, tint = AppColors.Faint, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Свой герой", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text("Впиши имя, если ИИ его пропустил", color = AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            AnimatedVisibility(adding, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp),
                        cursorBrush = SolidColor(AppColors.Accent),
                        modifier = Modifier.weight(1f).height(44.dp).fieldSurface(appCorner(14)).padding(horizontal = 14.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                                if (name.isEmpty()) Text("Имя героя", color = AppColors.Faint, fontSize = 15.sp)
                                inner()
                            }
                        },
                    )
                    Box(
                        Modifier
                            .size(44.dp)
                            .liveClick(CircleShape, sound = FeedbackSound.Toggle) {
                                if (name.isNotBlank()) {
                                    bridge.onAdd(name.trim())
                                    name = ""
                                    adding = false
                                }
                            }
                            .background(if (name.isNotBlank()) AppColors.Accent else AppColors.Line),
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Check, "Добавить", tint = if (name.isNotBlank()) AppColors.OnAccent else AppColors.Faint, modifier = Modifier.size(20.dp)) }
                }
            }
        }
        val authorName = bridge.author()
        if (authorName.isNotBlank()) {
            val authorHero = Hero(Hero.AUTHOR_ID, authorName, "Автор книги")
            val shape = appCorner(16)
            Row(
                Modifier
                    .fillMaxWidth()
                    .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f) { bridge.onOpen(authorHero) }
                    .background(AppColors.Accent.copy(alpha = 0.1f))
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.3f), shape)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroAvatar(bridge, authorHero, 46.dp)
                Column(Modifier.weight(1f)) {
                    Text("Поговорить с автором", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(authorName, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(AppIcons.Forward, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
    toRemove?.let { hero ->
        ConfirmSheet(
            title = "Убрать героя?",
            text = "«${hero.name}» пропадёт из списка вместе с аватаром.",
            confirm = "Убрать",
            onConfirm = { bridge.onRemove(hero) },
            onDismiss = { toRemove = null },
        )
    }
}

@Composable
private fun HeroRow(bridge: HeroBridge, hero: Hero, onLong: () -> Unit) {
    val shape = appCorner(16)
    Row(
        Modifier
            .fillMaxWidth()
            .liveClick(shape, sound = FeedbackSound.Open, pressedScale = 0.98f, onLongClick = onLong) { bridge.onOpen(hero) }
            .fieldSurface(shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroAvatar(bridge, hero, 46.dp)
        Column(Modifier.weight(1f)) {
            Text(hero.name, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hero.role.isNotBlank()) {
                Text(hero.role, color = AppColors.Muted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            "Говорить",
            color = AppColors.Accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, AppColors.Accent.copy(alpha = 0.45f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun HeroChatContent(bridge: HeroBridge, modifier: Modifier = Modifier) {
    val state = bridge.state
    val hero = state.current ?: return
    val chat = state.chat
    val listState = rememberLazyListState()
    val isAuthor = hero.id == Hero.AUTHOR_ID
    FollowBottom(listState, chat.bubbles.size)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroAvatar(bridge, hero, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (isAuthor) "Автор книги" else hero.role.ifBlank { "Герой книги" },
                    color = AppColors.Muted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.noSpoilers) "● без спойлеров · ${state.place}" else "● знает всю книгу",
                    color = if (state.noSpoilers) AppColors.Good else AppColors.Warn,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier
                    .liveClick(CircleShape, sound = FeedbackSound.Open) { if (!chat.streaming) bridge.onReset() }
                    .border(1.dp, AppColors.Line, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(AppIcons.Rotate, null, tint = AppColors.Muted, modifier = Modifier.size(14.dp))
                Text("Заново", color = AppColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(chat.bubbles) { index, bubble ->
                if (bubble.fromUser) {
                    if (bubble.shown.isNotBlank()) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                bubble.shown,
                                color = AppColors.Text,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier
                                    .widthIn(max = 290.dp)
                                    .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                                    .background(AppColors.Line)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                } else {
                    val writing = chat.streaming && index == chat.bubbles.lastIndex
                    HeroBubble(bridge, hero, bubble.shown, writing)
                }
            }
            chat.error?.let { error ->
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(appCorner(14)).background(AppColors.Bad.copy(alpha = 0.12f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(error, color = AppColors.Bad, fontSize = 13.sp, lineHeight = 18.sp)
                        Text(
                            "Повторить",
                            color = AppColors.Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.liveClick(appCorner(8), onClick = bridge.onRetry).padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = !chat.streaming && chat.bubbles.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ideas = if (isAuthor) {
                    listOf("Зачем вы написали эту книгу?", "Что вы хотели сказать?", "Кто из героев вам ближе?")
                } else {
                    listOf("Расскажи о себе", "Что тебя тревожит?", "Как ты относишься к остальным?")
                }
                ideas.forEach { idea ->
                    val shape = appCorner(18)
                    Box(
                        Modifier.height(34.dp)
                            .liveClick(shape, sound = FeedbackSound.Tap) { bridge.onSend(idea) }
                            .border(1.dp, AppColors.Line, shape)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(idea, color = AppColors.Text, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).fieldSurface(appCorner(24)).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = state.input,
                onValueChange = { state.input = it },
                textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(AppColors.Accent),
                maxLines = 4,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                decorationBox = { inner ->
                    Box {
                        if (state.input.isEmpty()) {
                            Text(
                                "Спроси ${if (isAuthor) "автора" else hero.name.substringBefore(' ')}…",
                                color = AppColors.Faint,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            Box(
                Modifier.size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Tap, onClick = bridge.onVoice),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Voice, "Сказать голосом", tint = AppColors.Muted, modifier = Modifier.size(20.dp)) }
            Box(
                Modifier
                    .size(38.dp)
                    .liveClick(CircleShape, sound = FeedbackSound.Tap, pressedScale = 0.88f) {
                        if (chat.streaming) {
                            bridge.onStop()
                        } else if (state.input.isNotBlank()) {
                            bridge.onSend(state.input.trim())
                            state.input = ""
                        }
                    }
                    .background(if (chat.streaming || state.input.isNotBlank()) AppColors.Accent else AppColors.Line),
                contentAlignment = Alignment.Center,
            ) {
                if (chat.streaming) {
                    Box(Modifier.size(12.dp).clip(appCorner(2)).background(AppColors.OnAccent))
                } else {
                    Icon(AppIcons.Send, "Отправить", tint = if (state.input.isNotBlank()) AppColors.OnAccent else AppColors.Faint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroBubble(bridge: HeroBridge, hero: Hero, text: String, writing: Boolean) {
    val count = rememberRevealCount(text, writing)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HeroAvatar(bridge, hero, 26.dp, editable = false)
        Box(
            Modifier
                .weight(1f)
                .animateContentSize(androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(Brush.verticalGradient(listOf(AppColors.Field, AppColors.Field)))
                .border(1.dp, AppColors.PanelBorder, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (count == 0 && writing) {
                HeroTyping()
            } else {
                Text(
                    revealText(text, count, writing, AppColors.Text),
                    color = AppColors.Text,
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun HeroTyping() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "hero")
    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    com.flerty.wikiflerty.ui.Motion.tween<Float>(520, delayMillis = i * 160),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "d$i",
            )
            Box(Modifier.size(7.dp).clip(CircleShape).background(AppColors.Accent.copy(alpha = alpha)))
        }
    }
}
