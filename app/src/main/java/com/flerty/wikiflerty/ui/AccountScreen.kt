package com.flerty.wikiflerty.ui

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
private fun SignIn(account: Account, onRecovery: (String) -> Unit) {
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
                account.register(name, password).onSuccess { onRecovery(it) }.onFailure { toast(it.message ?: "Не получилось") }
            } else {
                account.login(name, password).onSuccess { toast("Вход выполнен") }.onFailure { toast(it.message ?: "Не получилось") }
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(20)).animateContentSize(Motion.tween(260)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HintNote("Прогресс, заметки, настройки и сами книги — на любом телефоне по логину и паролю. 1 ГБ в облаке бесплатно.", AppIcons.Cloud, framed = false)
        ListDivider()
        SettingSegmented(null, listOf("Вход", "Регистрация"), mode) { mode = it }
        InputField(login, { v -> login = v.filter { it.isLetterOrDigit() || it == '_' }.take(24) }, "Логин")
        InputField(password, { password = it.take(200) }, "Пароль", secret = true)
        if (mode == 1) {
            InputField(repeat, { repeat = it.take(200) }, "Пароль ещё раз", secret = true)
            Text("Логин: латиница, цифры и _. Пароль: от 8 символов", color = AppColors.Faint, fontSize = 12.sp)
            if (cloud.enabled) Text("Твоя синхронизация по коду перейдёт в аккаунт вместе со всеми данными", color = AppColors.Faint, fontSize = 12.sp)
        }
        AppButton(if (busy) "Подожди…" else if (mode == 1) "Создать аккаунт" else "Войти", primary = true, modifier = Modifier.fillMaxWidth()) {
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
    Text("Без аккаунта, по коду", color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    SyncContent()

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
private fun RecoveryCodeSheet(code: String, onDismiss: () -> Unit) {
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
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    androidx.compose.runtime.LaunchedEffect(Unit) { account.refresh() }

    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(20)).animateContentSize(Motion.tween(260)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingIcon(AppIcons.Person, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(state.login.orEmpty(), color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Бесплатно · ${cloudSize(state.limit.takeIf { it > 0 } ?: (1L shl 30))} в облаке", color = AppColors.Faint, fontSize = 12.sp)
            }
        }
        val limit = state.limit.takeIf { it > 0 } ?: (1L shl 30)
        val fraction = (state.used.toFloat() / limit).coerceIn(0f, 1f)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(10.dp).background(AppColors.Line.copy(alpha = 0.6f), CircleShape)) {
                Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(if (state.used > 0) 0.02f else 0f)).fillMaxHeight().background(if (fraction > 0.92f) AppColors.Bad else AppColors.Accent, CircleShape))
            }
            Row {
                Text("Занято ${cloudSize(state.used)} из ${cloudSize(limit)}", color = AppColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${state.books.size} ${countWord(state.books.size, "книга", "книги", "книг")}", color = AppColors.Faint, fontSize = 13.sp)
            }
        }
        val line = state.working ?: state.notice
        Appear(line != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.working != null) KitSpinner(Modifier.size(14.dp), AppColors.Accent)
                Text(line.orEmpty(), color = if (state.working != null) AppColors.Muted else AppColors.Bad, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    StatusRow(status) { scope.launch { cloud.syncNow(); account.poke(0) } }

    SettingsGroup(null) {
        SettingSwitch("Загружать книги в облако", state.uploadBooks, hint = "Чтобы они появились на других устройствах", icon = AppIcons.Cloud) { account.setUploadBooks(it) }
        ListDivider()
        SettingSwitch("Скачивать книги автоматически", state.autoDownload, hint = "Только по Wi-Fi", icon = AppIcons.Download) { account.setAutoDownload(it) }
        ListDivider()
        SettingSwitch("Переносить настройки", sync.syncSettings, hint = "Оформление и опции чтения", icon = AppIcons.Options) { v ->
            app.sync.store.update { it.copy(syncSettings = v) }
        }
    }

    val localFps = books.mapNotNull { it.fingerprint }.toSet()
    TabbedFrame("Книги в облаке", icon = AppIcons.Cloud, trailing = { CountBadge(state.books.size) }) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.books.isEmpty()) {
                Text(
                    if (state.uploadBooks) "Книги загрузятся сами, когда будет связь" else "Пока пусто",
                    color = AppColors.Faint,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            state.books.forEach { b ->
                androidx.compose.runtime.key(b.fp) {
                    val here = b.fp in localFps
                    val shape = appCorner(14)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .background(AppColors.Line.copy(alpha = 0.45f), shape)
                            .border(1.dp, AppColors.PanelBorder, shape)
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name.ifBlank { "Без названия" }, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                cloudSize(b.size) + if (here) " · на телефоне" else "",
                                color = AppColors.Faint,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                        when {
                            b.fp in busyBooks -> Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(18.dp), AppColors.Accent) }
                            !here -> Box(
                                Modifier.size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Download) {
                                    scope.launch {
                                        account.downloadNow(b.fp).onSuccess { toast("Книга в библиотеке") }.onFailure { toast(it.message ?: "Не получилось скачать") }
                                    }
                                },
                                contentAlignment = Alignment.Center,
                            ) { Icon(AppIcons.Download, "Скачать", tint = AppColors.Accent, modifier = Modifier.size(20.dp)) }
                        }
                        Box(
                            Modifier.size(38.dp).liveClick(CircleShape, sound = FeedbackSound.Close) { dropping = b },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Trash, "Удалить из облака", tint = AppColors.Bad, modifier = Modifier.size(18.dp)) }
                    }
                }
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
            onConfirm = { scope.launch { account.logout() } },
            onDismiss = { leaving = false },
        )
    }

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
                .onSuccess { done(true, "Аккаунт удалён") }
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
            text?.let { Text(it, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp) }
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

private fun countWord(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}
