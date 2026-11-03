package com.flerty.wikiflerty.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.system.ReadingReminder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val minutes by ReadingReminder.minutes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { ReadingReminder.load(context) }
    val start = minutes.takeIf { it >= 0 } ?: (21 * 60)
    var on by remember { mutableStateOf(ReadingReminder.load(context) >= 0) }
    val state = rememberTimePickerState(start / 60, start % 60, is24Hour = true)
    var pending by remember { mutableStateOf<Int?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        pending?.let { ReadingReminder.set(context, it) }
        pending = null
    }
    AppSheet(onDismiss, title = "Напоминание") {
        val close = sheetClose(onDismiss)
        SettingsGroup(null) {
            SettingSwitch("Напоминать", on, icon = AppIcons.Bell) { on = it }
        }
        AnimatedVisibility(visible = on, enter = expandVertically(Motion.tween(280)) + fadeIn(Motion.fade(220)), exit = shrinkVertically(Motion.tween(220)) + fadeOut(Motion.fade(160))) {
            Column(
                Modifier.fillMaxWidth().fieldSurface(appCorner(18)).padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(
                    state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = AppColors.Field,
                        selectorColor = AppColors.Accent,
                        timeSelectorSelectedContainerColor = AppColors.AccentTint,
                        timeSelectorUnselectedContainerColor = AppColors.Field,
                        timeSelectorSelectedContentColor = AppColors.Accent,
                        timeSelectorUnselectedContentColor = AppColors.Text,
                        clockDialSelectedContentColor = accentContent,
                        clockDialUnselectedContentColor = AppColors.Text,
                    ),
                )
            }
        }
        AppButton("Сохранить", primary = true, modifier = Modifier.fillMaxWidth()) {
            val value = if (on) state.hour * 60 + state.minute else -1
            if (value >= 0 && Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                pending = value
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ReadingReminder.set(context, value)
            }
            close()
        }
    }
}
