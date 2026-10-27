package com.flerty.wikiflerty.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

data class ExportFormat(
    val id: String,
    val title: String,
    val hint: String,
    val fileName: String,
    val mime: String,
    val build: () -> String,
)

@Composable
fun ExportSheet(
    title: String,
    formats: List<ExportFormat>,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(formats.first().id) }
    val format = formats.firstOrNull { it.id == selected } ?: formats.first()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(format.build().toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            Toast.makeText(context, if (ok) "Сохранено" else "Не удалось сохранить", Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }

    AppSheet(onDismiss = onDismiss, title = title) {
        extra?.invoke()
        SettingsGroup(null) {
            PickerRow(
                label = "Формат",
                icon = AppIcons.Note,
                options = formats.map { PickerOption(it.id, it.title, it.hint) },
                selectedId = selected,
            ) { selected = it }
        }
        Text(format.hint, color = AppColors.Faint, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Отправить", primary = true, modifier = Modifier.weight(1f)) {
                shareFile(context, format.fileName, format.mime, format.build())
            }
            AppButton("Сохранить", primary = false, modifier = Modifier.weight(1f)) {
                runCatching { save.launch(format.fileName) }
            }
        }
    }
}

fun shareFile(context: Context, fileName: String, mime: String, content: String) {
    runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(if (mime.startsWith("text/")) "text/plain" else mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, fileName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "Не удалось отправить", Toast.LENGTH_SHORT).show()
    }
}

fun exportStamp(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
