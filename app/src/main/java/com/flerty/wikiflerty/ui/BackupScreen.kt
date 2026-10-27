package com.flerty.wikiflerty.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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

    Text(
        "Всё в одном файле: прогресс, заметки, словарь, статистика и настройки.",
        color = AppColors.Muted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

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
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AppColors.Accent)
            Spacer(Modifier.width(8.dp))
            Text(it, color = AppColors.Muted, fontSize = 13.sp)
        }
    }

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
        Text(
            title,
            color = if (filled) accentContent else AppColors.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            hint,
            color = if (filled) accentContent.copy(alpha = 0.75f) else AppColors.Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
