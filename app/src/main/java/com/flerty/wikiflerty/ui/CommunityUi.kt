package com.flerty.wikiflerty.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
    var draft by mutableStateOf("")
    val title: String get() = when {
        mod -> "Модерация"
        room != null -> roomTitle
        else -> "Сообщество"
    }

    fun back(): Boolean {
        if (mod) {
            mod = false
            return true
        }
        if (room != null) {
            room = null
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
    BackHandler(enabled = CommunityNav.room != null || CommunityNav.mod) { CommunityNav.back() }
    if (rules) RulesSheet(onAccept = { rules = false }) { rules = false }
    val target = when {
        CommunityNav.mod -> "mod"
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                    state.rooms.firstOrNull { it.id == top.room }?.let { r -> CommunityNav.roomTitle = r.title; CommunityNav.room = r.id }
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
        if (state.me.login == null) GuestCard()
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
    data class Item(val msg: Community.Msg, val first: Boolean) : ChatRow
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
    DisposableEffect(roomId) {
        community.open(roomId)
        onDispose {
            community.close()
            scope.launch { community.refresh() }
        }
    }
    val incoming by community.incoming.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) { if (incoming > 0) feedback?.play(FeedbackSound.Incoming) }
    var reply by remember { mutableStateOf<Community.Msg?>(null) }
    var actions by remember { mutableStateOf<Community.Msg?>(null) }
    var reporting by remember { mutableStateOf<Community.Msg?>(null) }
    var profile by remember { mutableStateOf<String?>(null) }
    val me = account.login ?: state.me.login
    val msgs = if (view.room == roomId) view.msgs else emptyList()
    val rows = remember(msgs) {
        val out = ArrayList<ChatRow>()
        var prev: Community.Msg? = null
        msgs.forEach { m ->
            val p = prev
            if (p == null || !sameDay(p.at, m.at)) out.add(ChatRow.Day(dayLabel(m.at), "d" + m.id))
            val first = p == null || p.login != m.login || m.at - p.at > 5 * 60_000 || !sameDay(p.at, m.at)
            out.add(ChatRow.Item(m, first))
            prev = m
        }
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
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                view.off -> Box(Modifier.padding(16.dp)) { OffCard() }
                !view.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(28.dp)) }
                msgs.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Tile(AppIcons.Chat, 60.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Здесь пока тихо", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Начни разговор первым", color = AppColors.Muted, fontSize = 13.sp)
                }
                else -> LazyColumn(
                    state = list,
                    reverseLayout = true,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = { r -> if (r is ChatRow.Item) r.msg.id else (r as ChatRow.Day).key }) { r ->
                        Box(Modifier.animateItem(fadeInSpec = Motion.fade(220), placementSpec = Motion.tween(260), fadeOutSpec = Motion.fade(160))) {
                            when (r) {
                                is ChatRow.Day -> DayChip(r.label)
                                is ChatRow.Item -> MessageItem(
                                    m = r.msg,
                                    first = r.first,
                                    mine = r.msg.login == me,
                                    me = me,
                                    onAvatar = { profile = r.msg.login },
                                    onLong = { actions = r.msg },
                                    onReact = { kind ->
                                        if (me == null) return@MessageItem
                                        feedback?.play(FeedbackSound.React)
                                        scope.launch { community.react(r.msg, kind).onFailure { Toast.makeText(app, it.message, Toast.LENGTH_SHORT).show() } }
                                    },
                                )
                            }
                        }
                    }
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
        Composer(roomId, state, me, reply, onClearReply = { reply = null }, askRules = askRules)
    }
    actions?.let { m ->
        ActionsSheet(m, me, state.me.admin, onDismiss = { actions = null }, onReply = { reply = m }, onProfile = { profile = m.login }, onReport = { reporting = m })
    }
    reporting?.let { m -> ReportSheet(m) { reporting = null } }
    profile?.let { l -> ProfileSheet(l, canMention = me != null && l != me) { profile = null } }
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
    onAvatar: () -> Unit,
    onLong: () -> Unit,
    onReact: (String) -> Unit,
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
    Row(
        Modifier.fillMaxWidth().padding(top = if (first) 8.dp else 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            if (first) Box(Modifier.liveClick(appCorner(10), sound = FeedbackSound.Open, onClick = onAvatar)) { LetterAvatar(m.login, 34.dp) } else Spacer(Modifier.width(34.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            Column(
                Modifier
                    .liveClick(shape, sound = FeedbackSound.Sheet, pressedScale = 0.98f, onLongClick = onLong, onClick = {})
                    .clip(shape)
                    .background(if (mine) AppColors.AccentTintStrong else AppColors.Panel)
                    .border(1.dp, when {
                        mine -> AppColors.Accent.copy(alpha = 0.45f)
                        mention -> AppColors.Accent
                        else -> AppColors.PanelBorder
                    }, shape)
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
                    Row(Modifier.clip(appCorner(8)).background(AppColors.Background.copy(alpha = 0.5f)).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
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
                        Text(m.reading, color = AppColors.Faint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
                    }
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.at)), color = AppColors.Faint, fontSize = 10.sp)
                }
            }
            if (m.reacts.isNotEmpty()) ReactRow(m, me, onReact)
        }
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
            Row(
                Modifier
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
private fun Composer(roomId: String, state: Community.State, me: String?, reply: Community.Msg?, onClearReply: () -> Unit, askRules: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    var text by remember(roomId) { mutableStateOf(CommunityNav.draft) }
    var sending by remember { mutableStateOf(false) }
    var sentAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var attach by remember { mutableStateOf(false) }
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
    val wait = if (state.me.admin) 0 else ((sentAt + state.slow * 1000L - now + 999) / 1000).toInt().coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, AppColors.Background.copy(alpha = 0.9f)))).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
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
        AnimatedVisibility(reply != null, enter = expandVertically(Motion.tween(220)) + fadeIn(Motion.fade(200)), exit = shrinkVertically(Motion.tween(200)) + fadeOut(Motion.fade(140))) {
            val r = reply
            if (r != null) Row(
                Modifier.fillMaxWidth().fieldSurface(appCorner(14)).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.Reply, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.login, color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(r.text.ifBlank { r.quote?.text ?: r.book?.title.orEmpty() }, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(32.dp).liveClick(CircleShape, sound = FeedbackSound.Close, onClick = onClearReply), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, "Отменить ответ", tint = AppColors.Muted, modifier = Modifier.size(16.dp))
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
                    .border(1.dp, AppColors.Line, appCorner(14))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(if (reply != null) "Ответ для ${reply.login}…" else "Сообщение…", color = AppColors.Faint, fontSize = 15.sp, maxLines = 1)
                        inner()
                    }
                },
            )
            val ready = text.isNotBlank() && !sending && wait == 0
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.liveClick(appCorner(13), sound = FeedbackSound.Tap, enabled = ready, pressedScale = 0.9f) {
                        if (!state.me.accepted) {
                            askRules()
                            return@liveClick
                        }
                        val body = text.trim()
                        sending = true
                        scope.launch {
                            app.community.send(roomId, body, reply?.id).onSuccess {
                                feedback?.play(FeedbackSound.Send)
                                text = ""
                                onClearReply()
                                sentAt = System.currentTimeMillis()
                            }.onFailure {
                                feedback?.play(FeedbackSound.Deny)
                                Toast.makeText(app, it.message ?: "Не отправилось", Toast.LENGTH_LONG).show()
                            }
                            sending = false
                        }
                    },
                ) {
                    androidx.compose.animation.Crossfade(ready, animationSpec = Motion.fade(200), label = "send-ready") { on ->
                        if (on) Tile(AppIcons.Send, 44.dp, filled = true) else Tile(AppIcons.Send, 44.dp, tint = AppColors.Faint)
                    }
                }
                if (sending) KitSpinner(Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(wait > 0, enter = fadeIn(Motion.fade(200)) + expandVertically(Motion.tween(200)), exit = fadeOut(Motion.fade(160)) + shrinkVertically(Motion.tween(200))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Clock, null, tint = AppColors.Muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("Медленный режим: ещё $wait с", color = AppColors.Muted, fontSize = 11.sp)
            }
        }
    }
    if (attach) AttachSheet(roomId) { attach = false }
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

@Composable
private fun SheetAction(icon: ImageVector, title: String, color: Color = AppColors.Text, sound: FeedbackSound = FeedbackSound.Tap, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().liveClick(appCorner(14), sound = sound, pressedScale = 0.98f, onClick = onClick).fieldSurface(appCorner(14)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tile(icon, 32.dp, tint = if (color == AppColors.Text) AppColors.Accent else color)
        Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionsSheet(m: Community.Msg, me: String?, admin: Boolean, onDismiss: () -> Unit, onReply: () -> Unit, onProfile: () -> Unit, onReport: () -> Unit) {
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
                            .liveClick(appCorner(14), sound = FeedbackSound.React, pressedScale = 0.85f) {
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
            SheetAction(AppIcons.Reply, "Ответить", sound = FeedbackSound.Edit) { onReply(); onDismiss() }
        }
        if (m.text.isNotBlank()) SheetAction(AppIcons.Copy, "Копировать", sound = FeedbackSound.Copy) {
            clipboard.setText(AnnotatedString(m.text))
            onDismiss()
        }
        SheetAction(AppIcons.Person, "Профиль ${m.login}", sound = FeedbackSound.Open) { onProfile(); onDismiss() }
        if (me != null && m.login != me) SheetAction(AppIcons.Flag, "Пожаловаться", AppColors.Bad, FeedbackSound.Report) { onReport(); onDismiss() }
        if (admin) {
            SheetAction(AppIcons.Trash, "Удалить", AppColors.Bad, FeedbackSound.Trash) {
                scope.launch { app.community.admin("delete") { put("room", m.room); put("id", m.id) } }
                onDismiss()
            }
            if (m.login != me) {
                SheetAction(AppIcons.Mute, "Мут на 24 ч", AppColors.Bad, FeedbackSound.Off) {
                    scope.launch { app.community.admin("mute") { put("login", m.login); put("hours", 24); put("room", m.room); put("id", m.id); put("reason", "нарушение правил") } }
                    feedback?.play(FeedbackSound.Off)
                    onDismiss()
                }
                SheetAction(AppIcons.Ban, "Забанить", AppColors.Bad, FeedbackSound.Wipe) {
                    scope.launch { app.community.admin("ban") { put("login", m.login); put("room", m.room); put("id", m.id) } }
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun ReportSheet(m: Community.Msg, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    AppSheet(onDismiss = onDismiss, title = "Пожаловаться") {
        Text(m.text.ifBlank { m.quote?.text ?: m.book?.title.orEmpty() }, color = AppColors.Muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().fieldSurface(appCorner(14)).padding(12.dp))
        listOf("spam" to (AppIcons.Ban to "Спам или реклама"), "rude" to (AppIcons.Alert to "Грубость"), "spoiler" to (AppIcons.EyeOff to "Спойлер"), "other" to (AppIcons.Flag to "Другое")).forEach { (id, look) ->
            SheetAction(look.first, look.second, sound = FeedbackSound.Report) {
                scope.launch {
                    app.community.report(m, id).onSuccess { Toast.makeText(app, "Жалоба отправлена", Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(app, it.message, Toast.LENGTH_SHORT).show() }
                }
                onDismiss()
            }
        }
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
                            Text(profile.reading, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun AttachSheet(roomId: String, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val books by app.library.books.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    AppSheet(onDismiss = onDismiss, title = "Книга из библиотеки") {
        HintNote("Уйдут только название, автор и обложка — сам файл книги не передаётся", AppIcons.Book)
        val sorted = remember(books) { books.sortedByDescending { it.lastReadAt ?: it.addedAt }.take(60) }
        sorted.forEach { b ->
            Row(
                Modifier.fillMaxWidth().liveClick(appCorner(14), sound = FeedbackSound.Pick, pressedScale = 0.98f) {
                    scope.launch {
                        app.community.send(roomId, "", null, book = Community.BookRef(b.title, b.author, (b.title.hashCode() and 0x7fffffff) % 12))
                            .onSuccess { feedback?.play(FeedbackSound.Send) }
                            .onFailure { Toast.makeText(app, it.message, Toast.LENGTH_LONG).show() }
                    }
                    onDismiss()
                }.fieldSurface(appCorner(14)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BookCover(b.id, 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(b.title, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (b.author.isNotBlank()) Text(b.author, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(AppIcons.Send, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            }
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
