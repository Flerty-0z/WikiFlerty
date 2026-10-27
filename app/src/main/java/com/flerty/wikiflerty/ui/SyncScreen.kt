package com.flerty.wikiflerty.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.sync.Backup
import com.flerty.wikiflerty.sync.DropboxStore
import com.flerty.wikiflerty.sync.GoogleAuth
import com.flerty.wikiflerty.sync.SyncManager
import com.google.android.gms.auth.api.identity.Identity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val Good = Color(0xFF7ED3A0)
private val Bad = Color(0xFFF08A8A)

/** Sync with Google Drive and Dropbox, and export/import of all data. */
@Composable
fun SyncScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val sync = app.sync
    val state by sync.store.state.collectAsStateWithLifecycle()
    val running by sync.running.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var includeBooks by remember { mutableStateOf(false) }
    var busyFile by remember { mutableStateOf<String?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                busyFile = "Сохраняю…"
                runCatching { Backup.export(app, context, uri, includeBooks) }
                    .onSuccess { toast("Сохранено") }
                    .onFailure { toast(it.message ?: "Не удалось сохранить") }
                busyFile = null
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busyFile = "Загружаю…"
                runCatching { Backup.import(app, context, uri) }
                    .onSuccess { report ->
                        val books = Backup.lastImportedBooks
                        toast((if (books > 0) "Книг добавлено: $books. " else "") + report.describe())
                    }
                    .onFailure { toast(it.message ?: "Не удалось загрузить файл") }
                busyFile = null
            }
        }
    }
    val googleConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data) }
                .onSuccess {
                    sync.store.update { s -> s.copy(googleConnected = true) }
                    scope.launch { sync.syncNow().onFailure { e -> toast(e.message ?: "Ошибка") } }
                }
                .onFailure { toast("Google: ${it.message}") }
        }
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Status and the main button
        Column(
            Modifier.fillMaxWidth().panelSurface(appCorner(20)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Последняя синхронизация", color = AppColors.Muted, fontSize = 13.sp)
            Text(SyncManager.formatTime(state.lastSyncAt), color = AppColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            state.lastResult?.let { Text(it, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp) }
            val shape = appCorner(14)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .liveClick(shape, sound = FeedbackSound.Toggle, enabled = !running && sync.hasCloud) {
                        scope.launch { sync.syncNow().onFailure { toast(it.message ?: "Ошибка") } }
                    }
                    .background(if (sync.hasCloud) AppColors.Accent else AppColors.Line),
                contentAlignment = Alignment.Center,
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = AppColors.OnAccent)
                } else {
                    Text(
                        if (sync.hasCloud) "Синхронизировать сейчас" else "Подключи облако ниже",
                        color = if (sync.hasCloud) AppColors.OnAccent else AppColors.Muted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                "Синхронизируются прогресс чтения, заметки, словарь и настройки. Сами книги не загружаются — " +
                    "на другом телефоне просто добавь ту же книгу, и всё подтянется. Ключи нейросетей не передаются.",
                color = AppColors.Faint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }

        // Google Drive
        CloudCard(
            title = "Google Drive",
            connected = state.googleConnected,
            description = "Файл хранится в скрытой папке приложения на твоём Google Диске.",
            onDisconnect = { sync.store.update { it.copy(googleConnected = false) } },
        ) {
            ActionButton("Подключить Google Drive") {
                scope.launch {
                    runCatching { GoogleAuth.authorize(context) }
                        .onSuccess { result ->
                            val pending = result.pendingIntent
                            if (result.hasResolution() && pending != null) {
                                googleConsent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                            } else {
                                sync.store.update { it.copy(googleConnected = true) }
                                sync.syncNow().onFailure { e -> toast(e.message ?: "Ошибка") }
                            }
                        }
                        .onFailure {
                            toast(
                                "Google не дал доступ: ${it.message}. Проверь, что приложение зарегистрировано в Google Cloud " +
                                    "(инструкция в чате с Claude).",
                            )
                        }
                }
            }
        }

        // Dropbox
        DropboxCard(state.dropboxConnected, state.dropboxAppKey, sync, onError = ::toast, onConnected = {
            scope.launch { sync.syncNow().onFailure { e -> toast(e.message ?: "Ошибка") } }
        })

        SettingsGroup("Как синхронизировать") {
            SettingSwitch("Автоматически", state.autoSync, hint = "При открытии приложения и после закрытия книги") { v ->
                sync.store.update { it.copy(autoSync = v) }
            }
            SettingSwitch("Настройки и оформление", state.syncSettings, hint = "Одинаковый вид и опции чтения на всех телефонах") { v ->
                sync.store.update { it.copy(syncSettings = v) }
            }
        }

        SettingsGroup("Файл резервной копии") {
            Text(
                "Всё в одном файле: прогресс, заметки, словарь, настройки. Можно сохранить куда угодно и загрузить обратно " +
                    "или на другом телефоне.",
                color = AppColors.Faint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            SettingSwitch("Вместе с книгами", includeBooks, hint = "Файл станет больше, зато книги не нужно добавлять заново") {
                includeBooks = it
            }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineAction("Экспорт", Modifier.weight(1f), enabled = busyFile == null) {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportLauncher.launch("WikiFlerty-$date.wikiflerty")
                }
                OutlineAction("Импорт", Modifier.weight(1f), enabled = busyFile == null) {
                    importLauncher.launch(arrayOf("*/*"))
                }
            }
            busyFile?.let {
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = AppColors.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CloudCard(
    title: String,
    connected: Boolean,
    description: String,
    onDisconnect: () -> Unit,
    connect: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (connected) "Подключено" else "Не подключено",
                    color = if (connected) Good else AppColors.Faint,
                    fontSize = 12.sp,
                )
            }
            if (connected) {
                Text(
                    "Отключить",
                    color = Bad,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.liveClick(appCorner(8), sound = FeedbackSound.Close, onClick = onDisconnect).padding(8.dp),
                )
            }
        }
        Text(description, color = AppColors.Faint, fontSize = 12.sp, lineHeight = 17.sp)
        if (!connected) connect()
    }
}

@Composable
private fun DropboxCard(
    connected: Boolean,
    savedKey: String,
    sync: SyncManager,
    onError: (String) -> Unit,
    onConnected: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var appKey by remember(savedKey) { mutableStateOf(savedKey) }
    var verifier by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    CloudCard(
        title = "Dropbox",
        connected = connected,
        description = "Файл хранится в папке «Приложения/WikiFlerty» в твоём Dropbox.",
        onDisconnect = { sync.disconnectDropbox() },
    ) {
        InputField(appKey, { appKey = it.trim() }, "App key из консоли Dropbox")
        ActionButton("1. Открыть Dropbox и разрешить доступ", enabled = appKey.isNotBlank()) {
            val (v, challenge) = DropboxStore.newVerifier()
            verifier = v
            runCatching { uri.openUri(DropboxStore.authorizeUrl(appKey, challenge)) }
        }
        AnimatedVisibility(visible = verifier != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("После «Разрешить» Dropbox покажет код. Скопируй его сюда:", color = AppColors.Muted, fontSize = 13.sp)
                InputField(code, { code = it.trim() }, "Код из Dropbox")
                ActionButton(if (working) "Подключаю…" else "2. Готово", enabled = code.isNotBlank() && !working) {
                    val v = verifier ?: return@ActionButton
                    scope.launch {
                        working = true
                        runCatching { DropboxStore.exchangeCode(appKey, code, v) }
                            .onSuccess { token ->
                                sync.saveDropbox(appKey, token)
                                verifier = null
                                code = ""
                                onConnected()
                            }
                            .onFailure { onError(it.message ?: "Dropbox: не удалось подключить") }
                        working = false
                    }
                }
            }
        }
    }
}

@Composable
private fun InputField(value: String, onChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = AppColors.Text, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(AppColors.Accent),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(AppColors.Background.copy(alpha = 0.6f), appCorner(12))
            .border(1.dp, AppColors.Line, appCorner(12))
            .padding(horizontal = 12.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = AppColors.Faint, fontSize = 14.sp)
                inner()
            }
        },
    )
}

@Composable
private fun ActionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = appCorner(12)
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .liveClick(shape, sound = FeedbackSound.Open, enabled = enabled, onClick = onClick)
            .background(if (enabled) AppColors.AccentTintStrong else AppColors.Field)
            .border(1.dp, if (enabled) AppColors.Accent.copy(alpha = 0.4f) else AppColors.Line, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) AppColors.Accent else AppColors.Faint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlineAction(text: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val shape = appCorner(12)
    Box(
        modifier
            .height(46.dp)
            .liveClick(shape, sound = FeedbackSound.Open, enabled = enabled, onClick = onClick)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
