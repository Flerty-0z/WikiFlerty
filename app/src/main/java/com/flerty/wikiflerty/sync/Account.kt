package com.flerty.wikiflerty.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.BuildConfig
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.data.Book
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Account(private val app: App) {
    companion object {
        const val DOMAIN = "sync.wikiflerty.ru"
    }

    data class CloudBook(val fp: String, val size: Long, val name: String, val ext: String, val at: Long)

    data class State(
        val login: String? = null,
        val used: Long = 0,
        val limit: Long = 0,
        val plan: String = "free",
        val until: Long = 0,
        val base: Long = 0,
        val payOn: Boolean = false,
        val books: List<CloudBook> = emptyList(),
        val uploadBooks: Boolean = true,
        val autoDownload: Boolean = true,
        val working: String? = null,
        val notice: String? = null,
        val loaded: Boolean = false,
    ) {
        val signedIn: Boolean get() = login != null
    }

    data class Activation(val plan: String, val limit: Long, val before: Long, val until: Long)

    val activated = MutableStateFlow<Activation?>(null)

    class ServerError(val code: String, message: String) : Exception(message)

    private val prefs = app.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<State> = _state.asStateFlow()
    private val bookLock = Mutex()
    private var bookJob: Job? = null
    private val downloading = MutableStateFlow<Set<String>>(emptySet())
    val busyBooks: StateFlow<Set<String>> = downloading.asStateFlow()

    val available: Boolean get() = BuildConfig.SYNC_HOST.isNotBlank()

    private val token: String? get() = prefs.getString("token", null)

    private fun restore(): State {
        val cached = prefs.getString("cache", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val base = State(
            login = prefs.getString("login", null),
            uploadBooks = prefs.getBoolean("uploadBooks", true),
            autoDownload = prefs.getBoolean("autoDownload", true),
        )
        return if (cached != null && base.login != null) parse(cached, base) else base
    }

    private fun parse(j: JSONObject, base: State = _state.value): State {
        val list = j.optJSONArray("books") ?: JSONArray()
        val books = (0 until list.length()).mapNotNull { i ->
            val b = list.optJSONObject(i) ?: return@mapNotNull null
            CloudBook(b.optString("fp"), b.optLong("size"), b.optString("name"), b.optString("ext"), b.optLong("at"))
        }.sortedByDescending { it.at }
        return base.copy(
            login = j.optString("login").ifBlank { base.login },
            used = j.optLong("used"),
            limit = j.optLong("limit"),
            plan = j.optString("plan", "free"),
            until = j.optLong("until"),
            base = j.optLong("base"),
            payOn = j.optBoolean("pay"),
            books = books,
            loaded = true,
        )
    }

    private fun applyAccount(j: JSONObject) {
        prefs.edit().putString("cache", j.toString()).apply()
        _state.value = parse(j)
    }

    @OptIn(FlowPreview::class)
    fun start() {
        if (!available) return
        app.appScope.launch {
            app.sync.store.state.map { s -> s.foreignBooks.map { it.fingerprint }.toSet() }
                .distinctUntilChanged()
                .debounce(4_000)
                .collect { poke(0) }
        }
        app.appScope.launch {
            delay(8_000)
            while (true) {
                if (_state.value.signedIn) {
                    runCatching { refresh() }
                    poke(0)
                }
                delay(30 * 60_000L)
            }
        }
    }

    fun setUploadBooks(on: Boolean) {
        prefs.edit().putBoolean("uploadBooks", on).apply()
        _state.value = _state.value.copy(uploadBooks = on)
        if (on) poke(0)
    }

    fun setAutoDownload(on: Boolean) {
        prefs.edit().putBoolean("autoDownload", on).apply()
        _state.value = _state.value.copy(autoDownload = on)
        if (on) poke(0)
    }

    private val socketFactory by lazy {
        val cert = app.resources.openRawResource(R.raw.sync_cert).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("sync", cert)
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        cert to SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }.socketFactory
    }

    @Volatile private var domainDownAt = 0L

    private fun open(path: String, viaDomain: Boolean, auth: Boolean): HttpsURLConnection {
        val c = if (viaDomain) {
            URL("https://$DOMAIN/v1/$path").openConnection() as HttpsURLConnection
        } else {
            val (pinned, factory) = socketFactory
            (URL("https://${BuildConfig.SYNC_HOST}/v1/$path").openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = factory
                hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session ->
                    runCatching { session.peerCertificates.firstOrNull() == pinned }.getOrDefault(false)
                }
            }
        }
        c.requestMethod = "POST"
        c.connectTimeout = if (viaDomain) 8_000 else 15_000
        c.readTimeout = 30_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (auth) token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        return c
    }

    private fun connect(path: String, auth: Boolean): HttpsURLConnection {
        val tryDomain = System.currentTimeMillis() - domainDownAt > 10 * 60_000L
        if (tryDomain) {
            val c = open(path, viaDomain = true, auth = auth)
            try {
                c.connect()
                return c
            } catch (e: IOException) {
                c.disconnect()
                domainDownAt = System.currentTimeMillis()
            }
        }
        val c = open(path, viaDomain = false, auth = auth)
        c.connect()
        return c
    }

    private fun call(path: String, body: JSONObject = JSONObject(), auth: Boolean = true): JSONObject {
        val c = try {
            connect(path, auth)
        } catch (e: IOException) {
            throw ServerError("network", "Нет связи с облаком. Проверь интернет")
        }
        try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val err = json.optString("error", "server")
                if (err == "auth") forgetLocal()
                throw ServerError(err, json.optString("message").ifBlank { "Сервер не ответил, попробуй позже" })
            }
            return json
        } catch (e: ServerError) {
            throw e
        } catch (e: IOException) {
            throw ServerError("network", "Нет связи с облаком. Проверь интернет")
        } finally {
            c.disconnect()
        }
    }

    private val readers = MutableStateFlow<Int?>(null)
    val readingNow: StateFlow<Int?> = readers.asStateFlow()

    private val presenceId: String by lazy {
        prefs.getString("presence", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("presence", it).apply() }
    }

    suspend fun ping(reading: Boolean) = withContext(Dispatchers.IO) {
        if (!available) return@withContext
        runCatching { call("ping", JSONObject().put("id", presenceId).put("reading", reading), auth = false) }
            .onSuccess { readers.value = it.optInt("reading") }
    }

    private fun saveSession(login: String, json: JSONObject) {
        prefs.edit().putString("token", json.getString("token")).putString("login", login).putString("sync", json.optString("sync")).apply()
        _state.value = _state.value.copy(login = login)
        json.optJSONObject("account")?.let { applyAccount(it) }
    }

    private suspend fun joinSync(code: String, created: Boolean) {
        val current = app.cloud.status.value.code
        if (current == code) {
            app.cloud.request(500)
            return
        }
        app.cloud.start(code, existing = !created)
    }

    suspend fun register(login: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = app.cloud.status.value.code
            val code = existing ?: app.cloud.newCode()
            val json = call("register", JSONObject().put("login", login).put("password", password).put("sync", code), auth = false)
            saveSession(json.optJSONObject("account")?.optString("login") ?: login.lowercase(), json)
            if (existing == null) joinSync(code, created = true) else app.cloud.request(500)
            poke(1_000)
            json.getString("recovery")
        }
    }

    suspend fun login(login: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = call("login", JSONObject().put("login", login).put("password", password), auth = false)
            saveSession(json.optJSONObject("account")?.optString("login") ?: login.lowercase(), json)
            joinSync(json.getString("sync"), created = false)
            poke(1_000)
        }
    }

    suspend fun recover(login: String, recovery: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = call("recover", JSONObject().put("login", login).put("recovery", recovery).put("password", password), auth = false)
            saveSession(json.optJSONObject("account")?.optString("login") ?: login.lowercase(), json)
            joinSync(json.getString("sync"), created = false)
            poke(1_000)
            json.getString("recovery")
        }
    }

    suspend fun changePassword(old: String, new: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { call("password", JSONObject().put("old", old).put("password", new)); Unit }
    }

    private var payWatch: Job? = null

    suspend fun pay(plan: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = call("pay/create", JSONObject().put("plan", plan)).getString("url")
            val before = _state.value.until
            val limitBefore = _state.value.limit
            payWatch?.cancel()
            payWatch = app.appScope.launch {
                repeat(120) {
                    delay(5_000)
                    refresh()
                    val now = _state.value
                    if (now.until != before && now.until > 0) {
                        activated.value = Activation(now.plan, now.limit, limitBefore, now.until)
                        return@launch
                    }
                }
            }
            url
        }
    }

    suspend fun refresh(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (token == null) return@runCatching
            applyAccount(call("me"))
            val code = app.cloud.status.value.code
            if (code == null) {
                val sync = prefs.getString("sync", null)
                if (sync != null) joinSync(sync, created = false)
            }
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { call("logout") }
        forgetLocal()
        app.cloud.stop()
    }

    suspend fun deleteAccount(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            call("delete", JSONObject().put("password", password))
            forgetLocal()
            app.cloud.forget()
            Unit
        }
    }

    private fun forgetLocal() {
        bookJob?.cancel()
        prefs.edit().remove("token").remove("login").remove("cache").remove("skip").remove("sync").apply()
        _state.value = State(uploadBooks = _state.value.uploadBooks, autoDownload = _state.value.autoDownload)
    }

    suspend fun deleteFromCloud(fp: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            applyAccount(call("books/delete", JSONObject().put("fp", fp)))
            prefs.edit().putStringSet("skip", (prefs.getStringSet("skip", emptySet()).orEmpty() + fp)).apply()
        }
    }

    fun uploadAgain(fp: String) {
        prefs.edit().putStringSet("skip", (prefs.getStringSet("skip", emptySet()).orEmpty() - fp)).apply()
        poke(0)
    }

    fun skipped(): Set<String> = prefs.getStringSet("skip", emptySet()).orEmpty()

    fun poke(delayMs: Long = 2_000) {
        if (!available || !_state.value.signedIn) return
        if (bookJob?.isActive == true) return
        bookJob = app.appScope.launch(Dispatchers.IO) {
            delay(delayMs)
            bookLock.withLock { runCatching { syncBooks() }.onFailure { e -> note((e as? ServerError)?.message) } }
        }
    }

    private fun note(text: String?) {
        _state.value = _state.value.copy(working = null, notice = text)
    }

    private fun unmetered(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        return !cm.isActiveNetworkMetered
    }

    fun sourceFile(book: Book): File? {
        if (book.paper || book.fingerprint == null || book.fingerprint.startsWith("paper:")) return null
        val dir = app.library.bookFile(book).parentFile ?: return null
        val file = if (book.pdf) File(dir, "${book.id}.pdf") else app.library.bookFile(book)
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    private suspend fun syncBooks() {
        if (token == null) return
        applyAccount(call("me"))
        var notice: String? = null
        if (_state.value.uploadBooks) {
            val inCloud = _state.value.books.map { it.fp }.toSet()
            val skip = skipped()
            val local = app.library.books.value.filter { b -> b.fingerprint != null && b.fingerprint !in inCloud && b.fingerprint !in skip }
            for (book in local) {
                val file = sourceFile(book) ?: continue
                if (file.length() > 200L * 1024 * 1024) continue
                if (_state.value.used + file.length() > _state.value.limit) {
                    notice = "Место в облаке закончилось. Часть книг осталась только на телефоне"
                    break
                }
                _state.value = _state.value.copy(working = "Загружаю «${book.title}»")
                try {
                    upload(book, file)
                } catch (e: ServerError) {
                    if (e.code in setOf("too_big", "format")) {
                        prefs.edit().putStringSet("skip", skipped() + book.fingerprint!!).apply()
                        continue
                    }
                    notice = e.message
                    break
                }
            }
        }
        if (notice == null && _state.value.autoDownload && unmetered()) {
            val localFps = app.library.books.value.mapNotNull { it.fingerprint }.toSet()
            val wanted = app.sync.store.state.value.foreignBooks.map { it.fingerprint }.toSet()
            for (cb in _state.value.books.filter { it.fp in wanted && it.fp !in localFps }) {
                try {
                    download(cb)
                } catch (e: ServerError) {
                    notice = e.message
                    break
                }
            }
        }
        _state.value = _state.value.copy(working = null, notice = notice)
    }

    private fun upload(book: Book, file: File) {
        val fp = book.fingerprint ?: return
        val ext = if (book.pdf) "pdf" else "epub"
        val res = call("books/upload", JSONObject().put("fp", fp).put("size", file.length()).put("name", book.title).put("ext", ext))
        if (res.optBoolean("exists")) return
        val c = URL(res.getString("url")).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "PUT"
            c.doOutput = true
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.setFixedLengthStreamingMode(file.length())
            c.setRequestProperty("Content-Type", if (ext == "pdf") "application/pdf" else "application/epub+zip")
            c.outputStream.use { out -> file.inputStream().use { it.copyTo(out, 64 * 1024) } }
            if (c.responseCode !in 200..299) throw ServerError("upload", "Не получилось загрузить книгу, попробую позже")
        } catch (e: IOException) {
            throw ServerError("network", "Связь прервалась, загрузка продолжится позже")
        } finally {
            c.disconnect()
        }
        applyAccount(call("books/commit", JSONObject().put("fp", fp)))
    }

    suspend fun downloadNow(fp: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cb = _state.value.books.firstOrNull { it.fp == fp } ?: throw ServerError("missing", "Этой книги нет в облаке")
            bookLock.withLock { download(cb) }
        }
    }

    private suspend fun download(cb: CloudBook) {
        downloading.value = downloading.value + cb.fp
        _state.value = _state.value.copy(working = "Скачиваю «${cb.name}»")
        val dir = File(app.cacheDir, "cloud").apply { mkdirs() }
        val safe = cb.name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(80).ifBlank { cb.fp.take(16) }
        val file = File(dir, "$safe.${cb.ext}")
        try {
            val res = call("books/url", JSONObject().put("fp", cb.fp))
            val c = URL(res.getString("url")).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 20_000
                c.readTimeout = 120_000
                if (c.responseCode !in 200..299) throw ServerError("download", "Не получилось скачать книгу, попробую позже")
                c.inputStream.use { input -> file.outputStream().use { input.copyTo(it, 64 * 1024) } }
            } catch (e: IOException) {
                throw ServerError("network", "Связь прервалась, скачивание продолжится позже")
            } finally {
                c.disconnect()
            }
            if (file.length() != cb.size) throw ServerError("download", "Книга скачалась не полностью, попробую позже")
            app.library.import(Uri.fromFile(file)).getOrElse { throw ServerError("import", it.message ?: "Не получилось открыть скачанную книгу") }
        } finally {
            file.delete()
            downloading.value = downloading.value - cb.fp
        }
    }
}
