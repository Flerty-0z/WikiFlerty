package com.flerty.filmer

import android.app.UiAutomation
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class Film {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val self = instrumentation.context
    private val ua: UiAutomation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val pkg = "com.flerty.wikiflerty.studio"
    private val out = "/data/local/tmp/wf"
    private val appDir = "/sdcard/Android/data/$pkg/files/wf"
    private val local = File(instrumentation.targetContext.filesDir, "wf").apply { mkdirs() }

    @Volatile
    private var recording = false
    private val starts = JSONArray()
    private var parts = 0

    private fun sh(cmd: String): String {
        val pfd = ua.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun log(text: String) {
        Log.i("WFStudio", text)
        File(local, "film.log").appendText(text + "\n")
    }

    private fun put(name: String, file: File) {
        if (!file.exists()) return
        if (Build.VERSION.SDK_INT >= 31) {
            val fds = ua.executeShellCommandRw("tee $out/$name")
            ParcelFileDescriptor.AutoCloseOutputStream(fds[1]).use { o -> file.inputStream().use { it.copyTo(o) } }
            ParcelFileDescriptor.AutoCloseInputStream(fds[0]).use { it.readBytes() }
        } else {
            val ext = File(instrumentation.targetContext.getExternalFilesDir(null), "wf").apply { mkdirs() }
            file.copyTo(File(ext, name), overwrite = true)
            sh("cp ${ext.absolutePath}/$name $out/$name")
        }
    }

    @Test
    fun film() {
        val scene = InstrumentationRegistry.getArguments().getString("scene") ?: "overview"
        log("scene=$scene pkg=$pkg sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL}")

        sh("rm -rf $out")
        sh("mkdir -p $out")
        sh("input keyevent KEYCODE_WAKEUP")
        sh("wm dismiss-keyguard")
        sh("svc power stayon true")
        sh("settings put system screen_off_timeout 1800000")
        sh("settings put global window_animation_scale 1")
        sh("settings put global transition_animation_scale 1")
        sh("settings put global animator_duration_scale 1")
        sh("cmd statusbar collapse")
        sh("settings put system show_touches 1")
        if (Build.VERSION.SDK_INT >= 33) sh("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        demo()

        val service = "$pkg/com.flerty.wikiflerty.studio.StudioDriver"
        val current = sh("settings get secure enabled_accessibility_services").trim().takeUnless { it == "null" }.orEmpty()
        val list = (current.split(':').filter { it.isNotBlank() } + service).distinct().joinToString(":")
        sh("settings put secure enabled_accessibility_services $list")
        sh("settings put secure accessibility_enabled 1")
        log("accessibility: " + sh("settings get secure enabled_accessibility_services").trim())

        val recordStart = SystemClock.uptimeMillis()
        startRecording(recordStart)
        SystemClock.sleep(600)
        log(sh("am start -W -n $pkg/com.flerty.wikiflerty.studio.StudioActivity --es film $scene").trim())

        val deadline = SystemClock.uptimeMillis() + 22 * 60_000
        var done = ""
        while (SystemClock.uptimeMillis() < deadline) {
            done = sh("cat $appDir/done").trim().takeUnless { it.contains("No such file") }.orEmpty()
            if (done.isNotEmpty()) break
            SystemClock.sleep(1000)
        }
        SystemClock.sleep(1500)
        stopRecording()

        log("done=$done")
        log(sh("cat $appDir/app.log"))
        val raw = sh("cat $appDir/marks.json")
        if (raw.trim().startsWith("{")) {
            val marks = JSONObject(raw)
            val rel = JSONArray()
            for (i in 0 until starts.length()) rel.put(starts.getLong(i) - recordStart)
            marks.put("offset", marks.optLong("startedAt") - recordStart)
            marks.put("starts", rel)
            marks.put("parts", parts)
            File(local, "marks.json").writeText(marks.toString(1))
        }
        sh("cp $appDir/screen.txt $out/")
        sh("cp $appDir/app.log $out/")
        sh("cp $appDir/heap.hprof $out/")
        put("marks.json", File(local, "marks.json"))
        log(sh("ls -la $out"))
        put("film.log", File(local, "film.log"))

        assertTrue("Съёмка не закончилась", done.isNotEmpty())
        assertTrue("Сценарий: $done", done == "ok")
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

    private fun startRecording(recordStart: Long) {
        recording = true
        thread(name = "screenrecord") {
            while (recording) {
                parts++
                val name = "$out/part%02d.mp4".format(parts)
                starts.put(SystemClock.uptimeMillis())
                log("record $name at ${SystemClock.uptimeMillis() - recordStart}")
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
