package com.flerty.wikiflerty.ui

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.sync.Account
import com.flerty.wikiflerty.sync.CloudSync
import kotlinx.coroutines.launch

fun cloudSize(bytes: Long): String {
    val gb = 1024.0 * 1024 * 1024
    val mb = 1024.0 * 1024
    return when {
        bytes >= gb -> String.format(java.util.Locale.forLanguageTag("ru"), "%.1f ГБ", bytes / gb)
        bytes >= mb -> "${(bytes / mb).toInt()} МБ"
        bytes <= 0 -> "0 МБ"
        else -> "${(bytes / 1024).coerceAtLeast(1)} КБ"
    }
}

@Composable
fun AccountContent() {
    val app = LocalContext.current.applicationContext as App
    val account = app.account
    val state by account.state.collectAsStateWithLifecycle()
    if (!account.available) {
        HintNote("Аккаунт появится после обновления приложения. Пока можно синхронизироваться по коду.", AppIcons.Cloud)
        SyncContent()
        return
    }
    var recovery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    androidx.compose.animation.Crossfade(state.signedIn, animationSpec = Motion.fade(260), label = "account") { signed ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (signed) SignedIn(account, state) else SignIn(account) { recovery = it }
        }
    }
    recovery?.let { code -> RecoveryCodeSheet(code) { recovery = null } }
}

@Composable
fun SignIn(account: Account, showCode: Boolean = true, onLater: (() -> Unit)? = null, onSigned: () -> Unit = {}, onRecovery: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val cloud by app.cloud.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }
    val feedback = LocalFeedback.current
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun submit() {
        val name = login.trim()
        when {
            name.length < 3 -> return toast("Логин: от 3 символов")
            password.length < 8 -> return toast("Пароль должен быть не короче 8 символов")
            mode == 1 && password != repeat -> return toast("Пароли не совпадают")
        }
        busy = true
        scope.launch {
            if (mode == 1) {
                account.register(name, password).onSuccess { feedback?.play(FeedbackSound.SignUp); onRecovery(it) }.onFailure { feedback?.play(FeedbackSound.Deny); toast(it.message ?: "Не получилось") }
            } else {
                account.login(name, password).onSuccess { feedback?.play(FeedbackSound.SignIn); toast("Вход выполнен"); onSigned() }.onFailure { feedback?.play(FeedbackSound.Deny); toast(it.message ?: "Не получилось") }
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(20)).padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShelfSyncScene()
        Text("Твоя полка — на любом устройстве", color = AppColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Text("500 МБ для книг бесплатно", color = AppColors.Accent, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(20)).animateContentSize(Motion.tween(260)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingSegmented(null, listOf("Вход", "Регистрация"), mode) { mode = it }
        InputField(login, { v -> login = v.filter { it.isLetterOrDigit() || it == '_' }.take(24) }, "Логин")
        InputField(password, { password = it.take(200) }, "Пароль", secret = true)
        if (mode == 1) {
            InputField(repeat, { repeat = it.take(200) }, "Пароль ещё раз", secret = true)
            Text("Логин: латиница, цифры и _. Пароль: от 8 символов", color = AppColors.Faint, fontSize = 12.sp)
            if (cloud.enabled) Text("Синхронизация по коду перейдёт в аккаунт со всеми данными", color = AppColors.Faint, fontSize = 12.sp)
        }
        AppButton(if (busy) "Подожди…" else if (mode == 1) "Создать аккаунт" else "Войти", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Tap) {
            if (!busy) submit()
        }
        if (mode == 0) {
            Text(
                "Забыл пароль",
                color = AppColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally).liveClick(appCorner(10), sound = FeedbackSound.Open) { forgot = true }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    if (onLater != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Позже, без аккаунта ›",
                color = AppColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier.liveClick(appCorner(10), sound = FeedbackSound.Close) { onLater() }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
    if (showCode) {
        TitlePill("Синхронизация по коду", icon = AppIcons.Lock, modifier = Modifier.padding(top = 6.dp))
        SyncContent()
    }

    if (forgot) RecoverSheet(account, login, onDone = { code -> forgot = false; onRecovery(code) }) { forgot = false }
}

@Composable
private fun RecoverSheet(account: Account, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf(initial) }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AppSheet(onDismiss = onDismiss, title = "Восстановить пароль") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Нужен код восстановления, который приложение показало при регистрации", color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            InputField(login, { v -> login = v.filter { it.isLetterOrDigit() || it == '_' }.take(24) }, "Логин")
            InputField(code, { code = it.take(40) }, "Код восстановления", monospace = true)
            InputField(password, { password = it.take(200) }, "Новый пароль", secret = true)
            AppButton(if (busy) "Подожди…" else "Сменить пароль и войти", primary = true, modifier = Modifier.fillMaxWidth()) {
                if (busy) return@AppButton
                if (password.length < 8) {
                    Toast.makeText(context, "Пароль должен быть не короче 8 символов", Toast.LENGTH_LONG).show()
                    return@AppButton
                }
                busy = true
                scope.launch {
                    account.recover(login.trim(), code, password)
                        .onSuccess { onDone(it) }
                        .onFailure { Toast.makeText(context, it.message ?: "Не получилось", Toast.LENGTH_LONG).show() }
                    busy = false
                }
            }
        }
    }
}

@Composable
fun RecoveryCodeSheet(code: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppSheet(onDismiss = onDismiss, title = "Код восстановления") {
        val close = sheetClose(onDismiss)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Если забудешь пароль, войти получится только с этим кодом. Сохрани его в надёжном месте — показываем один раз.",
                color = AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Box(
                Modifier.fillMaxWidth().background(AppColors.Background, appCorner(14)).border(1.dp, AppColors.Accent.copy(alpha = 0.45f), appCorner(14)).padding(vertical = 16.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(code, color = AppColors.Text, fontSize = 17.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton("Скопировать", primary = false, modifier = Modifier.weight(1f)) {
                    val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                    clip?.setPrimaryClip(android.content.ClipData.newPlainText("WikiFlerty", code))
                    Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                }
                AppButton("Я сохранил", primary = true, modifier = Modifier.weight(1f)) { close() }
            }
        }
    }
}

@Composable
private fun SignedIn(account: Account, state: Account.State) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val cloud = app.cloud
    val status by cloud.status.collectAsStateWithLifecycle()
    val sync by app.sync.store.state.collectAsStateWithLifecycle()
    val books by app.library.books.collectAsStateWithLifecycle()
    val busyBooks by account.busyBooks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<CloudSync.Device?>(null) }
    var dropping by remember { mutableStateOf<Account.CloudBook?>(null) }
    var changing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf(false) }
    val feedback = LocalFeedback.current
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    androidx.compose.runtime.LaunchedEffect(Unit) { account.refresh() }
    val avatarPick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AccountAvatar.set(context, uri) }
            if (!ok) Toast.makeText(context, "Не удалось открыть картинку", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(20)).animateContentSize(Motion.tween(260)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountAvatarBox(52.dp, onClick = { avatarPick.launch("image/*") })
            Column(Modifier.weight(1f)) {
                Text(state.login.orEmpty(), color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(planLine(state), color = if (state.until > 0) AppColors.Accent else AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AvatarEditMarker(Modifier.align(Alignment.Top))
        }
        val limit = state.limit.takeIf { it > 0 } ?: (500L shl 20)
        val fraction = if (state.used > 0) (state.used.toFloat() / limit).coerceIn(0.02f, 1f) else 0f
        CloudShelf(fraction, height = 92.dp, free = if (fraction > 0.92f) "почти полна" else "свободно", freeAccent = fraction > 0.92f, seed = (state.login?.length ?: 0) % 12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${state.books.size} ${countWord(state.books.size, "книга", "книги", "книг")} · ${cloudSize(state.used)}", color = AppColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text("из ${cloudSize(limit)}", color = AppColors.Faint, fontSize = 13.sp)
        }
        val line = state.working ?: state.notice
        Appear(line != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.working != null) KitSpinner(Modifier.size(14.dp), AppColors.Accent)
                Text(line.orEmpty(), color = if (state.working != null) AppColors.Muted else AppColors.Bad, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (state.payOn || state.shopOn) {
            AppButton(if (state.until > 0) "Продлить полку" else "Расширить полку", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Sheet) { buying = true }
        }
    }

    StatusRow(status) { scope.launch { cloud.syncNow(); account.poke(0) } }

    SettingsGroup(null) {
        SettingSwitch("Загружать книги", state.uploadBooks, hint = null, icon = AppIcons.Cloud) { account.setUploadBooks(it) }
        ListDivider()
        SettingSwitch("Скачивать по Wi-Fi", state.autoDownload, hint = null, icon = AppIcons.Download) { account.setAutoDownload(it) }
        ListDivider()
        SettingSwitch("Переносить настройки", sync.syncSettings, hint = null, icon = AppIcons.Options) { v ->
            app.sync.store.update { it.copy(syncSettings = v) }
        }
    }

    val localFps = books.mapNotNull { it.fingerprint }.toSet()
    val localIds = books.filter { it.fingerprint != null }.associate { it.fingerprint!! to it.id }
    TabbedFrame("Книги в облаке", icon = AppIcons.Cloud, trailing = { CountBadge(state.books.size) }) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.books.isNotEmpty()) {
                var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
                if (state.books.size > 3) InputField(query, { query = it.take(60) }, "Поиск в облаке", icon = AppIcons.Search)
                val shown = remember(state.books, query) {
                    val q = query.trim().lowercase()
                    if (q.isEmpty()) state.books else state.books.filter { it.name.lowercase().contains(q) }
                }
                if (shown.isEmpty()) Text("Ничего не нашлось", color = AppColors.Faint, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                val hold = remember(listState) { HoldScroll(listState) }
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 296.dp).nestedScroll(hold).fadingEdges(listState),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown.size, key = { shown[it].fp }) { i ->
                        CloudBookRow(shown[i], shown[i].fp in localFps, localIds[shown[i].fp], shown[i].fp in busyBooks, onDownload = { b ->
                            scope.launch {
                                account.downloadNow(b.fp).onSuccess { toast("Книга в библиотеке") }.onFailure { toast(it.message ?: "Не получилось скачать") }
                            }
                        }, onDrop = { dropping = it })
                    }
                }
            }
            if (state.books.isEmpty()) {
                Text(
                    if (state.uploadBooks) "Книги загрузятся сами, когда будет связь" else "Пока пусто",
                    color = AppColors.Faint,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
    }

    DevicesPanel(cloud) { device -> removing = device }

    SettingsGroup(null) {
        SettingsNavRow(AppIcons.Lock, "Сменить пароль", "", framed = false) { changing = true }
        ListDivider()
        SettingsNavRow(AppIcons.Person, "Выйти из аккаунта", "Данные на телефоне останутся", framed = false) { leaving = true }
        ListDivider()
        SettingsNavRow(AppIcons.Trash, "Удалить аккаунт", "Вместе с книгами в облаке", framed = false) { deleting = true }
    }

    dropping?.let { b ->
        ConfirmSheet(
            title = "Удалить из облака?",
            text = "«${b.name}» пропадёт из облака и не скачается на новые устройства. На этом телефоне книга останется.",
            confirm = "Удалить",
            onConfirm = { scope.launch { account.deleteFromCloud(b.fp).onFailure { toast(it.message ?: "Не получилось") } } },
            onDismiss = { dropping = null },
        )
    }

    removing?.let { device ->
        ConfirmSheet(
            title = if (device.self) "Отключить этот телефон?" else "Удалить устройство?",
            text = if (device.self) "Синхронизация на этом телефоне выключится до следующего входа. Данные на нём останутся." else "«${device.name}» перестанет синхронизироваться при следующем подключении к интернету. Данные на нём останутся.",
            confirm = "Удалить",
            onConfirm = { cloud.removeDevice(device.id) },
            onDismiss = { removing = null },
        )
    }

    if (leaving) {
        ConfirmSheet(
            title = "Выйти из аккаунта?",
            text = "Синхронизация на этом телефоне выключится. Книги и прогресс останутся на телефоне и в облаке.",
            confirm = "Выйти",
            sound = FeedbackSound.SignOut,
            icon = AppIcons.Cloud,
            onConfirm = { scope.launch { account.logout() } },
            onDismiss = { leaving = false },
        )
    }

    if (buying) PlanSheet(account, state) { buying = false }

    if (changing) PasswordSheet(
        title = "Сменить пароль",
        fields = listOf("Текущий пароль", "Новый пароль"),
        action = "Сменить",
        onDismiss = { changing = false },
    ) { values, done ->
        if (values[1].length < 8) return@PasswordSheet done(false, "Пароль должен быть не короче 8 символов")
        scope.launch {
            account.changePassword(values[0], values[1])
                .onSuccess { done(true, "Пароль изменён") }
                .onFailure { done(false, it.message ?: "Не получилось") }
        }
    }

    if (deleting) PasswordSheet(
        title = "Удалить аккаунт?",
        text = "Аккаунт, книги в облаке и синхронизированные данные удалятся навсегда. На этом телефоне всё останется.",
        fields = listOf("Пароль"),
        action = "Удалить навсегда",
        onDismiss = { deleting = false },
    ) { values, done ->
        scope.launch {
            account.deleteAccount(values[0])
                .onSuccess { feedback?.play(FeedbackSound.Wipe); done(true, "Аккаунт удалён") }
                .onFailure { done(false, it.message ?: "Не получилось") }
        }
    }
}

@Composable
private fun PasswordSheet(
    title: String,
    fields: List<String>,
    action: String,
    onDismiss: () -> Unit,
    text: String? = null,
    onSubmit: (List<String>, (Boolean, String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val values = remember { androidx.compose.runtime.mutableStateListOf(*Array(fields.size) { "" }) }
    var busy by remember { mutableStateOf(false) }
    AppSheet(onDismiss = onDismiss, title = title) {
        val close = sheetClose(onDismiss)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            text?.let { HintNote(it, AppIcons.Info) }
            fields.forEachIndexed { i, label -> InputField(values[i], { values[i] = it.take(200) }, label, secret = true) }
            AppButton(if (busy) "Подожди…" else action, primary = true, modifier = Modifier.fillMaxWidth()) {
                if (busy) return@AppButton
                busy = true
                onSubmit(values.toList()) { ok, message ->
                    busy = false
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    if (ok) close()
                }
            }
        }
    }
}

private val dayFormat = java.text.SimpleDateFormat("d MMMM", java.util.Locale("ru"))

private fun planTitle(plan: String): String = when (plan) {
    "plus" -> "Облако+"
    "plus2" -> "Облако++"
    else -> "Бесплатно"
}

private fun planLine(state: Account.State): String {
    val limit = cloudSize(state.limit.takeIf { it > 0 } ?: (500L shl 20))
    if (state.until <= 0) return "Бесплатно · $limit в облаке"
    return "${planTitle(state.plan)} · $limit до ${dayFormat.format(java.util.Date(state.until))}"
}

@Composable
private fun CloudBookRow(b: Account.CloudBook, here: Boolean, localId: String?, busy: Boolean, onDownload: (Account.CloudBook) -> Unit, onDrop: (Account.CloudBook) -> Unit) {
    val shape = appCorner(14)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(AppColors.Line.copy(alpha = 0.45f), shape)
            .border(1.dp, AppColors.PanelBorder, shape)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BookCover(localId, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(b.name.ifBlank { "Без названия" }, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cloudSize(b.size) + if (here) " · на телефоне" else "", color = AppColors.Faint, fontSize = 12.sp, maxLines = 1)
        }
        when {
            busy -> Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(18.dp), AppColors.Accent) }
            !here -> Box(
                Modifier.size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Download) { onDownload(b) },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Download, "Скачать", tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
        }
        Box(
            Modifier.size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Close) { onDrop(b) },
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Trash, "Удалить из облака", tint = AppColors.Bad, modifier = Modifier.size(18.dp)) }
    }
}

private class HoldScroll(private val state: androidx.compose.foundation.lazy.LazyListState) : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    private val active get() = state.canScrollForward || state.canScrollBackward
    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset =
        if (active) available.copy(x = 0f) else androidx.compose.ui.geometry.Offset.Zero
    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity =
        if (active) available.copy(x = 0f) else androidx.compose.ui.unit.Velocity.Zero
}

private fun Modifier.fadingEdges(state: androidx.compose.foundation.lazy.LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = 22.dp.toPx()
        val top = state.canScrollBackward
        val bottom = state.canScrollForward
        if (top) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black), 0f, edge), size = androidx.compose.ui.geometry.Size(size.width, edge), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        if (bottom) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.Transparent), size.height - edge, size.height), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - edge), size = androidx.compose.ui.geometry.Size(size.width, edge), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }

private class Offer(val id: String, val title: String, val size: String, val price: Int, val hint: String, val shelves: Int)

private val offers = listOf(
    Offer("plus", "Облако+", "5 ГБ", 79, "одна полка · 3–5 тысяч книг", 1),
    Offer("plus2", "Облако++", "15 ГБ", 149, "целый шкаф · PDF и комиксы", 3),
)

@Composable
private fun PlanSheet(account: Account, state: Account.State, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(if (state.until > 0 && state.plan == "plus2") "plus2" else "plus") }
    var busy by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    AppSheet(onDismiss = onDismiss, title = "Больше места", smoothResize = false) {
        val close = sheetClose(onDismiss)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            offers.forEach { o ->
                val shape = appCorner(18)
                val selected = picked == o.id
                val current = state.until > 0 && state.plan == o.id
                val edge by androidx.compose.animation.animateColorAsState(if (selected) AppColors.Accent else AppColors.PanelBorder, Motion.fade(220), label = "plan-edge")
                val flag by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, Motion.tween(260), label = "plan-flag")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liveClick(shape, sound = FeedbackSound.Plan, pressedScale = 0.98f) { picked = o.id }
                        .background(AppColors.Line.copy(alpha = 0.45f), shape)
                        .border(if (selected) 1.5.dp else 1.dp, edge, shape)
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${o.title} · ${o.size}", color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Box(Modifier.size(28.dp).graphicsLayer { alpha = flag; scaleX = 0.4f + 0.6f * flag; scaleY = 0.4f + 0.6f * flag }) {
                                    if (flag > 0.01f) PlanHelpFlag(help && selected) { if (selected) help = !help }
                                }
                            }
                            Text(if (current) "твой тариф" else o.hint, color = if (selected || current) AppColors.Accent else AppColors.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${o.price} ₽", color = AppColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selected && help,
                        enter = androidx.compose.animation.expandVertically(Motion.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing), expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(Motion.tween(240, 80, androidx.compose.animation.core.FastOutSlowInEasing)),
                        exit = androidx.compose.animation.shrinkVertically(Motion.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing), shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(Motion.fade(160)),
                    ) {
                        Box(Modifier.padding(top = 10.dp)) {
                            HintNote(planExplain(state, o.id), if (state.until > 0) AppIcons.Calendar else AppIcons.Help)
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    androidx.compose.runtime.key(selected) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(o.shelves) { k ->
                                CloudShelf(1f, height = 30.dp, free = null, seed = k * 5 + o.price % 7, stagger = 16, startDelay = k * 140L, animated = selected)
                            }
                        }
                    }
                }
            }
            val price = offers.first { it.id == picked }.price
            if (!state.payOn) {
                LockedPay(price) { Toast.makeText(context, "Оплата откроется совсем скоро", Toast.LENGTH_SHORT).show() }
                return@Column
            }
            AppButton(if (busy) "Подожди…" else "Оплатить $price ₽", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Pay) {
                if (busy) return@AppButton
                busy = true
                scope.launch {
                    account.pay(picked)
                        .onSuccess { url ->
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }.onFailure { Toast.makeText(context, "Не нашёлся браузер для оплаты", Toast.LENGTH_LONG).show() }
                            close()
                        }
                        .onFailure { Toast.makeText(context, it.message ?: "Не получилось", Toast.LENGTH_LONG).show() }
                    busy = false
                }
            }
        }
    }
}

@Composable
private fun LockedPay(price: Int, onClick: () -> Unit) {
    val shape = appCorner(12)
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .liveClick(shape, sound = FeedbackSound.Deny, pressedScale = 0.98f, onClick = onClick)
            .background(AppColors.Accent.copy(alpha = 0.12f), shape)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(AppIcons.Lock, "Оплата пока закрыта", tint = AppColors.Accent.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        Text("Оплатить $price ₽", color = AppColors.Accent.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun planExplain(state: Account.State, id: String): String {
    if (state.until <= 0) return "Тариф действует 30 дней и сам не продлевается. Когда срок закончится, книги не удалятся — только новые загрузки встанут на паузу."
    val now = "Сейчас ${planTitle(state.plan)} до ${dayFormat.format(java.util.Date(state.until))}."
    return if (state.plan == id) "$now Оплата добавит ещё 30 дней." else "$now Оставшиеся дни пересчитаются по цене этого тарифа."
}

@Composable
private fun PlanHelpFlag(open: Boolean, onClick: () -> Unit) {
    val tint by androidx.compose.animation.animateColorAsState(if (open) AppColors.OnAccent else AppColors.Accent, Motion.fade(220), label = "plan-help-tint")
    val bg by androidx.compose.animation.animateColorAsState(if (open) AppColors.Accent else AppColors.Accent.copy(alpha = 0.14f), Motion.fade(220), label = "plan-help-bg")
    Box(
        Modifier
            .size(28.dp)
            .liveClick(CircleShape, sound = if (open) FeedbackSound.Collapse else FeedbackSound.Expand, pressedScale = 0.86f, onClick = onClick)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.Help, if (open) "Скрыть пояснение" else "Пояснение к тарифу", tint = tint, modifier = Modifier.size(16.dp)) }
}

@Composable
fun PlanActivatedHost() {
    val app = LocalContext.current.applicationContext as App
    if (!app.account.available) return
    val activation by app.account.activated.collectAsStateWithLifecycle()
    val act = activation ?: return
    val state by app.account.state.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    androidx.compose.runtime.LaunchedEffect(act) {
        kotlinx.coroutines.delay(260)
        feedback?.play(FeedbackSound.Grow)
    }
    val grew = act.limit > act.before
    AppSheet(onDismiss = { app.account.activated.value = null }, title = if (grew) "Полка стала больше" else "Полка продлена") {
        val close = sheetClose { app.account.activated.value = null }
        Column(
            Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val before = if (grew) act.before.toFloat() / act.limit else (state.used.toFloat() / act.limit.coerceAtLeast(1)).coerceIn(0.05f, 0.6f)
            CloudShelf(before.coerceIn(0.08f, 0.7f), height = 64.dp, free = if (grew) "+${cloudSize(act.limit - act.before)}" else "+30 дней", freeAccent = true, stagger = 45, startDelay = 200)
            if (act.plan == "plus2") CloudShelf(0f, height = 48.dp, free = "ещё полка", freeAccent = true)
            Row(Modifier.padding(top = 4.dp)) {
                Text("${planTitle(act.plan)} · ${cloudSize(act.limit)}", color = AppColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("до ${dayFormat.format(java.util.Date(act.until))}", color = AppColors.Muted, fontSize = 13.sp)
            }
        }
        AppButton("Отлично", primary = true, modifier = Modifier.fillMaxWidth(), labeled = true) { close() }
    }
}

private fun countWord(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

@Composable
fun ReadingNow() {
    val app = LocalContext.current.applicationContext as App
    if (!app.account.available) return
    val count by app.account.readingNow.collectAsStateWithLifecycle()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                app.account.peek()
                kotlinx.coroutines.delay(15_000)
            }
        }
    }
    val n = count
    androidx.compose.animation.AnimatedVisibility(
        visible = n != null,
        enter = androidx.compose.animation.fadeIn(Motion.fade(320)) + androidx.compose.animation.scaleIn(Motion.tween(320), initialScale = 0.85f),
        exit = androidx.compose.animation.fadeOut(Motion.fade(200)),
    ) {
        val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "live")
        val glow by pulse.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Restart), label = "live-dot")
        Row(
            Modifier.padding(end = 2.dp).height(44.dp).fieldSurface(CircleShape).padding(start = 13.dp, end = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(16.dp).graphicsLayer { scaleX = 0.5f + glow; scaleY = 0.5f + glow; alpha = (1f - glow) * 0.55f }.background(AppColors.Good, CircleShape))
                Box(Modifier.size(8.dp).background(AppColors.Good, CircleShape))
            }
            androidx.compose.animation.AnimatedContent(
                targetState = n ?: 0,
                transitionSpec = {
                    val up = targetState > initialState
                    (androidx.compose.animation.slideInVertically(Motion.tween(260)) { if (up) it else -it } + androidx.compose.animation.fadeIn(Motion.fade(220)))
                        .togetherWith(androidx.compose.animation.slideOutVertically(Motion.tween(220)) { if (up) -it else it } + androidx.compose.animation.fadeOut(Motion.fade(160)))
                },
                label = "live-count",
            ) { value ->
                Text("$value", color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }
}

@Composable
fun AccountWelcome(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val account = app.account
    val state by account.state.collectAsStateWithLifecycle()
    var recovery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(state.signedIn, recovery) {
        if (state.signedIn && recovery == null) onDone()
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDone,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = true),
    ) {
        KeepHostBars()
        Box(Modifier.fillMaxSize().background(AppColors.Background)) {
            KitBackdrop(AppStyle.decor, AppStyle.base, AppColors.Accent, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(AppColors.Background.copy(alpha = 0.3f), AppColors.Background.copy(alpha = 0.9f)))))
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    HeaderLabel("Аккаунт", icon = AppIcons.Person, size = 18.sp)
                }
                SignIn(account, showCode = false, onLater = onDone, onSigned = onDone) { recovery = it }
            }
        }
    }
    recovery?.let { code -> RecoveryCodeSheet(code) { recovery = null } }
}
