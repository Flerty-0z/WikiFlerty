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
        runCatching {
            val names = context.assets.list("studio/audio")?.sorted().orEmpty()
            if (names.isNotEmpty() && app.audiobooks.books.value.isEmpty()) {
                val adir = File(context.filesDir, "studio-audio").apply { mkdirs() }
                val uris = names.map { name ->
                    val f = File(adir, name)
                    context.assets.open("studio/audio/$name").use { input -> f.outputStream().use { input.copyTo(it) } }
                    Uri.fromFile(f)
                }
                app.audiobooks.importFiles(uris, "Тестовая аудиокнига")
            }
            val names2 = context.assets.list("studio/audio2")?.sorted().orEmpty()
            if (names2.isNotEmpty() && app.audiobooks.books.value.none { it.title.startsWith("Повести Белкина") }) {
                val adir = File(context.filesDir, "studio-audio2").apply { mkdirs() }
                val uris = names2.map { name ->
                    val f = File(adir, name)
                    context.assets.open("studio/audio2/$name").use { input -> f.outputStream().use { input.copyTo(it) } }
                    Uri.fromFile(f)
                }
                app.audiobooks.importFiles(uris, "Повести Белкина")?.let { ab ->
                    app.library.books.value.firstOrNull { it.title.startsWith("Повести Белкина") }?.let { tb -> app.audiobooks.update(ab.id) { it.copy(bookId = tb.id) } }
                }
            }
            val names3 = context.assets.list("studio/audio3")?.sorted().orEmpty()
            if (names3.isNotEmpty() && app.audiobooks.books.value.none { it.title.startsWith("Белкин в двух частях") }) {
                val adir = File(context.filesDir, "studio-audio3").apply { mkdirs() }
                val uris = names3.map { name ->
                    val f = File(adir, name)
                    context.assets.open("studio/audio3/$name").use { input -> f.outputStream().use { input.copyTo(it) } }
                    Uri.fromFile(f)
                }
                app.audiobooks.importFiles(uris, "Белкин в двух частях")?.let { ab ->
                    app.library.books.value.firstOrNull { it.title.startsWith("Повести Белкина") }?.let { tb -> app.audiobooks.update(ab.id) { it.copy(bookId = tb.id) } }
                }
            }
        }.onFailure { android.util.Log.w(StudioDriver.TAG, "audio seed failed", it) }
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
        if (scene == "ttsjump" || scene == "memsoak") (app as App).readerSettings.update { it.copy(ttsEngine = com.flerty.wikiflerty.tts.NeuralVoices.SILENT) }
        if (scene == "community") communityDemo(app as App)
        val memory = scope.launch {
            val rt = Runtime.getRuntime()
            val began = SystemClock.uptimeMillis()
            while (true) {
                val used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
                val native = android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576
                log("MEM t=${(SystemClock.uptimeMillis() - began) / 1000}s java=${used}MB max=${rt.maxMemory() / 1_048_576}MB native=${native}MB threads=${Thread.activeCount()}")
                delay(5000)
            }
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
                    memory.cancel()
                    if (scene == "memsoak") {
                        runCatching {
                            Runtime.getRuntime().gc()
                            Thread.sleep(800)
                            Runtime.getRuntime().gc()
                            val rt = Runtime.getRuntime()
                            log("MEM final java=${(rt.totalMemory() - rt.freeMemory()) / 1_048_576}MB")
                            android.os.Debug.dumpHprofData(File(dir, "heap.hprof").absolutePath)
                            log("heap dumped ${File(dir, "heap.hprof").length() / 1_048_576}MB")
                        }.onFailure { log("heap dump failed: $it") }
                    }
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

private fun communityDemo(app: App) {
    val now = System.currentTimeMillis()
    fun m(i: Int, login: String, text: String, ago: Long, admin: Boolean = false, reading: String = "", reply: Pair<Int, String>? = null, quote: com.flerty.wikiflerty.sync.Community.Quote? = null, book: com.flerty.wikiflerty.sync.Community.BookRef? = null, reacts: Map<String, List<String>> = emptyMap()) =
        com.flerty.wikiflerty.sync.Community.Msg((now - ago) * 1000 + i, i.toLong(), "general", login, admin, text, now - ago, reading, reply?.let { com.flerty.wikiflerty.sync.Community.Ref((now - 3_600_000L) * 1000 + it.first, it.second, "Кто дочитал «Дракулу»?") }, quote, book, reacts, false)
    val msgs = listOf(
        m(1, "lisa_reads", "Кто дочитал «Дракулу»? Концовка мне показалась слишком быстрой", 3_600_000, reading = "Дракула"),
        m(2, "mark_t", "Согласен, но дневниковый формат держит до конца", 3_500_000, reading = "Три мушкетёра", reacts = mapOf("thumb" to listOf("a", "b", "c", "d"), "heart" to listOf("e", "f"))),
        m(3, "f1erty0z", "Друзья, в чате можно делиться цитатами прямо из книги — выделите текст и нажмите «В чат»", 1_800_000, admin = true, reacts = mapOf("heart" to List(18) { "u$it" }, "flame" to List(7) { "v$it" })),
        m(4, "lisa_reads", "Попробовала, красиво!", 1_200_000, reading = "Мастер и Маргарита", quote = com.flerty.wikiflerty.sync.Community.Quote("Рукописи не горят.", "Мастер и Маргарита", "М. Булгаков", "Глава 24"), reacts = mapOf("heart" to List(9) { "w$it" }, "mark" to listOf("x", "y", "z"))),
        m(5, "flerty", "А мне посоветовали вот это, кто читал?", 240_000, reading = "Шерлок Холмс", book = com.flerty.wikiflerty.sync.Community.BookRef("Остров сокровищ", "Р. Л. Стивенсон", 3)),
        m(6, "mark_t", "@flerty читал, затягивает с первой главы", 180_000, reading = "Три мушкетёра", reply = 5 to "flerty"),
        m(7, "danya", "Попробуй ещё «Убийство в Восточном экспрессе»", 120_000, reading = "Остров сокровищ"),
    )
    val rooms = listOf(
        com.flerty.wikiflerty.sync.Community.Room("general", "Общий", "chat", 12, com.flerty.wikiflerty.sync.Community.Preview(msgs.last().id, "danya", msgs.last().text, msgs.last().at)),
        com.flerty.wikiflerty.sync.Community.Room("reading", "Что читаю", "book", 5, com.flerty.wikiflerty.sync.Community.Preview(1, "mark_t", "Начал «Трёх мушкетёров», пока бодро", now - 1_500_000)),
        com.flerty.wikiflerty.sync.Community.Room("advice", "Посоветуйте книгу", "search", 3, com.flerty.wikiflerty.sync.Community.Preview(2, "sova_night", "Что-то похожее на «Шерлока»?", now - 2_400_000)),
        com.flerty.wikiflerty.sync.Community.Room("audio", "Аудиокниги", "head", 2, com.flerty.wikiflerty.sync.Community.Preview(3, "danya", "Слушаю «Остров сокровищ» на 1.25×", now - 5_400_000)),
        com.flerty.wikiflerty.sync.Community.Room("comics", "Комиксы", "comic", 1, com.flerty.wikiflerty.sync.Community.Preview(4, "alex_k", "«Маленький Немо» — шедевр", now - 9_000_000)),
        com.flerty.wikiflerty.sync.Community.Room("ideas", "Идеи для WikiFlerty", "bulb", 4, com.flerty.wikiflerty.sync.Community.Preview(5, "knigoman", "Тёмная тема для словаря", now - 20_000_000)),
    )
    val state = com.flerty.wikiflerty.sync.Community.State(
        loaded = true, on = true, slow = 10, online = 24, rooms = rooms,
        me = com.flerty.wikiflerty.sync.Community.Me(login = "flerty", accepted = true, admin = true, reports = 2, mentions = listOf(com.flerty.wikiflerty.sync.Community.Mention(9, "general", "mark_t", "@flerty отличная идея с цитатами", now - 300_000))),
        pin = "Вышло большое обновление: сообщество, аудиокниги и комиксы. Пишите идеи в «Идеях»!",
        latest = com.flerty.wikiflerty.sync.Community.Preview(msgs.last().id, "danya", msgs.last().text, msgs.last().at, "general", "Общий"),
        goal = com.flerty.wikiflerty.sync.Community.Goal("2026-11", 50000, 31240, 412),
        quote = com.flerty.wikiflerty.sync.Community.DayQuote(4, "general", "lisa_reads", "Рукописи не горят.", "Мастер и Маргарита", "М. Булгаков", 12),
        sprint = com.flerty.wikiflerty.sync.Community.Sprint(77, "lisa_reads", now - 240_000, now + 1_260_000, 25, listOf(com.flerty.wikiflerty.sync.Community.Runner("lisa_reads", 14), com.flerty.wikiflerty.sync.Community.Runner("mark_t", 11), com.flerty.wikiflerty.sync.Community.Runner("danya", 6)), false, now, false),
    )
    val profiles = listOf(com.flerty.wikiflerty.sync.Community.Profile("mark_t", false, now - 20L * 86_400_000, "Три мушкетёра", 38, 47, 31, 212, app.achievements.unlocked.value.keys.take(4).toList()))
    app.community.demo(state, com.flerty.wikiflerty.sync.Community.RoomView(msgs = msgs, loaded = true, online = 12, more = false), profiles)
}

