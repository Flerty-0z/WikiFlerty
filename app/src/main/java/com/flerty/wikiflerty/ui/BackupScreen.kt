package com.flerty.wikiflerty.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.sync.Backup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun BackupContent() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val state by app.sync.store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var includeBooks by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = "Сохраняю…"
                runCatching { Backup.export(app, context, uri, includeBooks) }
                    .onSuccess { toast("Сохранено") }
                    .onFailure { toast(it.message ?: "Не удалось сохранить") }
                busy = null
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = "Загружаю…"
                runCatching { Backup.import(app, context, uri) }
                    .onSuccess { report ->
                        val books = Backup.lastImportedBooks
                        toast((if (books > 0) "Книг добавлено: $books. " else "") + report.describe())
                    }
                    .onFailure { toast(it.message ?: "Не удалось загрузить файл") }
                busy = null
            }
        }
    }

    HintNote("Всё в одном файле: прогресс, заметки, закладки, словарь, статистика и настройки.", AppIcons.Backup)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BackupButton("Экспорт", "Сохранить файл", Modifier.weight(1f), enabled = busy == null, filled = true) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            exportLauncher.launch("WikiFlerty-$date.wikiflerty")
        }
        BackupButton("Импорт", "Загрузить файл", Modifier.weight(1f), enabled = busy == null, filled = false) {
            importLauncher.launch(arrayOf("*/*"))
        }
    }
    busy?.let {
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            com.flerty.wikiflerty.ui.KitSpinner(Modifier.size(16.dp), AppColors.Accent)
            Spacer(Modifier.width(8.dp))
            Text(it, color = AppColors.Muted, fontSize = 13.sp)
        }
    }

    CloudBackupGroup()

    AutoBackupGroup()

    SettingsGroup("В файле") {
        SettingSwitch("Вместе с книгами", includeBooks, hint = "Файл будет больше", icon = AppIcons.Book) {
            includeBooks = it
        }
        SettingSwitch("Переносить настройки", state.syncSettings, hint = "Оформление и опции чтения", icon = AppIcons.Options) { v ->
            app.sync.store.update { it.copy(syncSettings = v) }
        }
    }
}

@Composable
private fun CloudBackupGroup() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val account = app.account
    val state by account.state.collectAsStateWithLifecycle()
    if (!account.available || !state.signedIn) return
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<com.flerty.wikiflerty.sync.Account.CloudBackup>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<com.flerty.wikiflerty.sync.Account.CloudBackup?>(null) }
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    fun reload() = scope.launch { account.backups().onSuccess { list = it } }
    LaunchedEffect(Unit) { reload() }
    val day = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val shown = remember { SimpleDateFormat("d MMMM", Locale("ru")) }
    TabbedFrame("Облачные копии", icon = AppIcons.Cloud, trailing = { CountBadge(list?.size ?: 0) }) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HintNote("Каждый день копия заметок, закладок, словаря, персонажей и статистики сама сохраняется в облако. Хранятся 7 последних дней", AppIcons.Cloud)
            list?.forEach { b ->
                val label = runCatching { shown.format(day.parse(b.day)!!) }.getOrDefault(b.day)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.Line.copy(alpha = 0.45f), appCorner(14))
                        .border(1.dp, AppColors.PanelBorder, appCorner(14))
                        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIcon(AppIcons.Calendar, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(cloudSize(b.size), color = AppColors.Faint, fontSize = 12.sp)
                    }
                    Box(
                        Modifier.size(40.dp).liveClick(CircleShape, sound = FeedbackSound.Open) { asking = b },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Rotate, "Восстановить", tint = AppColors.Accent, modifier = Modifier.size(19.dp)) }
                }
            }
            AppButton(if (busy) "Подожди…" else "Сохранить копию сейчас", primary = false, modifier = Modifier.fillMaxWidth(), labeled = true, sound = FeedbackSound.Sync) {
                if (busy) return@AppButton
                busy = true
                scope.launch {
                    account.backupNow().onSuccess { toast("Копия сохранена в облако"); reload() }.onFailure { toast(it.message ?: "Не получилось") }
                    busy = false
                }
            }
        }
    }
    asking?.let { b ->
        ConfirmSheet(
            title = "Восстановить копию?",
            text = "Вернутся заметки, закладки, слова и персонажи из копии от ${runCatching { shown.format(day.parse(b.day)!!) }.getOrDefault(b.day)}. То, что есть сейчас, не пропадёт.",
            confirm = "Восстановить",
            danger = false,
            sound = FeedbackSound.Undo,
            onConfirm = {
                scope.launch {
                    account.restore(b.day).onSuccess { toast(it.describe()) }.onFailure { toast(it.message ?: "Не получилось") }
                }
            },
            onDismiss = { asking = null },
        )
    }
}

@Composable
private fun AutoBackupGroup() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val auto = app.autoBackup
    val config by auto.config.collectAsStateWithLifecycle()
    val running by auto.running.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var enableAfterPick by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            auto.setFolder(uri)
            if (enableAfterPick) auto.update { it.copy(enabled = true) }
            scope.launch { auto.runIfDue() }
        }
        enableAfterPick = false
    }
    val folder = auto.folderName()

    SettingsGroup("Автокопия") {
        SettingSwitch("Автоматически", config.enabled, hint = "Сохранять копию без напоминаний", icon = AppIcons.Backup) { on ->
            if (on && config.folder == null) {
                enableAfterPick = true
                folderLauncher.launch(null)
            } else {
                auto.update { it.copy(enabled = on) }
                if (on) scope.launch { auto.runIfDue() }
            }
        }
        FolderRow(folder ?: "Не выбрана", onClick = { folderLauncher.launch(config.folderUri) })
        PickerRow(
            "Как часто",
            listOf(
                PickerOption("1", "Каждый день"),
                PickerOption("3", "Раз в 3 дня"),
                PickerOption("7", "Раз в неделю"),
            ),
            config.intervalDays.toString(),
            icon = AppIcons.Clock,
        ) { id -> auto.update { it.copy(intervalDays = id.toInt()) } }
        PickerRow(
            "Хранить копий",
            listOf(3, 5, 10, 20).map { PickerOption(it.toString(), "Последние $it") },
            config.keep.toString(),
            icon = AppIcons.Folder,
        ) { id -> auto.update { it.copy(keep = id.toInt()) } }
        SettingSwitch("Вместе с книгами", config.includeBooks, hint = "Копия будет больше", icon = AppIcons.Book) { v ->
            auto.update { it.copy(includeBooks = v) }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val status = when {
            running -> "Сохраняю копию…"
            config.lastError != null -> config.lastError
            config.lastTime > 0 -> "Последняя: " + SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru")).format(Date(config.lastTime))
            else -> "Копий ещё не было"
        }
        Text(
            status.orEmpty(),
            color = if (config.lastError != null && !running) AppColors.Bad else AppColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
            modifier = Modifier.weight(1f),
        )
        if (running) {
            com.flerty.wikiflerty.ui.KitSpinner(Modifier.size(18.dp), AppColors.Accent)
        } else if (config.folder != null) {
            ActionPill("Сейчас", AppIcons.Backup) {
                scope.launch {
                    auto.runNow()
                        .onSuccess { Toast.makeText(context, "Копия сохранена", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(AppIcons.Folder)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Папка", color = AppColors.Text, fontSize = 15.sp, maxLines = 1)
            Text(
                value,
                color = AppColors.Muted,
                maxLines = 1,
                softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            )
        }
        androidx.compose.material3.Icon(AppIcons.Chevron, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BackupButton(
    title: String,
    hint: String,
    modifier: Modifier,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val shape = appCorner(16)
    Column(
        modifier
            .heightIn(min = 68.dp)
            .liveClick(shape, sound = FeedbackSound.Open, enabled = enabled, onClick = onClick)
            .then(if (filled) Modifier.accentHighlight(shape) else Modifier.border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ActionLabel(title, if (filled) accentContent else AppColors.Accent, weight = FontWeight.Bold)
        Text(
            hint,
            color = if (filled) accentContent.copy(alpha = 0.75f) else AppColors.Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
