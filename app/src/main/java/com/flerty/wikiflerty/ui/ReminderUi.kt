package com.flerty.wikiflerty.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.system.ReadingReminder

private val ReminderChoices = listOf(8 * 60, 12 * 60, 18 * 60, 20 * 60, 21 * 60, 22 * 60)

private fun clockLabel(m: Int) = "%02d:%02d".format(m / 60, m % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderRow() {
    val context = LocalContext.current
    val minutes by ReadingReminder.minutes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { ReadingReminder.load(context) }
    var custom by remember { mutableStateOf(false) }
    var pending by remember { mutableIntStateOf(-1) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        if (pending >= 0) ReadingReminder.set(context, pending)
        pending = -1
    }
    fun choose(m: Int) {
        if (m >= 0 && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pending = m
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ReadingReminder.set(context, m)
        }
    }
    Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        PickerRow(
            label = "Напоминание",
            icon = AppIcons.Bell,
            options = listOf(PickerOption("-1", "Выключено", "Без уведомлений", icon = AppIcons.Close)) +
                ReminderChoices.map { PickerOption(it.toString(), clockLabel(it), "Если сегодня ещё не читал", icon = AppIcons.Bell) } +
                listOfNotNull(
                    minutes.takeIf { it >= 0 && it !in ReminderChoices }?.let { PickerOption(it.toString(), clockLabel(it), "Своё время", icon = AppIcons.Bell) },
                    PickerOption(CUSTOM_ID, "Своё время", "Выбери любое время", icon = AppIcons.Clock),
                ),
            selectedId = minutes.coerceAtLeast(-1).toString(),
            searchable = false,
        ) { id -> if (id == CUSTOM_ID) custom = true else choose(id.toInt()) }
    }
    if (custom) {
        val start = minutes.takeIf { it >= 0 } ?: (21 * 60)
        val state = rememberTimePickerState(start / 60, start % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { custom = false },
            containerColor = AppColors.Panel,
            confirmButton = {
                TextButton(onClick = {
                    choose(state.hour * 60 + state.minute)
                    custom = false
                }) { Text("Готово", color = AppColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Отмена", color = AppColors.Muted) } },
            text = { TimePicker(state) },
        )
    }
}
