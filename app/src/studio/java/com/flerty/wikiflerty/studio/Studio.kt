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
                val folder = if (name.endsWith(".ttf") || name.endsWith(".otf")) "Шрифты" else "WikiFlerty Studio"
                if (StudioActivity.saveDownload(context, name, folder)) files++
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
        runCatching { seed(app) }.onFailure { android.util.Log.w(StudioDriver.TAG, "seed failed", it) }
        quiet(context)
        books to files
    }

    private suspend fun seed(app: App) {
        val books = app.library.books.value
        fun book(title: String) = books.firstOrNull { it.title.contains(title, true) }
        withContext(Dispatchers.Main) {
            app.style.update { com.flerty.wikiflerty.ui.Kit.NewYear.applyTo(it.copy(mode = com.flerty.wikiflerty.ui.ThemeMode.Dark.id), false) }
        }
        listOf("Капитанская дочка", "Герой нашего времени", "Отцы и дети").forEachIndexed { i, t ->
            book(t)?.let { app.library.addToSeries(it.id, "Школьная классика", (i + 1).toDouble()) }
        }
        mapOf(
            "Капитанская дочка" to listOf("любимое", "перечитать"),
            "Белая гвардия" to listOf("перечитать", "война"),
            "Герой нашего времени" to listOf("любимое"),
            "Отцы и дети" to listOf("школа"),
            "Муму" to listOf("школа", "короткое"),
            "Вий" to listOf("мистика", "короткое"),
            "Гранатовый браслет" to listOf("любимое", "короткое"),
        ).forEach { (t, tags) -> book(t)?.let { app.library.setTags(it.id, tags) } }
        withContext(Dispatchers.Main) {
            app.libraryPrefs.update { it.copy(seriesShelves = listOf("Школьная классика"), tagShelves = listOf("любимое")) }
        }
        val kd = book("Капитанская дочка") ?: return
        app.dictionary.add("ментор", "Наставник и воспитатель: тот, кто учит, опекает и даёт советы.", "Другого ментора я и не желал.", kd.id, kd.title, null)
        app.dictionary.add("недоросль", "Дворянский юноша, ещё не поступивший на службу; в шутку — недоучка.", "Я жил недорослем, гоняя голубей.", kd.id, kd.title, null)
        app.dictionary.add("борзой", "Порода охотничьих собак: быстрые, поджарые, с узкой мордой.", "мог очень здраво судить о свойствах борзого кобеля", kd.id, kd.title, null)
        app.dictionary.add("надзор", "Присмотр и наблюдение за кем-то, контроль.", "Под его надзором на двенадцатом году выучился я русской грамоте", kd.id, kd.title, null)
        val sav = app.characters.save(kd.id, null, "Савельич", "Дядька Петруши, верный слуга семьи Гринёвых")
        app.characters.edit(sav.id) { it.copy(role = "Слуга и дядька Петра Гринёва", aliases = listOf("Архип Савельич", "дядька")) }
        val bop = app.characters.save(kd.id, null, "Бопре", "Француз-гувернёр, бывший парикмахер и солдат")
        app.characters.edit(bop.id) { it.copy(role = "Учитель Петруши", aliases = listOf("мусье", "француз")) }
    }

    fun calm(app: App) {
        val now = System.currentTimeMillis()
        app.achievements.merge(JSONObject().apply { com.flerty.wikiflerty.data.Achievements.all.forEach { put(it.id, now) } })
        while (true) {
            val q = app.achievements.queue.value
            if (q.isEmpty()) break
            app.achievements.dismiss(q.first().id, q.size)
        }
    }

    fun quiet(context: Context) {
        runCatching { calm(context.applicationContext as App) }
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
        com.flerty.wikiflerty.tts.ReadAloud.trace = { line -> log("TTS $line") }
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
                driver.run(scenario, false) { ok, message ->
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
                    if (driver.screen.isNotEmpty()) File(dir, "screen.txt").writeText(driver.screen)
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
