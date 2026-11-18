package com.flerty.wikiflerty.studio

import android.content.Context
import android.net.Uri
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ui.NewsRelease
import com.flerty.wikiflerty.ui.Tips
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object Studio {
    suspend fun prepare(context: Context): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as App
        var books = 0
        var files = 0
        runCatching {
            context.assets.list("studio/files")?.forEach { name ->
                if (StudioActivity.saveDownload(context, name)) files++
            }
        }
        val dir = File(context.cacheDir, "studio-books").apply { mkdirs() }
        context.assets.list("studio/library")?.sorted()?.forEach { name ->
            val f = File(dir, name)
            context.assets.open("studio/library/$name").use { input -> f.outputStream().use { input.copyTo(it) } }
            val result = app.library.import(Uri.fromFile(f))
            if (result.isSuccess) books++
            f.delete()
        }
        quiet(context)
        books to files
    }

    fun quiet(context: Context) {
        Tips.markTour(context)
        Tips.markReaderCoach(context)
        Tips.markRelease(context, NewsRelease)
        Tips.markVersion(context, Tips.versionCode(context))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    var filming = false
        private set

    fun film(context: Context, scene: String) {
        if (filming) return
        filming = true
        val app = context.applicationContext
        val dir = File(app.getExternalFilesDir(null), "wf").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        fun log(text: String) {
            android.util.Log.i(StudioDriver.TAG, text)
            File(dir, "app.log").appendText(text + "\n")
        }
        scope.launch {
            runCatching {
                val prepared = prepare(app)
                log("prepared books=${prepared.first} files=${prepared.second}")
                val scenario = scenario(app, scene) ?: error("no scenario $scene")
                var driver = StudioDriver.instance
                val end = SystemClock.uptimeMillis() + 20_000
                while (driver == null && SystemClock.uptimeMillis() < end) {
                    delay(250)
                    driver = StudioDriver.instance
                }
                driver ?: error("driver not running")
                quiet(app)
                app.startActivity(Intent().setClassName(app, "com.flerty.wikiflerty.ui.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                delay(3000)
                quiet(app)
                File(dir, "ready").writeText(SystemClock.uptimeMillis().toString())
                delay(800)
                driver.run(scenario, true) { ok, message ->
                    File(dir, "marks.json").writeText(
                        JSONObject()
                            .put("scene", scene)
                            .put("ok", ok)
                            .put("message", message)
                            .put("startedAt", driver.startedAt)
                            .put("width", app.resources.displayMetrics.widthPixels)
                            .put("height", app.resources.displayMetrics.heightPixels)
                            .put("density", app.resources.displayMetrics.density.toDouble())
                            .put("steps", driver.marks)
                            .toString(1),
                    )
                    if (!ok) File(dir, "screen.txt").writeText(driver.screen)
                    log("result ok=$ok $message")
                    File(dir, "done").writeText(if (ok) "ok" else "fail")
                    filming = false
                }
            }.onFailure {
                log("film failed: $it")
                File(dir, "done").writeText("error $it")
                filming = false
            }
        }
    }

    fun scenario(context: Context, id: String): Scenario? {
        val cached = File(context.filesDir, "scenarios/$id.json").takeIf { it.exists() }?.readText()
        val raw = cached ?: runCatching { context.assets.open("studio/scenarios/$id.json").bufferedReader().readText() }.getOrNull() ?: return null
        return Scenario.parse(id, raw)
    }
}
