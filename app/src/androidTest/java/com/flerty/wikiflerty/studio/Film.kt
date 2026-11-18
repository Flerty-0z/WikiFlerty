package com.flerty.wikiflerty.studio

import android.app.UiAutomation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class Film {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ua: UiAutomation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val pkg = context.packageName
    private val out = "/sdcard/wf"
    private val local = File(context.getExternalFilesDir(null), "wf").apply { mkdirs() }

    @Volatile
    private var recording = false
    private var parts = 0
    private val starts = org.json.JSONArray()
    private var recordStart = 0L

    private fun sh(cmd: String): String {
        val pfd = ua.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun log(text: String) {
        Log.i(StudioDriver.TAG, text)
        File(local, "film.log").appendText(text + "\n")
    }

    @Test
    fun film() {
        val args = InstrumentationRegistry.getArguments()
        val scene = args.getString("scene") ?: "overview"
        log("scene=$scene sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL}")

        sh("mkdir -p $out")
        sh("input keyevent KEYCODE_WAKEUP")
        sh("wm dismiss-keyguard")
        sh("svc power stayon true")
        sh("settings put system screen_off_timeout 1800000")
        sh("settings put global window_animation_scale 1")
        sh("settings put global transition_animation_scale 1")
        sh("settings put global animator_duration_scale 1")
        sh("cmd statusbar collapse")
        if (Build.VERSION.SDK_INT >= 33) sh("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        demo()

        val prepared = runBlocking { Studio.prepare(context) }
        log("prepared books=${prepared.first} files=${prepared.second}")

        val service = "$pkg/${StudioDriver::class.java.name}"
        val current = sh("settings get secure enabled_accessibility_services").trim().takeUnless { it == "null" }.orEmpty()
        val list = (current.split(':').filter { it.isNotBlank() } + service).distinct().joinToString(":")
        sh("settings put secure enabled_accessibility_services $list")
        sh("settings put secure accessibility_enabled 1")
        val driver = waitFor(20_000) { StudioDriver.instance }
        assertTrue("Служба сценариев не запустилась", driver != null)

        val scenario = Studio.scenario(context, scene)
        assertTrue("Нет сценария $scene", scenario != null)

        context.startActivity(
            Intent().setClassName(context, "com.flerty.wikiflerty.ui.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        SystemClock.sleep(3500)
        Studio.quiet(context)

        startRecording()
        SystemClock.sleep(800)

        val done = CountDownLatch(1)
        var result = false to ""
        instrumentation.runOnMainSync {
            driver!!.run(scenario!!, true) { ok, message ->
                result = ok to message
                done.countDown()
            }
        }
        val finished = done.await(25, TimeUnit.MINUTES)
        SystemClock.sleep(1200)
        stopRecording()

        val d = driver!!
        File(local, "marks.json").writeText(
            JSONObject()
                .put("scene", scene)
                .put("ok", result.first)
                .put("message", result.second)
                .put("offset", d.startedAt - recordStart)
                .put("parts", parts)
                .put("starts", starts)
                .put("width", context.resources.displayMetrics.widthPixels)
                .put("height", context.resources.displayMetrics.heightPixels)
                .put("density", context.resources.displayMetrics.density.toDouble())
                .put("steps", d.marks)
                .toString(1),
        )
        if (!result.first) File(local, "screen.txt").writeText(d.screen.ifEmpty { d.screenText() })
        log("result ok=${result.first} ${result.second}")
        sh("cp -r ${local.absolutePath}/. $out/")
        sh("ls -la $out").let { log(it) }

        assertTrue("Сценарий не закончился", finished)
        assertTrue(result.second, result.first)
    }

    private fun <T> waitFor(ms: Long, get: () -> T?): T? {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) {
            get()?.let { return it }
            SystemClock.sleep(250)
        }
        return get()
    }

    private fun demo() {
        sh("settings put global sysui_demo_allowed 1")
        val base = "am broadcast -a com.android.systemui.demo -e command"
        sh("$base enter")
        sh("$base clock -e hhmm 0941")
        sh("$base battery -e level 100 -e plugged false")
        sh("$base network -e wifi show -e level 4 -e fully true")
        sh("$base network -e mobile show -e datatype none -e level 4")
        sh("$base notifications -e visible false")
    }

    private fun startRecording() {
        recording = true
        recordStart = SystemClock.uptimeMillis()
        thread(name = "screenrecord") {
            while (recording) {
                parts++
                val name = "$out/part%02d.mp4".format(parts)
                starts.put(SystemClock.uptimeMillis() - recordStart)
                log("record $name")
                sh("screenrecord --bit-rate 20000000 --time-limit 175 $name")
            }
        }
    }

    private fun stopRecording() {
        recording = false
        sh("pkill -INT screenrecord")
        SystemClock.sleep(2500)
    }
}
