package com.flerty.wikiflerty.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FocusMode {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private var applied = false
    private var previous = NotificationManager.INTERRUPTION_FILTER_ALL

    private fun prefs(context: Context) = context.getSharedPreferences("focus", Context.MODE_PRIVATE)

    fun load(context: Context) {
        _enabled.value = prefs(context).getBoolean("enabled", false)
        applied = prefs(context).getBoolean("applied", false)
        previous = prefs(context).getInt("previous", NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun requestAccess(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        _enabled.value = value
        prefs(context).edit().putBoolean("enabled", value).apply()
    }

    fun update(context: Context, reading: Boolean) {
        if (reading && _enabled.value) activate(context) else deactivate(context)
    }

    private fun activate(context: Context) {
        if (applied || !hasAccess(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val current = manager.currentInterruptionFilter
        if (current != NotificationManager.INTERRUPTION_FILTER_ALL) return
        previous = current
        runCatching { manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }.onSuccess {
            applied = true
            prefs(context).edit().putBoolean("applied", true).putInt("previous", previous).apply()
        }
    }

    private fun deactivate(context: Context) {
        if (!applied) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager != null && hasAccess(context) && manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            runCatching { manager.setInterruptionFilter(previous) }
        }
        applied = false
        prefs(context).edit().putBoolean("applied", false).apply()
    }
}
