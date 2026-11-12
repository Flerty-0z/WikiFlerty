package com.flerty.wikiflerty.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object EInk {
    var on by mutableStateOf(false)
        private set
    var refresh by mutableIntStateOf(0)
        private set

    val refreshChoices = listOf(0, 5, 10, 20)

    private var prefs: SharedPreferences? = null
    private val listeners = mutableListOf<() -> Unit>()

    private val brands = listOf(
        "onyx", "boox", "pocketbook", "meebook", "haoqing", "likebook", "boyue", "bigme", "kobo", "tolino",
        "barnes", "nook", "inkpalm", "moaan", "supernote", "ratta", "dasung", "ireader", "hanvon", "bookeen",
        "inkbook", "e-ink", "eink", "remarkable", "mobiscribe", "fidibook", "xiaomi reader", "tagus",
    )

    fun init(context: Context) {
        val p = context.getSharedPreferences("eink", Context.MODE_PRIVATE)
        prefs = p
        on = p.getBoolean("on", false)
        refresh = p.getInt("refresh", 0)
    }

    fun listen(action: () -> Unit) {
        listeners += action
    }

    fun turn(value: Boolean) {
        if (on == value) return
        on = value
        prefs?.edit()?.putBoolean("on", value)?.putBoolean("asked", true)?.apply()
        listeners.forEach { runCatching { it() } }
    }

    fun refreshEvery(pages: Int) {
        refresh = pages.coerceAtLeast(0)
        prefs?.edit()?.putInt("refresh", refresh)?.apply()
    }

    val asked: Boolean get() = prefs?.getBoolean("asked", false) ?: true

    fun markAsked() {
        prefs?.edit()?.putBoolean("asked", true)?.apply()
    }

    fun likelyDevice(): Boolean {
        val name = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.PRODUCT).joinToString(" ").lowercase()
        return brands.any { it in name }
    }

    fun style(base: UiStyle): UiStyle = base.copy(
        kit = Kit.Ink.id,
        accent = 0xFF000000,
        corners = 0.5f,
        outline = true,
        buttons = ButtonLook.Filled.id,
        pressEffect = false,
        background = BackgroundKind.None.id,
        ambience = false,
        animations = false,
        mode = ThemeMode.Light.id,
        living = false,
        tunes = mapOf(Kit.Ink.id to KitTune(bookStyle = false, living = false, particles = false, props = false)),
    )
}
