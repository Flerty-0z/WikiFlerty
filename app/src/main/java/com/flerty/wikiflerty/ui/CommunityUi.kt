package com.flerty.wikiflerty.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.Achievements
import com.flerty.wikiflerty.data.ReadStatus
import com.flerty.wikiflerty.sync.Community
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CommunityNav {
    var room by mutableStateOf<String?>(null)
    var roomTitle by mutableStateOf("")
    var mod by mutableStateOf(false)
    var sprint by mutableStateOf(false)
    var searching by mutableStateOf(false)
    var draft by mutableStateOf("")
    var thread by mutableStateOf<Pair<String, String>?>(null)
    var pendingRoom by mutableStateOf<String?>(null)
    var pendingThread: Pair<String, String>? = null
    var pendingProgress: Double = 0.0
    var readerDiscuss: (() -> Unit)? = null
    var roomsY = 0
    val title: String get() = when {
        mod -> "Модерация"
        sprint -> "Спринт чтения"
        room != null -> roomTitle
        else -> "Сообщество"
    }

    fun back(): Boolean {
        if (searching) {
            searching = false
            return true
        }
        if (mod) {
            mod = false
            return true
        }
        if (sprint) {
            sprint = false
            return true
        }
        if (room != null) {
            room = null
            thread = null
            return true
        }
        return false
    }
}

private val reactions = listOf(
    "heart" to (AppIcons.Heart to Color(0xFFE88A9A)),
    "flame" to (AppIcons.Fire to Color(0xFFE8A458)),
    "thumb" to (AppIcons.Thumb to Color(0xFFE0B04A)),
    "smile" to (AppIcons.Smile to Color(0xFFB9C46A)),
    "mark" to (AppIcons.Bookmark to Color(0xFF8FB4E0)),
)

fun roomIcon(id: String): ImageVector = when (id) {
    "book" -> AppIcons.Book
    "search" -> AppIcons.Search
    "head" -> AppIcons.Headphones
    "comic" -> AppIcons.Comic
    "bulb" -> AppIcons.Bulb
    "star" -> AppIcons.Star
    "quote" -> AppIcons.Quote
    else -> AppIcons.Chat
}

private val avatarTints = listOf(0xFF8A5A3A, 0xFF4F6B8C, 0xFF6B4F80, 0xFF3F6E58, 0xFF8C4A52, 0xFF7A6A3C, 0xFF4E7A7A, 0xFF8A6440)

@Composable
fun LetterAvatar(login: String, size: Dp, modifier: Modifier = Modifier) {
    val tint = Color(avatarTints[(login.hashCode() and 0x7fffffff) % avatarTints.size])
    val shape = appCorner((size.value * 0.32f).toInt())
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint, Color.Black.copy(alpha = 0.55f).compositeOverSafe(tint))))
            .border(1.dp, AppColors.Accent.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(login.firstOrNull()?.uppercase() ?: "?", color = Color.White.copy(alpha = 0.92f), fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun Color.compositeOverSafe(bg: Color): Color = Color(
    red = red * alpha + bg.red * (1 - alpha),
    green = green * alpha + bg.green * (1 - alpha),
    blue = blue * alpha + bg.blue * (1 - alpha),
)

@Composable
private fun Tile(icon: ImageVector, size: Dp = 34.dp, tint: Color = AppColors.Accent, filled: Boolean = false) {
    val shape = appCorner((size.value * 0.3f).toInt())
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(if (filled) Brush.linearGradient(listOf(AppColors.Accent, AppColors.AccentDark)) else Brush.linearGradient(listOf(tint.copy(alpha = 0.24f), tint.copy(alpha = 0.08f))))
            .border(1.dp, if (filled) AppColors.Accent else tint.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (filled) AppColors.OnAccent else tint, modifier = Modifier.size(size * 0.52f)) }
}

@Composable
private fun OnlineDot(n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(AppColors.Good, CircleShape))
        Text("$n", color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

fun chatAgo(at: Long, now: Long = System.currentTimeMillis()): String {
    val d = (now - at).coerceAtLeast(0)
    return when {
        d < 60_000 -> "сейчас"
        d < 3_600_000 -> "${d / 60_000} мин"
        d < 86_400_000 && sameDay(at, now) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
        sameDay(at + 86_400_000, now) -> "вчера"
        else -> SimpleDateFormat("d MMM", Locale.forLanguageTag("ru")).format(Date(at))
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(at: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(at, now) -> "Сегодня"
        sameDay(at + 86_400_000, now) -> "Вчера"
        else -> SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru")).format(Date(at))
    }
}

@Composable
fun CommunityShowcase(onOpen: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    if (!app.account.available) return
    val community = app.community
    val state by community.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                community.refresh()
                delay(30_000)
            }
        }
    }
    val mentions = state.me.mentions.size
    Column(
        Modifier
            .fillMaxWidth()
            .scrollReveal()
            .liveClick(appCorner(20), sound = FeedbackSound.Open, pressedScale = 0.98f, onClick = onOpen)
            .fieldSurface(appCorner(20))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(AppIcons.Chat, 36.dp)
            Text("Сообщество", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            if (mentions > 0) {
                Box(Modifier.heightIn(min = 22.dp).clip(CircleShape).background(AppColors.Accent).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                    Text("$mentions", color = AppColors.OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (state.loaded && state.on) OnlineDot(state.online)
            Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
        }
        val latest = state.latest
        AnimatedContent(
            targetState = Triple(state.loaded, state.on, latest?.id),
            transitionSpec = { (fadeIn(Motion.fade(260)) + androidx.compose.animation.slideInVertically(Motion.tween(260)) { it / 3 }).togetherWith(fadeOut(Motion.fade(160))) },
            label = "community-latest",
        ) { (loaded, on, _) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(appCorner(14))
                    .background(AppColors.Background.copy(alpha = 0.55f))
                    .border(1.dp, AppColors.Line, appCorner(14))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    !loaded -> Text("Загружаю…", color = AppColors.Faint, fontSize = 13.sp)
                    !on -> Text("Сообщество временно выключено", color = AppColors.Muted, fontSize = 13.sp)
                    latest == null -> Text("Здесь пока тихо — напиши первым", color = AppColors.Muted, fontSize = 13.sp)
                    else -> {
                        LetterAvatar(latest.login, 30.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = AppColors.Accent)) { append(latest.login) }
                                    withStyle(SpanStyle(color = AppColors.Muted)) { append(" · " + latest.roomTitle) }
                                },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(latest.text, color = AppColors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(chatAgo(latest.at), color = AppColors.Faint, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val community = app.community
    val state by community.state.collectAsStateWithLifecycle()
    val account by app.account.state.collectAsStateWithLifecycle()
    var rules by remember { mutableStateOf(false) }
    LaunchedEffect(account.login) {
        community.refresh().onSuccess { if (it.me.login != null && !it.me.accepted) rules = true else if (it.me.login != null) community.shareProfile() }
    }
    BackHandler(enabled = CommunityNav.room != null || CommunityNav.mod || CommunityNav.sprint || CommunityNav.searching) { CommunityNav.back() }
    LaunchedEffect(CommunityNav.pendingRoom) {
        val target = CommunityNav.pendingRoom ?: return@LaunchedEffect
        CommunityNav.pendingRoom = null
        CommunityNav.mod = false
        CommunityNav.sprint = false
        CommunityNav.searching = false
        val thread = CommunityNav.pendingThread
        CommunityNav.pendingThread = null
        if (target.startsWith("b:")) {
            val meta = thread ?: community.threadMeta(target)
            if (meta != null) {
                if (community.spot(target) == null) community.rememberSpot(meta.first, meta.second, (CommunityNav.pendingProgress * 100).toInt(), "")
                CommunityNav.thread = meta
                CommunityNav.roomTitle = meta.first
                CommunityNav.room = target
            }
        } else {
            CommunityNav.thread = null
            CommunityNav.roomTitle = state.rooms.firstOrNull { it.id == target }?.title ?: CommunityNav.roomTitle.ifBlank { "Сообщество" }
            CommunityNav.room = target
        }
    }
    LaunchedEffect(Unit) { community.reportPages() }
    if (rules) RulesSheet(onAccept = { rules = false }) { rules = false }
    val target = when {
        CommunityNav.mod -> "mod"
        CommunityNav.sprint -> "sprint"
        CommunityNav.room == "ideas" -> "ideas"
        CommunityNav.room != null -> "room:" + CommunityNav.room
        else -> "rooms"
    }
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            val deeper = targetState != "rooms"
            val dir = if (deeper) 1 else -1
            (slideInHorizontally(Motion.tween(260)) { dir * it / 5 } + fadeIn(Motion.fade(220)))
                .togetherWith(slideOutHorizontally(Motion.tween(220)) { -dir * it / 5 } + fadeOut(Motion.fade(160)))
        },
        label = "community",
        modifier = modifier,
    ) { page ->
        when {
            page == "mod" -> ModPage()
            page == "sprint" -> SprintPage(state)
            page == "ideas" -> IdeasPage(state) { rules = true }
            page.startsWith("room:") -> RoomPage(page.removePrefix("room:"), state) { rules = true }
            else -> RoomsPage(state, account.login)
        }
    }
}

@Composable
private fun RoomsPage(state: Community.State, login: String?) {
    val app = LocalContext.current.applicationContext as App
    val community = app.community
    val seen by community.seen.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(20_000)
                community.refresh()
            }
        }
    }
    val scroll = rememberScrollState(CommunityNav.roomsY)
    DisposableEffect(scroll) { onDispose { CommunityNav.roomsY = scroll.value } }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!state.loaded) {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(28.dp)) }
            return@Column
        }
        if (state.error != null && state.rooms.isEmpty()) {
            HintNote(state.error, AppIcons.Alert)
            return@Column
        }
        if (!state.on) {
            OffCard()
            return@Column
        }
        if (state.me.admin) {
            Row(
                Modifier.fillMaxWidth().scrollReveal().liveClick(appCorner(18), sound = FeedbackSound.Open, pressedScale = 0.98f) { CommunityNav.mod = true }.fieldSurface(appCorner(18)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tile(AppIcons.Shield, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text("Модерация", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (state.me.reports > 0) "Жалоб: ${state.me.reports}" else "Жалоб нет", color = AppColors.Muted, fontSize = 12.sp)
                }
                Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
            }
        }
        state.pin?.let { pin ->
            Row(
                Modifier.fillMaxWidth().scrollReveal().clip(appCorner(18)).background(AppColors.AccentTintStrong).border(1.dp, AppColors.Accent.copy(alpha = 0.45f), appCorner(18)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tile(AppIcons.Pin, 34.dp, filled = true)
                Text(pin, color = AppColors.Text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            }
        }
        val mentions = state.me.mentions
        if (mentions.isNotEmpty()) {
            val top = mentions.maxBy { it.id }
            Row(
                Modifier.fillMaxWidth().scrollReveal().liveClick(appCorner(18), sound = FeedbackSound.Mention, pressedScale = 0.98f) {
                    community.markMentions()
                    if (top.room.startsWith("b:")) {
                        CommunityNav.pendingThread = community.threadMeta(top.room) ?: (top.title to "")
                        CommunityNav.pendingRoom = top.room
                    } else {
                        state.rooms.firstOrNull { it.id == top.room }?.let { r -> CommunityNav.roomTitle = r.title; CommunityNav.room = r.id }
                    }
                }.fieldSurface(appCorner(18)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tile(AppIcons.At, 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (mentions.size == 1) "Тебе ответили" else "Ответов тебе: ${mentions.size}", color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${top.from}: ${top.text}", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
            }
        }
        SprintCard(state.sprint, login != null)
        state.quote?.let { DayQuoteCard(it) }
        state.goal?.let { GoalCard(it) }
        state.rooms.forEach { room ->
            val unread = room.last != null && room.last.login != login && room.last.id > (seen[room.id] ?: 0L)
            Row(
                Modifier
                    .fillMaxWidth()
                    .scrollReveal()
                    .liveClick(appCorner(18), sound = FeedbackSound.Open, pressedScale = 0.98f) {
                        CommunityNav.roomTitle = room.title
                        CommunityNav.room = room.id
                    }
                    .fieldSurface(appCorner(18))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tile(roomIcon(room.icon), 42.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(room.title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (room.online > 0) OnlineDot(room.online)
                    }
                    val last = room.last
                    Text(
                        if (last == null) AnnotatedString("Пока пусто") else buildAnnotatedString {
                            withStyle(SpanStyle(color = AppColors.Text)) { append(last.login + ": ") }
                            append(last.text)
                        },
                        color = AppColors.Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    room.last?.let { Text(chatAgo(it.at), color = AppColors.Faint, fontSize = 11.sp, maxLines = 1) }
                    if (unread) Box(Modifier.size(9.dp).background(AppColors.Accent, CircleShape))
                }
            }
        }
        if (state.me.login == null) GuestCard() else NotifySettings()
    }
}

@Composable
private fun OffCard() {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp).scrollReveal().fieldSurface(appCorner(22)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tile(AppIcons.Power, 64.dp)
        Text("Сообщество временно выключено", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Загляни позже. Новости — в нашем Телеграм-канале", color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun GuestCard() {
    Column(
        Modifier.fillMaxWidth().scrollReveal().fieldSurface(appCorner(18)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(AppIcons.Lock, 32.dp)
            Text("Читать может каждый. Писать — после входа в аккаунт", color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        }
        AppButton("Войти и писать", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Open) {
            CommunityNav.room = null
            SettingsNav.account = true
        }
    }
}

@Composable
private fun RulesSheet(onAccept: () -> Unit, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    var agree by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { feedback?.play(FeedbackSound.Rules) }
    AppSheet(onDismiss = onDismiss, title = "Правила сообщества") {
        listOf(
            AppIcons.Smile to "Уважай собеседников: без оскорблений и травли",
            AppIcons.Ban to "Без рекламы, спама, ссылок и телефонов",
            AppIcons.EyeOff to "Не раскрывай сюжет без предупреждения",
            AppIcons.Lock to "Не делись адресом, телефоном и паролями",
            AppIcons.Flag to "За нарушения — жалобы, мут или бан",
            AppIcons.Shield to "За свои сообщения отвечает их автор",
        ).forEach { (icon, text) ->
            Row(
                Modifier.fillMaxWidth().fieldSurface(appCorner(14)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingIcon(icon, 30.dp)
                Text(text, color = AppColors.Text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            }
        }
        Row(
            Modifier.fillMaxWidth().liveClick(appCorner(12), sound = if (agree) FeedbackSound.Uncheck else FeedbackSound.Check, pressedScale = 0.98f) { agree = !agree }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val shape = appCorner(7)
            Box(
                Modifier.size(24.dp).clip(shape).background(if (agree) AppColors.Accent else Color.Transparent).border(1.5.dp, if (agree) AppColors.Accent else AppColors.Line, shape),
                contentAlignment = Alignment.Center,
            ) { if (agree) Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(16.dp)) }
            Text("Мне есть 14 лет, правила принимаю", color = AppColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
        AppButton(if (busy) "Подожди…" else "Принять", primary = agree, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Confirm) {
            if (!agree || busy) return@AppButton
            busy = true
            scope.launch {
                app.community.accept().onSuccess { onAccept() }.onFailure { Toast.makeText(app, it.message ?: "Не получилось", Toast.LENGTH_LONG).show() }
                busy = false
            }
        }
    }
}

private sealed interface ChatRow {
    data class Day(val label: String, val key: String) : ChatRow
    data class Chapter(val label: String, val key: String) : ChatRow
    data class Locked(val items: List<Community.Locked>) : ChatRow
    data class Item(val msg: Community.Msg, val first: Boolean) : ChatRow
}

private fun rowKey(r: ChatRow): Any = when (r) {
    is ChatRow.Item -> r.msg.id
    is ChatRow.Day -> r.key
    is ChatRow.Chapter -> r.key
    is ChatRow.Locked -> "locked"
}

@Composable
private fun RoomPage(roomId: String, state: Community.State, askRules: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val community = app.community
    val view by community.room.collectAsStateWithLifecycle()
    val account by app.account.state.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val book = roomId.startsWith("b:")
    val thread = CommunityNav.thread
    val spot = remember(roomId) { if (book) community.spot(roomId) ?: Community.Spot(0, "") else null }
    DisposableEffect(roomId) {
        community.open(roomId, spot?.percent ?: Int.MAX_VALUE)
        onDispose {
            community.close()
            CommunityNav.searching = false
            scope.launch { community.refresh() }
        }
    }
    val incoming by community.incoming.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) { if (incoming > 0) feedback?.play(FeedbackSound.Incoming) }
    var reply by remember { mutableStateOf<Community.Msg?>(null) }
    var editing by remember { mutableStateOf<Community.Msg?>(null) }
    var actions by remember { mutableStateOf<Community.Msg?>(null) }
    var reporting by remember { mutableStateOf<Community.Msg?>(null) }
    var profile by remember { mutableStateOf<String?>(null) }
    val me = account.login ?: state.me.login
    val msgs = if (view.room == roomId) view.msgs else emptyList()
    val known = remember(roomId) { HashSet<Long>() }
    val ready = remember(roomId) { mutableStateOf(false) }
    LaunchedEffect(view.loaded, roomId) {
        if (view.loaded && !ready.value) {
            known.addAll(msgs.map { it.id })
            ready.value = true
        }
    }
    val rows = remember(msgs, view.locked) {
        val out = ArrayList<ChatRow>()
        var prev: Community.Msg? = null
        msgs.forEach { m ->
            val p = prev
            if (p == null || !sameDay(p.at, m.at)) out.add(ChatRow.Day(dayLabel(m.at), "d" + m.id))
            if (book && m.chTitle.isNotBlank() && (p == null || p.chTitle != m.chTitle)) out.add(ChatRow.Chapter(m.chTitle, "c" + m.id))
            val first = p == null || p.login != m.login || m.at - p.at > 5 * 60_000 || !sameDay(p.at, m.at) || m.kind.isNotEmpty() || p.kind.isNotEmpty()
            out.add(ChatRow.Item(m, first))
            prev = m
        }
        if (book && view.locked.isNotEmpty()) out.add(ChatRow.Locked(view.locked))
        out.reversed()
    }
    val list = rememberLazyListState()
    val nearTop by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 4 } ?: false } }
    var loadingOlder by remember { mutableStateOf(false) }
    LaunchedEffect(nearTop, view.more, msgs.size) {
        if (nearTop && view.more && msgs.isNotEmpty() && !loadingOlder) {
            loadingOlder = true
            community.older()
            loadingOlder = false
        }
    }
    val newest = msgs.lastOrNull()?.id
    LaunchedEffect(newest) {
        if (newest != null && list.firstVisibleItemIndex <= 2) list.animateScrollToItem(0)
    }
    var jumpTo by remember { mutableStateOf<Long?>(null) }
    var flash by remember { mutableStateOf<Long?>(null) }
    val backStack = remember(roomId) { mutableStateListOf<Long>() }
    var tries by remember { mutableStateOf(0) }
    LaunchedEffect(jumpTo, rows) {
        val id = jumpTo ?: return@LaunchedEffect
        val i = rows.indexOfFirst { it is ChatRow.Item && it.msg.id == id }
        if (i >= 0) {
            flash = null
            delay(24)
            list.animateScrollToItem(i, -list.layoutInfo.viewportSize.height / 3)
            flash = id
            jumpTo = null
            tries = 0
        } else if (view.more && tries < 12 && msgs.isNotEmpty() && id < msgs.first().id) {
            tries++
            community.older()
        } else {
            jumpTo = null
            tries = 0
            Toast.makeText(app, "Сообщение не найдено — возможно, его удалили", Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        if (book && spot != null) ChapterBar(spot, view.locked.sumOf { it.count })
        AnimatedVisibility(CommunityNav.searching, enter = expandVertically(Motion.tween(240)) + fadeIn(Motion.fade(200)), exit = shrinkVertically(Motion.tween(200)) + fadeOut(Motion.fade(140))) {
            SearchPanel(roomId, spot?.percent ?: Int.MAX_VALUE) { m ->
                CommunityNav.searching = false
                jumpTo = m.id
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                view.off -> Box(Modifier.padding(16.dp)) { OffCard() }
                !view.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(28.dp)) }
                msgs.isEmpty() && view.locked.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Tile(if (book) AppIcons.Book else AppIcons.Chat, 60.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(if (book) "Обсуждение пока пустое" else "Здесь пока тихо", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (book) "Поделись мыслями о прочитанном первым" else "Начни разговор первым", color = AppColors.Muted, fontSize = 13.sp)
                }
                else -> LazyColumn(
                    state = list,
                    reverseLayout = true,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = ::rowKey) { r ->
                        Box(Modifier.animateItem(fadeInSpec = Motion.fade(220), placementSpec = Motion.tween(260), fadeOutSpec = Motion.fade(160))) {
                            when (r) {
                                is ChatRow.Day -> DayChip(r.label)
                                is ChatRow.Chapter -> ChapterChip(r.label)
                                is ChatRow.Locked -> LockedCard(r.items)
                                is ChatRow.Item -> {
                                    val fresh = ready.value && r.msg.id !in known
                                    if (fresh) known.add(r.msg.id)
                                    if (r.msg.kind == "sprint") {
                                        SprintNote(r.msg, state.sprint)
                                    } else {
                                        MessageItem(
                                            m = r.msg,
                                            first = r.first,
                                            mine = r.msg.login == me,
                                            me = me,
                                            fresh = fresh,
                                            flash = flash == r.msg.id,
                                            onAvatar = { profile = r.msg.login },
                                            onQuote = { id ->
                                                if (backStack.lastOrNull() != r.msg.id) backStack.add(r.msg.id)
                                                if (backStack.size > 20) backStack.removeAt(0)
                                                jumpTo = id
                                            },
                                            onLong = { actions = r.msg },
                                            onSwipe = {
                                                if (me != null) {
                                                    editing = null
                                                    reply = r.msg
                                                }
                                            },
                                            onReact = { kind ->
                                                if (me == null) return@MessageItem
                                                scope.launch { community.react(r.msg, kind).onFailure { Toast.makeText(app, it.message, Toast.LENGTH_SHORT).show() } }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                backStack.isNotEmpty(),
                enter = scaleIn(Motion.spring(), initialScale = 0.6f) + fadeIn(Motion.fade(180)),
                exit = scaleOut(Motion.tween(160), targetScale = 0.6f) + fadeOut(Motion.fade(140)),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .liveClick(CircleShape, sound = FeedbackSound.Back, pressedScale = 0.9f) {
                            val id = backStack.removeAt(backStack.lastIndex)
                            jumpTo = id
                        }
                        .shadow(8.dp, CircleShape, ambientColor = AppColors.Shade.copy(alpha = 0.3f), spotColor = AppColors.Shade.copy(alpha = 0.3f))
                        .clip(CircleShape)
                        .background(AppColors.Panel)
                        .border(1.dp, AppColors.Accent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Chevron, "Вернуться к ответу", tint = AppColors.Accent, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = 90f })
                }
            }
            if (view.error != null && view.loaded) {
                Text(
                    "Связь прервалась, переподключаюсь…",
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).fieldSurface(CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Composer(
            roomId,
            state,
            me,
            reply,
            editing,
            onClearReply = { reply = null },
            onClearEdit = { editing = null },
            askRules = askRules,
            chapter = if (book && spot != null) spot.percent to spot.chapter else null,
            thread = if (book) thread else null,
        )
    }
    actions?.let { m ->
        ActionsSheet(
            m, me, state.me.admin,
            onDismiss = { actions = null },
            onReply = { editing = null; reply = m },
            onEdit = { reply = null; editing = m },
            onProfile = { profile = m.login },
            onReport = { reporting = m },
        )
    }
    reporting?.let { m -> ReportSheet(m) { reporting = null } }
    profile?.let { l -> ProfileSheet(l, canMention = me != null && l != me) { profile = null } }
}

@Composable
private fun ChapterBar(spot: Community.Spot, hidden: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).fieldSurface(appCorner(16)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(AppIcons.Book, 28.dp)
            Text(
                buildAnnotatedString {
                    append("Ты на ")
                    withStyle(SpanStyle(color = AppColors.Accent, fontWeight = FontWeight.Bold)) { append("${spot.percent}%") }
                    if (spot.chapter.isNotBlank()) append(" · " + spot.chapter)
                },
                color = AppColors.Text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier.clip(appCorner(8)).border(1.dp, AppColors.Accent.copy(alpha = 0.5f), appCorner(8)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(AppIcons.EyeOff, null, tint = AppColors.Accent, modifier = Modifier.size(11.dp))
                Text(if (hidden > 0) "скрыто $hidden" else "без спойлеров", color = AppColors.Accent, fontSize = 10.sp, maxLines = 1)
            }
        }
        val progress by androidx.compose.animation.core.animateFloatAsState(spot.percent / 100f, Motion.tween(600), label = "spot")
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AppColors.Line)) {
            Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(AppColors.AccentDark, AppColors.Accent))))
        }
    }
}

@Composable
private fun ChapterChip(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fieldSurface(appCorner(10)).padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(AppIcons.Book, null, tint = AppColors.Accent, modifier = Modifier.size(12.dp))
            Text(label, color = AppColors.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
        }
        Box(Modifier.weight(1f).height(1.dp).background(AppColors.Line))
    }
}

@Composable
private fun LockedCard(items: List<Community.Locked>) {
    val total = items.sumOf { it.count }
    val titles = items.map { it.title }.filter { it.isNotBlank() }.distinct()
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(appCorner(18)).background(AppColors.Panel).border(1.dp, AppColors.PanelBorder, appCorner(18)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val glow = androidx.compose.animation.core.rememberInfiniteTransition(label = "lock")
        val pulse by glow.animateFloat(0.9f, 1.06f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600), androidx.compose.animation.core.RepeatMode.Reverse), label = "lock-pulse")
        Box(Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }) { Tile(AppIcons.Lock, 44.dp) }
        Text("Дальше по книге: ${chatCount(total)}", color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Откроются сами, когда дочитаешь до этого места", color = AppColors.Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (titles.isNotEmpty()) {
            Text(titles.take(3).joinToString(" · ") + if (titles.size > 3) " …" else "", color = AppColors.Faint, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private fun chatCount(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> "сообщение"
        m10 in 2..4 && m100 !in 12..14 -> "сообщения"
        else -> "сообщений"
    }
    return "$n $w"
}

@Composable
private fun SearchPanel(roomId: String, upto: Int, onPick: (Community.Msg) -> Unit) {
    val app = LocalContext.current.applicationContext as App
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Community.Msg>?>(null) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) {
            found = null
            return@LaunchedEffect
        }
        delay(320)
        app.community.search(roomId, q.trim(), upto).onSuccess { found = it }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InputField(q, { q = it.take(60) }, "Поиск по комнате", icon = AppIcons.Search)
        val list = found
        if (list != null) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 280.dp).fieldSurface(appCorner(16)).verticalScroll(rememberScrollState()).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (list.isEmpty()) Text("Ничего не нашлось", color = AppColors.Faint, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                list.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().liveClick(appCorner(12), sound = FeedbackSound.Go, pressedScale = 0.98f) { onPick(m) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LetterAvatar(m.login, 28.dp)
                        Column(Modifier.weight(1f)) {
                            Text(m.login, color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(m.text.ifBlank { m.quote?.text ?: m.book?.title.orEmpty() }, color = AppColors.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(chatAgo(m.at), color = AppColors.Faint, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CommunitySearchButton() {
    if (CommunityNav.room == null || CommunityNav.mod || CommunityNav.sprint || CommunityNav.room == "ideas") return
    val on = CommunityNav.searching
    Box(
        Modifier.size(44.dp).liveClick(CircleShape, sound = FeedbackSound.Search) { CommunityNav.searching = !CommunityNav.searching }.fieldSurface(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.Crossfade(on, animationSpec = Motion.fade(180), label = "search-btn") { open ->
            Icon(if (open) AppIcons.Close else AppIcons.Search, "Поиск", tint = if (open) AppColors.Accent else AppColors.Text, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = AppColors.Muted, fontSize = 11.sp, modifier = Modifier.fieldSurface(appCorner(10)).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun MessageItem(
    m: Community.Msg,
    first: Boolean,
    mine: Boolean,
    me: String?,
    fresh: Boolean,
    flash: Boolean,
    onAvatar: () -> Unit,
    onLong: () -> Unit,
    onSwipe: () -> Unit,
    onReact: (String) -> Unit,
    onQuote: (Long) -> Unit = {},
) {
    val corner = (16 * AppStyle.corners).dp
    val small = (5 * AppStyle.corners).dp
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (!mine && first) small else corner,
        topEnd = if (mine && first) small else corner,
        bottomStart = corner,
        bottomEnd = corner,
    )
    val mention = me != null && !mine && (m.reply?.login == me || m.text.contains("@$me"))
    val view = androidx.compose.ui.platform.LocalView.current
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val limit = with(density) { 76.dp.toPx() }
    val trigger = with(density) { 56.dp.toPx() }
    val drag = remember { androidx.compose.animation.core.Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val enter = remember(m.id) { androidx.compose.animation.core.Animatable(if (fresh && Motion.enabled) 0f else 1f) }
    LaunchedEffect(m.id) { if (enter.value < 1f) enter.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 380f)) }
    val glow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash) {
            glow.snapTo(1f)
            glow.animateTo(0f, androidx.compose.animation.core.tween(1600))
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = if (first) 8.dp else 2.dp)
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * 28.dp.toPx()
                val sc = 0.94f + 0.06f * e
                scaleX = sc
                scaleY = sc
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
            }
            .pointerInput(m.id, me) {
                if (me == null) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val hit = drag.value <= -trigger
                        scope.launch { drag.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f)) }
                        if (hit) onSwipe()
                        armed = false
                    },
                    onDragCancel = {
                        scope.launch { drag.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f)) }
                        armed = false
                    },
                ) { change, amount ->
                    change.consume()
                    val next = (drag.value + amount * if (drag.value < -trigger) 0.35f else 1f).coerceIn(-limit, 0f)
                    scope.launch { drag.snapTo(next) }
                    val nowArmed = next <= -trigger
                    if (nowArmed && !armed) {
                        feedback?.play(FeedbackSound.Pick, view)
                    }
                    armed = nowArmed
                }
            },
    ) {
        val pull = (-drag.value / trigger).coerceIn(0f, 1f)
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(34.dp).graphicsLayer {
                alpha = pull
                val k = 0.5f + 0.5f * pull + if (armed) 0.12f else 0f
                scaleX = k
                scaleY = k
                rotationZ = (1f - pull) * -40f
            }.clip(CircleShape).background(if (armed) AppColors.Accent else AppColors.AccentTintStrong).border(1.dp, AppColors.Accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Reply, null, tint = if (armed) AppColors.OnAccent else AppColors.Accent, modifier = Modifier.size(17.dp)) }
        Row(
            Modifier.fillMaxWidth().graphicsLayer { translationX = drag.value },
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!mine) {
                if (first) Box(Modifier.liveClick(appCorner(10), sound = FeedbackSound.Open, onClick = onAvatar)) { LetterAvatar(m.login, 34.dp) } else Spacer(Modifier.width(34.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
                val edge = when {
                    mine -> AppColors.Accent.copy(alpha = 0.45f)
                    mention -> AppColors.Accent
                    else -> AppColors.PanelBorder
                }
                Column(
                    Modifier
                        .liveClick(shape, sound = FeedbackSound.Sheet, pressedScale = 0.98f, onLongClick = onLong, onClick = {})
                        .clip(shape)
                        .background(if (mine) AppColors.AccentTintStrong else AppColors.Panel)
                        .drawBehind { if (glow.value > 0f) drawRect(AppColors.Accent.copy(alpha = 0.28f * glow.value)) }
                        .border(1.dp, edge, shape)
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (!mine && first) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(m.login, color = if (m.admin) AppColors.Accent else AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (m.admin) {
                                Row(
                                    Modifier.clip(appCorner(6)).background(AppColors.Accent).padding(horizontal = 5.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(AppIcons.Shield, null, tint = AppColors.OnAccent, modifier = Modifier.size(10.dp))
                                    Text("автор", color = AppColors.OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    m.reply?.let { r ->
                        Row(Modifier.liveClick(appCorner(8), sound = FeedbackSound.Nav, pressedScale = 0.97f, onLongClick = onLong) { onQuote(r.id) }.clip(appCorner(8)).background(AppColors.Background.copy(alpha = 0.5f)).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(AppColors.Accent))
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(r.login, color = AppColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(r.text, color = AppColors.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (m.text.isNotBlank()) Text(mentionText(m.text, me), color = AppColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
                    m.quote?.let { QuoteCard(it) }
                    m.book?.let { BookCard(it) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (m.reading.isNotBlank() && !mine && first) {
                            Icon(AppIcons.Book, null, tint = AppColors.Faint, modifier = Modifier.size(11.dp))
                            Text(m.reading, color = AppColors.Faint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                        }
                        if (m.edited) Text("изм.", color = AppColors.Faint, fontSize = 10.sp)
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.at)), color = AppColors.Faint, fontSize = 10.sp)
                    }
                }
                if (m.reacts.isNotEmpty()) ReactRow(m, me, onReact)
            }
        }
    }
}

@Composable
private fun SprintNote(m: Community.Msg, sprint: Community.Sprint?) {
    val live = sprint != null && !sprint.done && m.at >= sprint.start - 120_000
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(appCorner(18)).background(AppColors.AccentTintStrong).border(1.dp, AppColors.Accent.copy(alpha = 0.5f), appCorner(18))
            .liveClick(appCorner(18), sound = FeedbackSound.Go, pressedScale = 0.98f, enabled = live) { CommunityNav.sprint = true }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tile(AppIcons.Timer, 38.dp, filled = live)
        Column(Modifier.weight(1f)) {
            Text(m.login, color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(m.text, color = AppColors.Text, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (live) Text("Вступить", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun mentionText(text: String, me: String?): AnnotatedString = buildAnnotatedString {
    val re = Regex("@([a-z0-9_]{3,24})")
    var at = 0
    re.findAll(text).forEach { m ->
        append(text.substring(at, m.range.first))
        withStyle(SpanStyle(color = AppColors.Accent, fontWeight = if (m.groupValues[1] == me) FontWeight.Bold else FontWeight.SemiBold)) { append(m.value) }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactRow(m: Community.Msg, me: String?, onReact: (String) -> Unit) {
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        reactions.forEach { (kind, look) ->
            val who = m.reacts[kind].orEmpty()
            if (who.isEmpty()) return@forEach
            val mineR = me != null && me in who
            val pop = remember(kind) { androidx.compose.animation.core.Animatable(1f) }
            var seen by remember(kind) { mutableStateOf(who.size) }
            var spark by remember(kind) { mutableStateOf(0) }
            LaunchedEffect(who.size) {
                if (who.size != seen) {
                    if (who.size > seen) spark++
                    seen = who.size
                    pop.snapTo(1.3f)
                    pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 500f))
                }
            }
            Row(
                Modifier
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .sparkBurst(spark, listOf(look.second, AppColors.Accent), count = 8, reach = 1.6f)
                    .liveClick(appCorner(10), sound = FeedbackSound.React, pressedScale = 0.9f) { onReact(kind) }
                    .clip(appCorner(10))
                    .background(if (mineR) AppColors.AccentTintStrong else AppColors.Panel)
                    .border(1.dp, if (mineR) AppColors.Accent else AppColors.PanelBorder, appCorner(10))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(look.first, null, tint = look.second, modifier = Modifier.size(13.dp))
                Text("${who.size}", color = AppColors.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun QuoteCard(q: Community.Quote) {
    Column(
        Modifier.clip(appCorner(12)).background(AppColors.Background.copy(alpha = 0.45f)).border(1.dp, AppColors.Line, appCorner(12)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(AppIcons.Quote, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
        Text(q.text, color = AppColors.Text, fontSize = 14.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Line))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GeneratedCover(q.book, q.author, Modifier.width(20.dp).height(28.dp).clip(appCorner(3)))
            Column {
                Text(q.book, color = AppColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(q.author, q.chapter).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = AppColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BookCard(b: Community.BookRef) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val own = books.firstOrNull { it.title.equals(b.title, ignoreCase = true) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier.clip(appCorner(12)).background(AppColors.Background.copy(alpha = 0.45f)).border(1.dp, AppColors.Line, appCorner(12)).padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GeneratedCover(b.title, b.author, Modifier.width(46.dp).height(66.dp).clip(appCorner(5)))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 180.dp)) {
            Text(b.title, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotBlank()) Text(b.author, color = AppColors.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val label = when {
                own == null -> "Копировать название"
                own.status == ReadStatus.Planned -> "В планах"
                own.status == ReadStatus.Finished -> "Прочитано"
                own.status == ReadStatus.Reading -> "Читаю"
                else -> "В планы"
            }
            Row(
                Modifier
                    .liveClick(appCorner(9), sound = if (own == null) FeedbackSound.Copy else FeedbackSound.Bookmark, pressedScale = 0.95f) {
                        if (own == null) {
                            clipboard.setText(AnnotatedString(listOf(b.title, b.author).filter { it.isNotBlank() }.joinToString(" — ")))
                            Toast.makeText(app, "Название скопировано — найди книгу и добавь её", Toast.LENGTH_SHORT).show()
                        } else if (own.status == ReadStatus.New) {
                            scope.launch { app.library.setStatus(own.id, ReadStatus.Planned) }
                            Toast.makeText(app, "Книга в планах", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .clip(appCorner(9))
                    .background(AppColors.Panel)
                    .border(1.dp, AppColors.PanelBorder, appCorner(9))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(if (own == null) AppIcons.Copy else AppIcons.Bookmark, null, tint = AppColors.Accent, modifier = Modifier.size(13.dp))
                Text(label, color = AppColors.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Composer(
    roomId: String,
    state: Community.State,
    me: String?,
    reply: Community.Msg?,
    editing: Community.Msg?,
    onClearReply: () -> Unit,
    onClearEdit: () -> Unit,
    askRules: () -> Unit,
    chapter: Pair<Int, String>? = null,
    thread: Pair<String, String>? = null,
) {
    val app = LocalContext.current.applicationContext as App
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    var text by remember(roomId) { mutableStateOf(CommunityNav.draft) }
    var sending by remember { mutableStateOf(false) }
    var sentAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var attach by remember { mutableStateOf(false) }
    var burst by remember { mutableStateOf(0) }
    LaunchedEffect(sentAt) {
        while (System.currentTimeMillis() - sentAt < state.slow * 1000L + 1000) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    LaunchedEffect(CommunityNav.draft) {
        if (CommunityNav.draft.isNotEmpty()) {
            text = CommunityNav.draft
            CommunityNav.draft = ""
        }
    }
    LaunchedEffect(editing?.id) { editing?.let { text = it.text } }
    val wait = if (state.me.admin || editing != null) 0 else ((sentAt + state.slow * 1000L - now + 999) / 1000).toInt().coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, AppColors.Background.copy(alpha = 0.9f)))).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
    ) {
        when {
            me == null -> {
                GuestCard()
                return@Column
            }
            state.me.banned -> {
                Banner(AppIcons.Ban, "Ты не можешь писать в сообществе", state.me.why.ifBlank { "Нарушение правил" })
                return@Column
            }
            state.me.muted > System.currentTimeMillis() -> {
                val left = state.me.muted - System.currentTimeMillis()
                val h = left / 3_600_000
                val m = (left / 60_000) % 60
                Banner(AppIcons.Mute, "Писать можно будет через " + (if (h > 0) "$h ч $m мин" else "$m мин"), listOf("Читать можно", state.me.why).filter { it.isNotBlank() }.joinToString(" · "))
                return@Column
            }
        }
        val target = editing ?: reply
        val lastTarget = remember { arrayOf<Community.Msg?>(null) }
        val lastEdit = remember { booleanArrayOf(false) }
        if (target != null) {
            lastTarget[0] = target
            lastEdit[0] = editing != null
        }
        AnimatedVisibility(
            target != null,
            enter = expandVertically(Motion.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing), expandFrom = Alignment.Bottom) + fadeIn(Motion.fade(200)),
            exit = shrinkVertically(Motion.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing), shrinkTowards = Alignment.Bottom) + fadeOut(Motion.fade(160)),
        ) {
            val r = lastTarget[0]
            val isEdit = lastEdit[0]
            if (r != null) Row(
                Modifier.padding(bottom = 7.dp).fillMaxWidth().fieldSurface(appCorner(14)).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(if (isEdit) AppIcons.Pencil else AppIcons.Reply, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isEdit) "Изменить сообщение" else r.login, color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(r.text.ifBlank { r.quote?.text ?: r.book?.title.orEmpty() }, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(32.dp).liveClick(CircleShape, sound = FeedbackSound.Close) {
                    if (editing != null) {
                        onClearEdit()
                        text = ""
                    } else onClearReply()
                }, contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, "Отменить", tint = AppColors.Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.liveClick(appCorner(13), sound = FeedbackSound.More) { if (!state.me.accepted) askRules() else attach = true }) { Tile(AppIcons.Plus, 44.dp) }
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(800) },
                textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(AppColors.Accent),
                maxLines = 5,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(appCorner(14))
                    .background(AppColors.Field)
                    .border(1.dp, if (editing != null) AppColors.Accent.copy(alpha = 0.6f) else AppColors.Line, appCorner(14))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(
                            when {
                                reply != null -> "Ответ для ${reply.login}…"
                                chapter != null -> "Твои мысли о прочитанном…"
                                else -> "Сообщение…"
                            },
                            color = AppColors.Faint,
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                        inner()
                    }
                },
            )
            val ready = text.isNotBlank() && !sending && wait == 0
            Box(contentAlignment = Alignment.Center, modifier = Modifier.sparkBurst(burst, listOf(AppColors.Accent, AppColors.AccentLight, AppColors.Text), count = 10, reach = 1.4f)) {
                Box(
                    Modifier.liveClick(appCorner(13), sound = FeedbackSound.Tap, enabled = ready, pressedScale = 0.88f) {
                        if (!state.me.accepted) {
                            askRules()
                            return@liveClick
                        }
                        val body = text.trim()
                        sending = true
                        scope.launch {
                            val result = if (editing != null) app.community.edit(editing, body).map { } else app.community.send(roomId, body, reply?.id, chapter = chapter, thread = thread).map { }
                            result.onSuccess {
                                feedback?.play(if (editing != null) FeedbackSound.Check else FeedbackSound.Send)
                                if (editing == null) sentAt = System.currentTimeMillis()
                                text = ""
                                burst++
                                onClearReply()
                                onClearEdit()
                            }.onFailure {
                                feedback?.play(FeedbackSound.Deny)
                                Toast.makeText(app, it.message ?: "Не отправилось", Toast.LENGTH_LONG).show()
                            }
                            sending = false
                        }
                    },
                ) {
                    androidx.compose.animation.Crossfade(ready to (editing != null), animationSpec = Motion.fade(200), label = "send-ready") { (on, edit) ->
                        val icon = if (edit) AppIcons.Check else AppIcons.Send
                        if (on) Tile(icon, 44.dp, filled = true) else Tile(icon, 44.dp, tint = AppColors.Faint)
                    }
                }
                if (sending) KitSpinner(Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(wait > 0, enter = fadeIn(Motion.fade(200)) + expandVertically(Motion.tween(220), expandFrom = Alignment.Top), exit = fadeOut(Motion.fade(160)) + shrinkVertically(Motion.tween(220), shrinkTowards = Alignment.Top)) {
            Row(Modifier.padding(top = 7.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Clock, null, tint = AppColors.Muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("Медленный режим: ещё $wait с", color = AppColors.Muted, fontSize = 11.sp)
            }
        }
    }
    if (attach) AttachSheet(roomId, chapter, thread) { attach = false }
}

@Composable
private fun Banner(icon: ImageVector, title: String, sub: String) {
    Row(
        Modifier.fillMaxWidth().clip(appCorner(16)).background(AppColors.Panel).border(1.dp, AppColors.Bad.copy(alpha = 0.55f), appCorner(16)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tile(icon, 36.dp, tint = AppColors.Bad)
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (sub.isNotBlank()) Text(sub, color = AppColors.Muted, fontSize = 12.sp)
        }
    }
}

private class ActionItem(val icon: ImageVector, val title: String, val color: Color?, val sound: FeedbackSound, val onClick: () -> Unit)

@Composable
private fun ActionGroup(items: List<ActionItem>) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(horizontal = 6.dp, vertical = 4.dp)) {
        items.forEachIndexed { i, it ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(AppColors.PanelBorder))
            val tint = it.color ?: AppColors.Text
            Row(
                Modifier.fillMaxWidth().liveClick(appCorner(14), sound = it.sound, pressedScale = 0.98f, onClick = it.onClick).padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tile(it.icon, 32.dp, tint = it.color ?: AppColors.Accent)
                Text(it.title, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionsSheet(m: Community.Msg, me: String?, admin: Boolean, onDismiss: () -> Unit, onReply: () -> Unit, onEdit: () -> Unit, onProfile: () -> Unit, onReport: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val clipboard = LocalClipboardManager.current
    AppSheet(onDismiss = onDismiss) {
        if (me != null) {
            Row(Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                reactions.forEach { (kind, look) ->
                    val on = me in m.reacts[kind].orEmpty()
                    Box(
                        Modifier
                            .size(48.dp)
                            .liveClick(appCorner(14), sound = FeedbackSound.React, pressedScale = 0.8f) {
                                scope.launch { app.community.react(m, kind) }
                                onDismiss()
                            }
                            .clip(appCorner(14))
                            .background(if (on) AppColors.AccentTintStrong else AppColors.Background.copy(alpha = 0.4f))
                            .border(1.dp, if (on) AppColors.Accent else AppColors.Line, appCorner(14)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(look.first, null, tint = look.second, modifier = Modifier.size(23.dp)) }
                }
            }
        }
        val fresh = me != null && m.login == me && System.currentTimeMillis() - m.at < 10 * 60_000L
        if (fresh) {
            ActionGroup(buildList {
                if (m.text.isNotBlank() || (m.quote == null && m.book == null)) add(ActionItem(AppIcons.Pencil, "Изменить", null, FeedbackSound.Edit) { onEdit(); onDismiss() })
                add(ActionItem(AppIcons.Trash, "Удалить у всех", AppColors.Bad, FeedbackSound.Trash) {
                    scope.launch { app.community.unsend(m).onFailure { Toast.makeText(app, it.message, Toast.LENGTH_SHORT).show() } }
                    onDismiss()
                })
            })
        }
        val main = buildList {
            if (me != null) add(ActionItem(AppIcons.Reply, "Ответить", null, FeedbackSound.Edit) { onReply(); onDismiss() })
            if (m.text.isNotBlank()) add(ActionItem(AppIcons.Copy, "Копировать", null, FeedbackSound.Copy) { clipboard.setText(AnnotatedString(m.text)); onDismiss() })
            add(ActionItem(AppIcons.Note, "В заметки", null, FeedbackSound.Note) {
                scope.launch {
                    val where = saveToNotes(app, m)
                    Toast.makeText(app, where, Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            })
            add(ActionItem(AppIcons.Person, "Профиль ${m.login}", null, FeedbackSound.Open) { onProfile(); onDismiss() })
            if (me != null && m.login != me) add(ActionItem(AppIcons.Flag, "Пожаловаться", AppColors.Bad, FeedbackSound.Report) { onReport(); onDismiss() })
        }
        ActionGroup(main)
        if (admin) {
            ActionGroup(buildList {
                add(ActionItem(AppIcons.Trash, "Удалить", AppColors.Bad, FeedbackSound.Trash) {
                    scope.launch { app.community.admin("delete") { put("room", m.room); put("id", m.id) } }
                    onDismiss()
                })
                if (m.login != me) {
                    add(ActionItem(AppIcons.Mute, "Мут на 24 ч", AppColors.Bad, FeedbackSound.Off) {
                        scope.launch { app.community.admin("mute") { put("login", m.login); put("hours", 24); put("room", m.room); put("id", m.id); put("reason", "нарушение правил") } }
                        onDismiss()
                    })
                    add(ActionItem(AppIcons.Ban, "Забанить", AppColors.Bad, FeedbackSound.Wipe) {
                        scope.launch { app.community.admin("ban") { put("login", m.login); put("room", m.room); put("id", m.id) } }
                        onDismiss()
                    })
                }
            })
        }
    }
}

@Composable
private fun ReportSheet(m: Community.Msg, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    AppSheet(onDismiss = onDismiss, title = "Пожаловаться") {
        Text(m.text.ifBlank { m.quote?.text ?: m.book?.title.orEmpty() }, color = AppColors.Muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().fieldSurface(appCorner(14)).padding(12.dp))
        ActionGroup(listOf("spam" to (AppIcons.Ban to "Спам или реклама"), "rude" to (AppIcons.Alert to "Грубость"), "spoiler" to (AppIcons.EyeOff to "Спойлер"), "other" to (AppIcons.Flag to "Другое")).map { (id, look) ->
            ActionItem(look.first, look.second, null, FeedbackSound.Report) {
                scope.launch {
                    app.community.report(m, id).onSuccess { Toast.makeText(app, "Жалоба отправлена", Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(app, it.message, Toast.LENGTH_SHORT).show() }
                }
                onDismiss()
            }
        })
    }
}

@Composable
private fun ProfileSheet(login: String, canMention: Boolean, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    var p by remember { mutableStateOf<Community.Profile?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(login) { app.community.profile(login).onSuccess { p = it }.onFailure { failed = it.message } }
    AppSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LetterAvatar(login, 60.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(login, color = AppColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p?.admin == true) Icon(AppIcons.Shield, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                }
                p?.since?.takeIf { it > 0 }?.let { Text("в сообществе с " + SimpleDateFormat("LLLL yyyy", Locale.forLanguageTag("ru")).format(Date(it)), color = AppColors.Faint, fontSize = 12.sp) }
            }
        }
        val profile = p
        when {
            failed != null -> HintNote(failed!!, AppIcons.Alert)
            profile == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(24.dp)) }
            else -> Column(Modifier.animateContentSize(Motion.tween(260)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (profile.reading.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().clip(appCorner(16)).background(AppColors.AccentTintStrong).border(1.dp, AppColors.Accent.copy(alpha = 0.4f), appCorner(16)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GeneratedCover(profile.reading, "", Modifier.width(34.dp).height(48.dp).clip(appCorner(4)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Сейчас читает", color = AppColors.Muted, fontSize = 11.sp)
                            Text(profile.reading, color = AppColors.Text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AppColors.Line)) {
                                Box(Modifier.fillMaxWidth(profile.progress / 100f).height(4.dp).clip(CircleShape).background(AppColors.Accent))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("${profile.books}" to "прочитано", "${profile.streak}" to "дней подряд", "${profile.hours} ч" to "чтения").forEach { (v, t) ->
                        Column(Modifier.weight(1f).fieldSurface(appCorner(14)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, color = AppColors.Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(t, color = AppColors.Muted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                val medals = profile.achv.mapNotNull { Achievements.byId(it) }
                if (medals.isNotEmpty()) {
                    TitlePill("Достижения", icon = AppIcons.Trophy)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        medals.forEach { a ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                                AchievementMedal(a, earned = true, fraction = 1f, size = 52.dp)
                                Text(a.title, color = AppColors.Muted, fontSize = 10.sp, maxLines = 2, lineHeight = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        if (canMention) AppButton("Упомянуть", primary = false, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Mention) {
            CommunityNav.draft = "@$login "
            onDismiss()
        }
    }
}

@Composable
private fun AttachSheet(roomId: String, chapter: Pair<Int, String>?, thread: Pair<String, String>?, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val order = remember(books) { books.sortedByDescending { it.lastReadAt ?: it.addedAt }.mapIndexed { i, b -> b.id to i }.toMap() }
    var chosen by remember { mutableStateOf(books.maxByOrNull { it.lastReadAt ?: it.addedAt }?.id) }
    AppSheet(onDismiss = onDismiss, title = "Книга в чат") {
        val close = sheetClose(onDismiss)
        BookPickList(
            "Библиотека",
            books,
            listOfNotNull(chosen),
            onToggle = { b -> chosen = b.id },
            sortKey = { order[it.id] ?: Int.MAX_VALUE },
            note = { b -> b.author.takeIf { it.isNotBlank() } },
            single = true,
        )
        HintNote("Уйдут только название, автор и обложка — сам файл книги не передаётся", AppIcons.Book)
        AppButton("Отправить", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true) {
            val b = books.firstOrNull { it.id == chosen } ?: return@AppButton
            scope.launch {
                app.community.send(roomId, "", null, book = Community.BookRef(b.title, b.author, (b.title.hashCode() and 0x7fffffff) % 12), chapter = chapter, thread = thread)
                    .onSuccess { feedback?.play(FeedbackSound.Send) }
                    .onFailure { Toast.makeText(app, it.message, Toast.LENGTH_LONG).show() }
            }
            close()
        }
    }
}

@Composable
private fun ModPage() {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    var mod by remember { mutableStateOf<Community.Mod?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pinEdit by remember { mutableStateOf(false) }
    var wordsEdit by remember { mutableStateOf(false) }
    fun run(op: String, fill: org.json.JSONObject.() -> Unit = {}) {
        scope.launch { app.community.admin(op, fill).onSuccess { mod = it; app.community.refresh() }.onFailure { Toast.makeText(app, it.message, Toast.LENGTH_LONG).show() } }
    }
    LaunchedEffect(Unit) { app.community.moderation().onSuccess { mod = it }.onFailure { error = it.message } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val m = mod
        if (m == null) {
            if (error != null) HintNote(error!!, AppIcons.Alert) else Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(28.dp)) }
            return@Column
        }
        SettingsGroup(null) {
            SettingSwitch("Сообщество включено", m.on, hint = "Выключение сразу закрывает чат у всех", icon = AppIcons.Power) { v -> run("power") { put("on", v) } }
            val slows = listOf(0, 5, 10, 30, 60)
            SettingSegmented("Медленный режим", listOf("выкл", "5 с", "10 с", "30 с", "1 м"), slows.indexOf(m.slow).coerceAtLeast(0), help = "Как часто один человек может писать в одну комнату") { i -> run("slow") { put("slow", slows[i]) } }
        }
        SettingsNavRow(AppIcons.Pin, "Закреп", m.pin.ifBlank { "Нет закреплённого сообщения" }) { pinEdit = true }
        SettingsNavRow(AppIcons.Ban, "Свои запрещённые слова", if (m.words.isEmpty()) "Только встроенный фильтр" else "${m.words.size} слов") { wordsEdit = true }
        TitlePill("Жалобы", icon = AppIcons.Flag)
        if (m.reports.isEmpty()) HintNote("Жалоб нет", AppIcons.Check)
        m.reports.forEach { r ->
            Column(
                Modifier.fillMaxWidth().scrollReveal().fieldSurface(appCorner(18)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LetterAvatar(r.msg.login, 30.dp)
                    Column(Modifier.weight(1f)) {
                        Text(r.msg.login, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Жалоб: ${r.count} · " + r.reasons.entries.joinToString(", ") { it.key } + if (r.hidden) " · скрыто" else "",
                            color = AppColors.Bad,
                            fontSize = 11.sp,
                        )
                    }
                }
                Text(r.msg.text.ifBlank { r.msg.quote?.text ?: r.msg.book?.title.orEmpty() }, color = AppColors.Muted, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("Оставить", AppIcons.Check) { run("keep") { put("id", r.msg.id) } },
                        Triple("Удалить", AppIcons.Trash) { run("delete") { put("room", r.msg.room); put("id", r.msg.id) } },
                        Triple("Мут", AppIcons.Mute) { run("mute") { put("login", r.msg.login); put("hours", 24); put("id", r.msg.id); put("room", r.msg.room); put("reason", "нарушение правил") } },
                        Triple("Бан", AppIcons.Ban) { run("ban") { put("login", r.msg.login); put("id", r.msg.id); put("room", r.msg.room) } },
                    ).forEach { (label, icon, action) ->
                        Column(
                            Modifier.weight(1f).liveClick(appCorner(12), sound = FeedbackSound.Tap, pressedScale = 0.95f, onClick = action).fieldSurface(appCorner(12)).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(icon, null, tint = if (label == "Оставить") AppColors.Accent else AppColors.Bad, modifier = Modifier.size(17.dp))
                            Text(label, color = AppColors.Text, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (m.mutes.isNotEmpty() || m.bans.isNotEmpty()) TitlePill("Ограничены", icon = AppIcons.Mute)
        m.mutes.forEach { (login, until, why) ->
            LimitRow(login, "Мут до " + SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("ru")).format(Date(until)) + (if (why.isNotBlank()) " · $why" else ""), "Снять") { run("unmute") { put("login", login) } }
        }
        m.bans.forEach { (login, why) -> LimitRow(login, "Бан · $why", "Разбанить") { run("unban") { put("login", login) } } }
    }
    val m = mod
    if (pinEdit && m != null) TextInputSheet(title = "Закреп", initial = m.pin, hint = "Текст объявления", onDismiss = { pinEdit = false }, icon = AppIcons.Pin, maxLength = 300, multiline = true) { t -> run("pin") { put("text", t) }; pinEdit = false }
    if (wordsEdit && m != null) TextInputSheet(title = "Запрещённые слова", initial = m.words.joinToString(", "), hint = "через запятую", onDismiss = { wordsEdit = false }, icon = AppIcons.Ban, maxLength = 4000, multiline = true) { t ->
        run("words") { put("words", org.json.JSONArray(t.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() })) }
        wordsEdit = false
    }
}

@Composable
private fun LimitRow(login: String, sub: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().fieldSurface(appCorner(16)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LetterAvatar(login, 30.dp)
        Column(Modifier.weight(1f)) {
            Text(login, color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = AppColors.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(action, color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.liveClick(appCorner(10), sound = FeedbackSound.On, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable
fun CommunityOnline() {
    val app = LocalContext.current.applicationContext as App
    val state by app.community.state.collectAsStateWithLifecycle()
    AnimatedVisibility(state.loaded && state.on, enter = fadeIn(Motion.fade(300)), exit = fadeOut(Motion.fade(200))) {
        Row(
            Modifier.height(44.dp).fieldSurface(CircleShape).padding(start = 13.dp, end = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.size(8.dp).background(AppColors.Good, CircleShape))
            Text("${state.online}", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1)
        }
    }
}

object ChatShare {
    var quote by mutableStateOf<Community.Quote?>(null)
}

@Composable
fun ChatShareHost() {
    val q = ChatShare.quote ?: return
    val app = LocalContext.current.applicationContext as App
    val community = app.community
    val state by community.state.collectAsStateWithLifecycle()
    val account by app.account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    var room by remember { mutableStateOf("general") }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { community.refresh() }
    val close = { ChatShare.quote = null }
    if (rules) RulesSheet(onAccept = { rules = false }) { rules = false }
    AppSheet(onDismiss = close, title = "Цитата в чат") {
        QuoteCard(q)
        when {
            account.login == null -> HintNote("Чтобы писать в сообществе, войди в аккаунт в настройках", AppIcons.Lock)
            !state.loaded -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(24.dp)) }
            !state.on -> HintNote("Сообщество временно выключено", AppIcons.Power)
            else -> {
                TitlePill("Куда отправить", icon = AppIcons.Chat)
                state.rooms.forEach { r ->
                    val on = r.id == room
                    Row(
                        Modifier.fillMaxWidth().liveClick(appCorner(14), sound = FeedbackSound.Pick, pressedScale = 0.98f) { room = r.id }
                            .clip(appCorner(14)).background(if (on) AppColors.AccentTintStrong else AppColors.Field)
                            .border(1.dp, if (on) AppColors.Accent else AppColors.Line, appCorner(14)).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Tile(roomIcon(r.icon), 30.dp)
                        Text(r.title, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (on) Icon(AppIcons.Check, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                    }
                }
                InputField(comment, { comment = it.take(300) }, "Комментарий, если хочешь")
                AppButton(if (busy) "Подожди…" else "Отправить", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Tap) {
                    if (busy) return@AppButton
                    if (!state.me.accepted) {
                        rules = true
                        return@AppButton
                    }
                    busy = true
                    scope.launch {
                        community.send(room, comment.trim(), null, quote = q).onSuccess {
                            feedback?.play(FeedbackSound.Send)
                            Toast.makeText(app, "Цитата в сообществе", Toast.LENGTH_SHORT).show()
                            close()
                        }.onFailure {
                            feedback?.play(FeedbackSound.Deny)
                            Toast.makeText(app, it.message ?: "Не отправилось", Toast.LENGTH_LONG).show()
                        }
                        busy = false
                    }
                }
            }
        }
    }
}

private suspend fun saveToNotes(app: App, m: Community.Msg): String {
    val books = app.library.books.value
    val title = m.quote?.book ?: m.book?.title ?: CommunityNav.thread?.first.takeIf { CommunityNav.room?.startsWith("b:") == true }
    val own = title?.let { t -> books.firstOrNull { it.title.equals(t, ignoreCase = true) } }
    val text = m.quote?.text ?: m.text.ifBlank { m.book?.let { b -> listOf(b.title, b.author).filter { it.isNotBlank() }.joinToString(" — ") }.orEmpty() }
    val note = listOf("Из сообщества · ${m.login}", if (m.quote != null) m.text else "").filter { it.isNotBlank() }.joinToString("\n")
    return if (own != null) {
        val locator = own.locatorJson?.let { runCatching { org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(it)) }.getOrNull() }
            ?: org.readium.r2.shared.publication.Locator(org.readium.r2.shared.util.Url("community")!!, org.readium.r2.shared.util.mediatype.MediaType.TEXT)
        app.notes.create(own.id, locator, text, note, 0xFFE0B04AL, m.quote?.chapter?.ifBlank { null } ?: "Из сообщества")
        "Сохранено в заметки к «${own.title}»"
    } else {
        val locator = org.readium.r2.shared.publication.Locator(org.readium.r2.shared.util.Url("community")!!, org.readium.r2.shared.util.mediatype.MediaType.TEXT)
        app.notes.create(COMMUNITY_NOTES, locator, text, note, 0xFFE0B04AL, CommunityNav.roomTitle.ifBlank { "Сообщество" })
        "Сохранено в заметки"
    }
}

const val COMMUNITY_NOTES = "community"

@Composable
private fun SprintCard(sprint: Community.Sprint?, signed: Boolean) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sprint?.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val live = sprint != null && !sprint.done && now < sprint.endLocal
    val shape = appCorner(20)
    Row(
        Modifier
            .fillMaxWidth()
            .scrollReveal()
            .liveClick(shape, sound = FeedbackSound.Timer, pressedScale = 0.98f) {
                if (live || sprint != null) CommunityNav.sprint = true
                else if (signed) CommunityNav.sprint = true
                else Toast.makeText(app, "Войди в аккаунт, чтобы участвовать", Toast.LENGTH_SHORT).show()
            }
            .clip(shape)
            .background(AppColors.Panel)
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tile(AppIcons.Timer, 42.dp, filled = live)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when {
                    sprint == null -> "Спринт чтения"
                    live && now < sprint.startLocal -> "Спринт через ${((sprint.startLocal - now) / 1000).coerceAtLeast(0)} с"
                    live -> "Идёт спринт · ${sprintClock(sprint.endLocal - now)}"
                    else -> "Спринт закончился"
                },
                color = AppColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                when {
                    sprint == null -> "Читаем вместе 15, 25 или 45 минут"
                    live -> "Читают: ${sprint.people.size}" + if (sprint.joined) " · ты в спринте" else ""
                    else -> "Итоги: ${sprint.people.size} участн. · ${sprint.people.sumOf { it.pages }} стр."
                },
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (live && sprint != null && !sprint.joined && signed) {
            Text(
                "Вступить",
                color = AppColors.OnAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.liveClick(appCorner(10), sound = FeedbackSound.Go) {
                    scope.launch { app.community.joinSprint().onSuccess { CommunityNav.sprint = true }.onFailure { Toast.makeText(app, it.message, Toast.LENGTH_SHORT).show() } }
                }.clip(appCorner(10)).background(AppColors.Accent).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

private fun sprintClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun SprintRing(fraction: Float, size: Dp) {
    val track = AppColors.Line
    val accent = AppColors.Accent
    val anim by androidx.compose.animation.core.animateFloatAsState(fraction, Motion.tween(900), label = "ring")
    Box(
        Modifier.size(size).drawBehind {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(accent, -90f, 360f * anim, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        },
    )
}

@Composable
private fun DayQuoteCard(q: Community.DayQuote) {
    Column(
        Modifier.fillMaxWidth().scrollReveal().clip(appCorner(18)).background(AppColors.Panel).border(1.dp, AppColors.PanelBorder, appCorner(18)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Quote, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            Text("Цитата дня", color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (q.reacts > 0) {
                Icon(AppIcons.Heart, null, tint = Color(0xFFE88A9A), modifier = Modifier.size(13.dp))
                Text("${q.reacts}", color = AppColors.Muted, fontSize = 11.sp)
            }
        }
        Text("«${q.text}»", color = AppColors.Text, fontSize = 15.sp, lineHeight = 22.sp, fontStyle = FontStyle.Italic, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
        Text(listOf(q.book, q.author).filter { it.isNotBlank() }.joinToString(" · ") + " — прислал(а) ${q.login}", color = AppColors.Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private val ShelfSpines = listOf(0xFF7A3E44, 0xFF3F5A7A, 0xFF6B5A32, 0xFF2F5A46, 0xFF5A3F6E, 0xFF8A5A3A, 0xFF4E7A7A).map { Color(it) }
private val ShelfMonths = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val ShelfHeights = listOf(0.80f, 0.92f, 0.76f, 0.88f, 1f, 0.84f)

private fun pagesText(n: Int) = "%,d".format(Locale.ROOT, n).replace(',', '\u00A0')

@Composable
private fun GoalCard(g: Community.Goal) {
    val app = LocalContext.current.applicationContext as App
    val fraction = (g.total.toFloat() / g.target.coerceAtLeast(1)).coerceIn(0f, 1f)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val anim by androidx.compose.animation.core.animateFloatAsState(if (shown) fraction else 0f, Motion.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "goal")
    val month = remember(g.month) { runCatching { ShelfMonths[java.time.YearMonth.parse(g.month).monthValue - 1] }.getOrDefault("месяца") }
    val count = 25
    val line = AppColors.Line
    val shape = appCorner(20)
    Column(
        Modifier.fillMaxWidth().scrollReveal().clip(shape).background(AppColors.Panel).border(1.dp, AppColors.PanelBorder, shape).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(AppIcons.Book, 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Полка $month", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Собираем вместе", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
            }
            Text("${(fraction * 100).toInt()}%", color = AppColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp)) {
            val pad = 4.dp.toPx()
            val gap = 3.dp.toPx()
            val w = (size.width - pad * 2 - gap * (count - 1)) / count
            val filled = anim * count
            val radius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
            for (i in 0 until count) {
                val h = size.height * ShelfHeights[i % ShelfHeights.size]
                val x = pad + i * (w + gap)
                val k = (filled - i).coerceIn(0f, 1f)
                drawRoundRect(line, androidx.compose.ui.geometry.Offset(x, size.height - h), androidx.compose.ui.geometry.Size(w, h), radius, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = dash), alpha = 1f - k)
                if (k > 0f) {
                    val hh = h * (0.6f + 0.4f * k)
                    val top = size.height - hh
                    drawRoundRect(ShelfSpines[i % ShelfSpines.size], androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Size(w, hh), radius, alpha = k)
                    drawRoundRect(Color.Black.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Size(w, hh), radius, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()), alpha = k)
                    drawRect(Color.White.copy(alpha = 0.10f * k), androidx.compose.ui.geometry.Offset(x + w * 0.2f, top + hh * 0.18f), androidx.compose.ui.geometry.Size(w * 0.6f, 1.5.dp.toPx()))
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(9.dp)
                .drawBehind { drawRoundRect(Color.Black.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(0f, 4.dp.toPx()), size, androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())) }
                .clip(appCorner(4))
                .background(Brush.verticalGradient(listOf(Color(0xFF6B4A28), Color(0xFF3D2A17)))),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = AppColors.Text, fontWeight = FontWeight.Bold))
                    append(pagesText(g.total))
                    pop()
                    append(" из ${pagesText(g.target)} стр.")
                },
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text("читателей: ${g.readers}", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
        }
        LaunchedEffect(Unit) { app.community.reportPages() }
    }
}

@Composable
private fun NotifySettings() {
    val app = LocalContext.current.applicationContext as App
    val community = app.community
    var on by remember { mutableStateOf(community.notifyOn) }
    var quiet by remember { mutableStateOf(community.quietNight) }
    var from by remember { mutableStateOf(community.quietFrom) }
    var to by remember { mutableStateOf(community.quietTo) }
    var picking by remember { mutableStateOf(false) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    SettingsGroup(null) {
        SettingSwitch("Уведомления об ответах", on, hint = "Проверка раз в 15 минут, когда приложение закрыто", icon = AppIcons.Bell) { v ->
            on = v
            community.notifyOn = v
            com.flerty.wikiflerty.system.CommunityNotify.schedule(app)
            if (v && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        SettingSwitch("Не беспокоить", quiet, hint = "С ${quietClock(from)} до ${quietClock(to)}", icon = AppIcons.Moon, enabled = on) { v ->
            quiet = v
            community.quietNight = v
        }
        AnimatedVisibility(on && quiet, enter = expandVertically(Motion.tween(260)) + fadeIn(Motion.fade(200)), exit = shrinkVertically(Motion.tween(220)) + fadeOut(Motion.fade(140))) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).liveClick(appCorner(12), sound = FeedbackSound.Sheet, pressedScale = 0.98f) { picking = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingIcon(AppIcons.Timer)
                Text("Время тишины", color = AppColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("${quietClock(from)} – ${quietClock(to)}", color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (picking) QuietSheet(from, to, onDismiss = { picking = false }) { f, t ->
        from = f
        to = t
        community.quietFrom = f
        community.quietTo = t
    }
}

private fun quietClock(m: Int) = "%d:%02d".format(m / 60, m % 60)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun QuietSheet(from: Int, to: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var edge by remember { mutableStateOf(0) }
    val start = androidx.compose.material3.rememberTimePickerState(from / 60, from % 60, is24Hour = true)
    val end = androidx.compose.material3.rememberTimePickerState(to / 60, to % 60, is24Hour = true)
    AppSheet(onDismiss, title = "Время тишины") {
        val close = sheetClose(onDismiss)
        SettingSegmented(null, listOf("С ${quietClock(start.hour * 60 + start.minute)}", "До ${quietClock(end.hour * 60 + end.minute)}"), edge) { edge = it }
        Box(Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.Crossfade(edge, animationSpec = Motion.fade(220), label = "quiet-edge") { e ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TimePicker(
                        if (e == 0) start else end,
                        colors = androidx.compose.material3.TimePickerDefaults.colors(
                            clockDialColor = AppColors.Field,
                            selectorColor = AppColors.Accent,
                            timeSelectorSelectedContainerColor = AppColors.AccentTint,
                            timeSelectorUnselectedContainerColor = AppColors.Field,
                            timeSelectorSelectedContentColor = AppColors.Accent,
                            timeSelectorUnselectedContentColor = AppColors.Text,
                            clockDialSelectedContentColor = AppColors.OnAccent,
                            clockDialUnselectedContentColor = AppColors.Text,
                        ),
                    )
                }
            }
        }
        HintNote("В это время уведомления об ответах не придут — прочитаешь их, когда откроешь сообщество", AppIcons.Moon)
        AppButton("Сохранить", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Success) {
            onSave(start.hour * 60 + start.minute, end.hour * 60 + end.minute)
            close()
        }
    }
}

@Composable
private fun SprintPage(state: Community.State) {
    val app = LocalContext.current.applicationContext as App
    val community = app.community
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val account by app.account.state.collectAsStateWithLifecycle()
    val sprintState by community.sprint.collectAsStateWithLifecycle()
    val sprint = sprintState ?: state.sprint
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var minutes by remember { mutableStateOf(25) }
    var busy by remember { mutableStateOf(false) }
    var party by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        community.sprintState()
        community.resumeSprint()
        while (true) {
            now = System.currentTimeMillis()
            if ((now / 1000) % 5 == 0L) community.sprintState()
            delay(1000)
        }
    }
    val wasLive = remember { mutableStateOf(false) }
    val live = sprint != null && !sprint.done && now < sprint.endLocal
    LaunchedEffect(live) {
        if (wasLive.value && !live && sprint?.joined == true) {
            party++
            feedback?.play(FeedbackSound.Success)
        }
        wasLive.value = live
    }
    Box(Modifier.fillMaxSize().confetti(party, listOf(AppColors.Accent, AppColors.AccentLight, Color(0xFFE8A458), Color(0xFF8FB4E0)))) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (sprint == null || (!live && sprint.done && now > sprint.endLocal + 15 * 60_000)) {
            Tile(AppIcons.Timer, 72.dp)
            Text("Читаем вместе", color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Запусти спринт — все в сообществе увидят его и смогут присоединиться. Страницы считаются сами, пока ты читаешь.", color = AppColors.Muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            SettingSegmented("Длительность", listOf("15 мин", "25 мин", "45 мин"), listOf(15, 25, 45).indexOf(minutes)) { i -> minutes = listOf(15, 25, 45)[i] }
            if (account.login == null) {
                GuestCard()
            } else {
                AppButton(if (busy) "Подожди…" else "Начать спринт", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Go) {
                    if (busy) return@AppButton
                    if (!state.me.accepted) {
                        Toast.makeText(app, "Сначала прими правила сообщества", Toast.LENGTH_SHORT).show()
                        return@AppButton
                    }
                    busy = true
                    scope.launch {
                        community.startSprint(minutes).onFailure { Toast.makeText(app, it.message, Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }
            }
            return@Column
        }
        val total = (sprint.endLocal - sprint.startLocal).toFloat().coerceAtLeast(1f)
        val waiting = now < sprint.startLocal
        val left = when {
            waiting -> 1f
            live -> ((sprint.endLocal - now) / total).coerceIn(0f, 1f)
            else -> 0f
        }
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            SprintRing(left, 200.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        waiting -> sprintClock(sprint.startLocal - now)
                        live -> sprintClock(sprint.endLocal - now)
                        else -> "Готово"
                    },
                    color = AppColors.Text,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Text(
                    when {
                        waiting -> "до старта"
                        live -> "спринт ${sprint.minutes} мин"
                        else -> "спринт завершён"
                    },
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                )
            }
        }
        if (sprint.joined) {
            val mine = sprint.people.firstOrNull { it.login == account.login }?.pages ?: 0
            Text("Твои страницы: ${maxOf(mine, if (live && !waiting) community.sprintPages() else 0)}", color = AppColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (live) HintNote("Открывай книгу и читай — страницы засчитаются сами, даже если выйти из сообщества", AppIcons.Book)
        } else if (live && account.login != null) {
            AppButton("Вступить в спринт", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Go) {
                scope.launch { community.joinSprint().onFailure { Toast.makeText(app, it.message, Toast.LENGTH_SHORT).show() } }
            }
        }
        TitlePill("Участники · ${sprint.people.size}", icon = AppIcons.People)
        Column(Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(6.dp)) {
            sprint.people.forEachIndexed { i, p ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(AppColors.PanelBorder))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", color = if (i < 3 && !waiting) AppColors.Accent else AppColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    LetterAvatar(p.login, 30.dp)
                    Text(p.login, color = if (p.login == account.login) AppColors.Accent else AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${p.pages} стр.", color = AppColors.Muted, fontSize = 13.sp)
                }
            }
        }
        if (live && sprint.joined) {
            Text(
                "Выйти из спринта",
                color = AppColors.Muted,
                fontSize = 13.sp,
                modifier = Modifier.liveClick(appCorner(10), sound = FeedbackSound.Close) { scope.launch { community.leaveSprint() } }.padding(10.dp),
            )
        }
    }
    }
}

private val ideaStatus = mapOf(
    "new" to ("Новая" to AppIcons.Sparkle),
    "review" to ("Рассматриваю" to AppIcons.Eye),
    "work" to ("В работе" to AppIcons.Pencil),
    "done" to ("Сделано" to AppIcons.Check),
    "no" to ("Не будет" to AppIcons.Close),
)

@Composable
private fun IdeasPage(state: Community.State, askRules: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val community = app.community
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    var tab by remember { mutableStateOf(0) }
    var ideas by remember { mutableStateOf<List<Community.Idea>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<Community.Idea?>(null) }
    var bumped by remember { mutableStateOf(0L to 0) }
    LaunchedEffect(Unit) { community.ideas().onSuccess { ideas = it }.onFailure { error = it.message } }
    val me = app.account.state.collectAsStateWithLifecycle().value.login
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { SettingSegmented(null, listOf("Идеи", "Обсуждение"), tab) { tab = it } }
        AnimatedContent(tab, transitionSpec = { fadeIn(Motion.fade(220)).togetherWith(fadeOut(Motion.fade(140))) }, label = "ideas-tab", modifier = Modifier.weight(1f)) { t ->
            if (t == 1) {
                RoomPage("ideas", state, askRules)
                return@AnimatedContent
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (me != null) {
                    AppButton("Предложить идею", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Add) {
                        if (!state.me.accepted) askRules() else adding = true
                    }
                } else GuestCard()
                SettingSegmented(null, listOf("Популярные", "В работе", "Сделано"), filter) { filter = it }
                val list = ideas
                when {
                    list == null && error != null -> HintNote(error!!, AppIcons.Alert)
                    list == null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(26.dp)) }
                    else -> {
                        val shown = list.filter {
                            when (filter) {
                                1 -> it.status == "work" || it.status == "review"
                                2 -> it.status == "done"
                                else -> it.status != "done" && it.status != "no"
                            }
                        }.sortedWith(compareByDescending<Community.Idea> { it.votes }.thenByDescending { it.at })
                        if (shown.isEmpty()) HintNote(if (filter == 0) "Идей пока нет — предложи первую" else "Здесь пока пусто", AppIcons.Bulb)
                        shown.forEach { idea ->
                            IdeaCard(
                                idea,
                                bump = if (bumped.first == idea.id) bumped.second else 0,
                                canManage = state.me.admin,
                                onVote = {
                                    if (me == null) return@IdeaCard
                                    feedback?.play(if (idea.voted) FeedbackSound.Uncheck else FeedbackSound.React)
                                    bumped = idea.id to (bumped.second + 1)
                                    ideas = list.map { if (it.id == idea.id) it.copy(voted = !it.voted, votes = it.votes + if (it.voted) -1 else 1) else it }
                                    scope.launch { community.idea("vote") { put("id", idea.id) }.onSuccess { ideas = it } }
                                },
                                onManage = { managing = idea },
                            )
                        }
                    }
                }
            }
        }
    }
    if (adding) IdeaSheet(onDismiss = { adding = false }) { title, text ->
        scope.launch {
            community.idea("add") { put("title", title); put("text", text) }
                .onSuccess { ideas = it; adding = false; feedback?.play(FeedbackSound.Success) }
                .onFailure { Toast.makeText(app, it.message, Toast.LENGTH_LONG).show() }
        }
    }
    managing?.let { idea ->
        AppSheet(onDismiss = { managing = null }, title = "Выбор статуса") {
            ActionGroup(ideaStatus.map { (id, look) ->
                ActionItem(look.second, look.first, null, FeedbackSound.Toggle) {
                    scope.launch { community.admin("idea") { put("id", idea.id); put("status", id) }; community.ideas().onSuccess { ideas = it } }
                    managing = null
                }
            })
            ActionGroup(listOf(ActionItem(AppIcons.Trash, "Удалить идею", AppColors.Bad, FeedbackSound.Trash) {
                scope.launch { community.idea("delete") { put("id", idea.id) }.onSuccess { ideas = it } }
                managing = null
            }))
        }
    }
}

@Composable
private fun IdeaCard(idea: Community.Idea, bump: Int, canManage: Boolean, onVote: () -> Unit, onManage: () -> Unit) {
    val (label, icon) = ideaStatus[idea.status] ?: ("Новая" to AppIcons.Sparkle)
    val statusColor = when (idea.status) {
        "done" -> AppColors.Good
        "work" -> AppColors.Accent
        "no" -> AppColors.Faint
        else -> AppColors.Muted
    }
    var expanded by remember(idea.id) { mutableStateOf(false) }
    var long by remember(idea.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().scrollReveal().liveClick(appCorner(18), sound = if (expanded) FeedbackSound.Collapse else FeedbackSound.Expand, pressedScale = 0.99f, enabled = long || canManage, onLongClick = if (canManage) onManage else null) { if (long) expanded = !expanded }.fieldSurface(appCorner(18)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val scale = remember { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(bump) {
            if (bump > 0) {
                scale.snapTo(1.18f)
                scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 420f))
            }
        }
        Column(
            Modifier
                .width(52.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .sparkBurst(bump, listOf(AppColors.Accent, AppColors.AccentLight), count = 9)
                .liveClick(appCorner(14), sound = FeedbackSound.React, pressedScale = 0.9f, onClick = onVote)
                .clip(appCorner(14))
                .background(if (idea.voted) AppColors.Accent else AppColors.Background.copy(alpha = 0.4f))
                .border(1.dp, if (idea.voted) AppColors.Accent else AppColors.Line, appCorner(14))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(AppIcons.Thumb, null, tint = if (idea.voted) AppColors.OnAccent else AppColors.Accent, modifier = Modifier.size(18.dp))
            Text("${idea.votes}", color = if (idea.voted) AppColors.OnAccent else AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).animateContentSize(Motion.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(idea.title, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
            if (idea.text.isNotBlank()) {
                Text(
                    idea.text,
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded && it.hasVisualOverflow) long = true },
                )
                if (long) {
                    val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 270f else 90f, Motion.tween(280), label = "idea-turn")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (expanded) "Свернуть" else "Развернуть", color = AppColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Icon(AppIcons.Chevron, null, tint = AppColors.Accent, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = turn })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.clip(appCorner(8)).border(1.dp, statusColor.copy(alpha = 0.6f), appCorner(8)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(icon, null, tint = statusColor, modifier = Modifier.size(11.dp))
                    Text(label, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(idea.login + " · " + chatAgo(idea.at), color = AppColors.Faint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (idea.note.isNotBlank()) {
                Row(Modifier.clip(appCorner(10)).background(AppColors.AccentTintStrong).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.Shield, null, tint = AppColors.Accent, modifier = Modifier.size(12.dp))
                    Text(idea.note, color = AppColors.Text, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun IdeaSheet(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AppSheet(onDismiss = onDismiss, title = "Новая идея") {
        HintNote("Коротко и по делу — за идеи голосуют, а самые популярные я беру в работу", AppIcons.Bulb)
        InputField(title, { title = it.take(80) }, "Суть идеи")
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(600) },
            textStyle = TextStyle(color = AppColors.Text, fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(AppColors.Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(appCorner(12)).background(AppColors.Background).border(1.dp, AppColors.Line, appCorner(12)).padding(12.dp),
            decorationBox = { inner -> Box { if (text.isEmpty()) Text("Подробности, если нужно", color = AppColors.Faint, fontSize = 14.sp); inner() } },
        )
        AppButton("Отправить", primary = title.trim().length >= 4, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Tap) {
            if (title.trim().length >= 4) onSend(title.trim(), text.trim())
        }
    }
}
