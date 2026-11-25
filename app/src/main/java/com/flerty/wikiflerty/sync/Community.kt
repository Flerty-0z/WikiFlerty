package com.flerty.wikiflerty.sync

import android.content.Context
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.data.ReadStatus
import com.flerty.wikiflerty.ui.StatsCalc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Community(private val app: App) {
    data class Ref(val id: Long, val login: String, val text: String)
    data class Quote(val text: String, val book: String, val author: String, val chapter: String)
    data class BookRef(val title: String, val author: String, val tint: Int)
    data class Msg(
        val id: Long,
        val ver: Long,
        val room: String,
        val login: String,
        val admin: Boolean,
        val text: String,
        val at: Long,
        val reading: String,
        val reply: Ref?,
        val quote: Quote?,
        val book: BookRef?,
        val reacts: Map<String, List<String>>,
        val gone: Boolean,
        val edited: Boolean = false,
        val ch: Int = 0,
        val chTitle: String = "",
        val kind: String = "",
    )
    data class Locked(val ch: Int, val title: String, val count: Int)
    data class Goal(val month: String, val target: Int, val total: Int, val readers: Int)
    data class DayQuote(val id: Long, val room: String, val login: String, val text: String, val book: String, val author: String, val reacts: Int)
    data class Runner(val login: String, val pages: Int)
    data class Sprint(val id: Long, val by: String, val start: Long, val end: Long, val minutes: Int, val people: List<Runner>, val joined: Boolean, val now: Long, val done: Boolean) {
        private val skew = System.currentTimeMillis() - now
        val startLocal: Long get() = start + skew
        val endLocal: Long get() = end + skew
    }
    data class Idea(val id: Long, val login: String, val title: String, val text: String, val at: Long, val votes: Int, val voted: Boolean, val status: String, val note: String)
    data class Preview(val id: Long, val login: String, val text: String, val at: Long, val room: String = "", val roomTitle: String = "")
    data class Room(val id: String, val title: String, val icon: String, val online: Int, val last: Preview?)
    data class Mention(val id: Long, val room: String, val from: String, val text: String, val at: Long, val title: String = "")
    data class Me(
        val login: String? = null,
        val accepted: Boolean = false,
        val admin: Boolean = false,
        val muted: Long = 0,
        val banned: Boolean = false,
        val why: String = "",
        val mentions: List<Mention> = emptyList(),
        val reports: Int = 0,
    )
    data class State(
        val loaded: Boolean = false,
        val on: Boolean = true,
        val slow: Int = 0,
        val online: Int = 0,
        val rooms: List<Room> = emptyList(),
        val me: Me = Me(),
        val pin: String? = null,
        val latest: Preview? = null,
        val error: String? = null,
        val goal: Goal? = null,
        val quote: DayQuote? = null,
        val sprint: Sprint? = null,
    )
    data class Profile(
        val login: String,
        val admin: Boolean,
        val since: Long,
        val reading: String,
        val progress: Int,
        val books: Int,
        val streak: Int,
        val hours: Int,
        val achv: List<String>,
    )
    data class RoomView(
        val room: String = "",
        val msgs: List<Msg> = emptyList(),
        val loaded: Boolean = false,
        val online: Int = 0,
        val off: Boolean = false,
        val more: Boolean = true,
        val error: String? = null,
        val locked: List<Locked> = emptyList(),
        val upto: Int = Int.MAX_VALUE,
    )
    data class Report(val msg: Msg, val count: Int, val reasons: Map<String, Int>, val hidden: Boolean)
    data class Mod(
        val reports: List<Report> = emptyList(),
        val mutes: List<Triple<String, Long, String>> = emptyList(),
        val bans: List<Pair<String, String>> = emptyList(),
        val words: List<String> = emptyList(),
        val on: Boolean = true,
        val slow: Int = 0,
        val pin: String = "",
    )

    private val prefs = app.getSharedPreferences("community", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val _room = MutableStateFlow(RoomView())
    val room: StateFlow<RoomView> = _room.asStateFlow()
    private val _seen = MutableStateFlow(loadSeen())
    val seen: StateFlow<Map<String, Long>> = _seen.asStateFlow()
    val incoming = MutableStateFlow(0L)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pollJob: Job? = null
    private val viewer: String by lazy {
        prefs.getString("viewer", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("viewer", it).apply() }
    }

    val account get() = app.account

    @Volatile
    private var demoRoom: RoomView? = null
    private var demoProfiles: Map<String, Profile> = emptyMap()

    fun demo(state: State, room: RoomView, profiles: List<Profile>) {
        demoRoom = room
        demoProfiles = profiles.associateBy { it.login }
        _state.value = state
    }

    private fun loadSeen(): Map<String, Long> = prefs.all.filterKeys { it.startsWith("seen:") }.mapNotNull { (k, v) -> (v as? Long)?.let { k.removePrefix("seen:") to it } }.toMap()

    fun markSeen(room: String, id: Long) {
        if (id <= (_seen.value[room] ?: 0L)) return
        prefs.edit().putLong("seen:$room", id).apply()
        _seen.value = _seen.value + (room to id)
    }

    val mentionSeen: Long get() = prefs.getLong("mention", 0L)

    fun markMentions() {
        val top = _state.value.me.mentions.maxOfOrNull { it.id } ?: return
        prefs.edit().putLong("mention", top).apply()
        _state.value = _state.value.copy(me = _state.value.me.copy(mentions = emptyList()))
    }

    fun unread(room: Room, me: String?): Boolean {
        val last = room.last ?: return false
        if (last.login == me) return false
        return last.id > (_seen.value[room.id] ?: 0L)
    }

    private fun base(): JSONObject = JSONObject().put("viewer", viewer)

    suspend fun refresh(): Result<State> = withContext(Dispatchers.IO) {
        if (demoRoom != null) return@withContext Result.success(_state.value)
        runCatching {
            val j = account.chat("state", base().put("mention", mentionSeen))
            parseState(j).also { _state.value = it }
        }.onFailure { e -> _state.value = _state.value.copy(loaded = true, error = e.message) }
    }

    private fun parseState(j: JSONObject): State {
        val rooms = j.optJSONArray("rooms").objects().map { r ->
            val last = r.optJSONObject("last")?.let { Preview(it.optLong("id"), it.optString("login"), it.optString("text"), it.optLong("at")) }
            Room(r.optString("id"), r.optString("title"), r.optString("icon"), r.optInt("online"), last)
        }
        val me = j.optJSONObject("me")?.let { m ->
            Me(
                login = m.optString("login").ifBlank { null },
                accepted = m.optBoolean("accepted"),
                admin = m.optBoolean("admin"),
                muted = m.optLong("muted"),
                banned = m.optBoolean("banned"),
                why = m.optString("why"),
                mentions = m.optJSONArray("mentions").objects().map { Mention(it.optLong("id"), it.optString("room"), it.optString("from"), it.optString("text"), it.optLong("at"), it.optString("title")) },
                reports = m.optInt("reports"),
            )
        } ?: Me()
        val latest = j.optJSONObject("latest")?.let { Preview(it.optLong("id"), it.optString("login"), it.optString("text"), it.optLong("at"), it.optString("room"), it.optString("roomTitle")) }
        return State(
            loaded = true,
            on = j.optBoolean("on", true),
            slow = j.optInt("slow"),
            online = j.optInt("online"),
            rooms = rooms,
            me = me,
            pin = j.optJSONObject("pin")?.optString("text")?.ifBlank { null },
            latest = latest,
            goal = j.optJSONObject("goal")?.let { Goal(it.optString("month"), it.optInt("target"), it.optInt("total"), it.optInt("readers")) },
            quote = j.optJSONObject("quote")?.let { DayQuote(it.optLong("id"), it.optString("room"), it.optString("login"), it.optString("text"), it.optString("book"), it.optString("author"), it.optInt("reacts")) },
            sprint = j.optJSONObject("sprint")?.let(::parseSprint),
        )
    }

    private fun parseSprint(j: JSONObject): Sprint = Sprint(
        j.optLong("id"), j.optString("by"), j.optLong("start"), j.optLong("end"), j.optInt("minutes"),
        j.optJSONArray("people").objects().map { Runner(it.optString("login"), it.optInt("pages")) },
        j.optBoolean("joined"), j.optLong("now"), j.optBoolean("done"),
    )

    fun open(roomId: String, upto: Int = Int.MAX_VALUE) {
        pollJob?.cancel()
        demoRoom?.let { d ->
            _room.value = d.copy(room = roomId, msgs = d.msgs.map { it.copy(room = roomId) })
            return
        }
        _room.value = RoomView(room = roomId, upto = upto)
        pollJob = scope.launch {
            var ver = 0L
            var fails = 0
            val me = account.state.value.login
            while (isActive) {
                try {
                    val j = account.chat("poll", base().put("room", roomId).put("after", ver).put("upto", upto.coerceAtMost(100000)), timeout = 45_000)
                    fails = 0
                    if (j.optBoolean("off")) {
                        _room.value = _room.value.copy(off = true, loaded = true)
                        delay(15_000)
                        ver = 0
                        continue
                    }
                    val fresh = j.optJSONArray("msgs").objects().map(::parseMsg)
                    val first = ver == 0L
                    ver = j.optLong("ver", ver)
                    val cur = _room.value
                    val merged = if (first) fresh.filterNot { it.gone } else merge(cur.msgs, fresh)
                    val arrived = !first && fresh.any { m -> !m.gone && m.login != me && cur.msgs.none { it.id == m.id } }
                    val locked = j.optJSONArray("locked").objects().map { Locked(it.optInt("ch"), it.optString("title"), it.optInt("count")) }
                    _room.value = cur.copy(msgs = merged, loaded = true, online = j.optInt("online"), off = false, error = null, more = if (first) fresh.size >= 60 else cur.more, locked = locked)
                    if (arrived) incoming.value = System.currentTimeMillis()
                    merged.lastOrNull()?.let { markSeen(roomId, it.id) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (!isActive) break
                    fails++
                    _room.value = _room.value.copy(loaded = true, error = e.message)
                    delay(minOf(30_000L, 2_000L * fails))
                }
            }
        }
    }

    fun close() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun merge(old: List<Msg>, fresh: List<Msg>): List<Msg> {
        val map = LinkedHashMap<Long, Msg>()
        old.forEach { map[it.id] = it }
        fresh.forEach { m -> if (m.gone) map.remove(m.id) else map[m.id] = m }
        return map.values.sortedBy { it.id }
    }

    private fun put(m: Msg) {
        val cur = _room.value
        if (cur.room == m.room) _room.value = cur.copy(msgs = merge(cur.msgs, listOf(m)))
    }

    suspend fun older(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cur = _room.value
            val first = cur.msgs.firstOrNull()?.id ?: return@runCatching
            val j = account.chat("history", base().put("room", cur.room).put("before", first).put("upto", cur.upto.coerceAtMost(100000)))
            val msgs = j.optJSONArray("msgs").objects().map(::parseMsg)
            val now = _room.value
            if (now.room == cur.room) _room.value = now.copy(msgs = merge(msgs, now.msgs), more = j.optBoolean("more"))
        }
    }

    suspend fun send(room: String, text: String, reply: Long?, quote: Quote? = null, book: BookRef? = null, chapter: Pair<Int, String>? = null, thread: Pair<String, String>? = null): Result<Msg> = withContext(Dispatchers.IO) {
        if (demoRoom != null) {
            val me = account.state.value.login ?: "flerty"
            val r = reply?.let { id -> _room.value.msgs.firstOrNull { it.id == id } }?.let { Ref(it.id, it.login, it.text.take(80)) }
            val m = Msg(System.currentTimeMillis() * 1000, 0, room, me, false, text, System.currentTimeMillis(), "", r, quote, book, emptyMap(), false)
            put(m)
            return@withContext Result.success(m)
        }
        runCatching {
            val body = base().put("room", room).put("text", text)
            if (reply != null) body.put("reply", reply)
            if (quote != null) body.put("quote", JSONObject().put("text", quote.text).put("book", quote.book).put("author", quote.author).put("chapter", quote.chapter))
            if (book != null) body.put("book", JSONObject().put("title", book.title).put("author", book.author).put("tint", book.tint))
            if (chapter != null) body.put("ch", chapter.first).put("chTitle", chapter.second)
            if (thread != null) body.put("title", thread.first).put("author", thread.second)
            parseMsg(account.chat("send", body).getJSONObject("msg")).also { put(it); markSeen(room, it.id) }
        }
    }

    suspend fun react(m: Msg, kind: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (demoRoom != null) {
            val me = account.state.value.login ?: "flerty"
            val list = m.reacts[kind].orEmpty()
            val next = if (me in list) list - me else list + me
            put(m.copy(reacts = (m.reacts + (kind to next)).filterValues { it.isNotEmpty() }))
            return@withContext Result.success(Unit)
        }
        runCatching { put(parseMsg(account.chat("react", base().put("room", m.room).put("id", m.id).put("kind", kind)).getJSONObject("msg"))) }
    }

    suspend fun report(m: Msg, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { account.chat("report", base().put("room", m.room).put("id", m.id).put("reason", reason)); Unit }
    }

    suspend fun accept(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            account.chat("accept", base())
            _state.value = _state.value.copy(me = _state.value.me.copy(accepted = true))
            shareProfile()
        }
    }

    suspend fun profile(login: String): Result<Profile> = withContext(Dispatchers.IO) {
        demoProfiles[login]?.let { return@withContext Result.success(it) }
        runCatching {
            val j = account.chat("profile", base().put("login", login))
            Profile(
                j.optString("login"), j.optBoolean("admin"), j.optLong("since"), j.optString("reading"), j.optInt("progress"),
                j.optInt("books"), j.optInt("streak"), j.optInt("hours"), j.optJSONArray("achv").strings(),
            )
        }
    }

    fun shareProfile() {
        if (account.state.value.login == null) return
        scope.launch {
            runCatching {
                val books = app.library.books.value
                val current = books.filter { it.status == ReadStatus.Reading || (it.progress > 0.0 && it.progress < 0.999) }.maxByOrNull { it.lastReadAt ?: 0L }
                val days = app.stats.days.value
                val perDay = StatsCalc.perDay(days)
                val streak = StatsCalc.streaks(perDay).first
                val hours = (perDay.values.sumOf { it.ms } / 3_600_000L).toInt()
                val achv = app.achievements.unlocked.value.entries.sortedByDescending { it.value }.take(4).map { it.key }
                val p = JSONObject()
                    .put("reading", current?.title.orEmpty())
                    .put("progress", ((current?.progress ?: 0.0) * 100).toInt())
                    .put("books", books.count { it.status == ReadStatus.Finished })
                    .put("streak", streak)
                    .put("hours", hours)
                    .put("achv", JSONArray(achv))
                account.chat("me", base().put("profile", p))
            }
        }
    }

    suspend fun moderation(): Result<Mod> = withContext(Dispatchers.IO) { runCatching { parseMod(account.chat("reports", base())) } }

    suspend fun admin(op: String, fill: JSONObject.() -> Unit = {}): Result<Mod> = withContext(Dispatchers.IO) {
        runCatching { parseMod(account.chat("admin", base().put("op", op).apply(fill))) }
    }

    private fun parseMod(j: JSONObject): Mod = Mod(
        reports = j.optJSONArray("reports").objects().map { r ->
            val reasons = r.optJSONObject("reasons")?.let { o -> o.keys().asSequence().associateWith { o.optInt(it) } }.orEmpty()
            Report(parseMsg(r.getJSONObject("msg")), r.optInt("count"), reasons, r.optString("state") == "hidden")
        },
        mutes = j.optJSONArray("mutes").objects().map { Triple(it.optString("login"), it.optLong("until"), it.optString("why")) },
        bans = j.optJSONArray("bans").objects().map { it.optString("login") to it.optString("why") },
        words = j.optJSONArray("words").strings(),
        on = j.optBoolean("on", true),
        slow = j.optInt("slow"),
        pin = j.optJSONObject("pin")?.optString("text").orEmpty(),
    )

    private fun parseMsg(j: JSONObject): Msg = Msg(
        id = j.optLong("id"),
        ver = j.optLong("ver"),
        room = j.optString("room"),
        login = j.optString("login"),
        admin = j.optBoolean("admin"),
        text = j.optString("text"),
        at = j.optLong("at"),
        reading = j.optString("reading"),
        reply = j.optJSONObject("reply")?.let { Ref(it.optLong("id"), it.optString("login"), it.optString("text")) },
        quote = j.optJSONObject("quote")?.let { Quote(it.optString("text"), it.optString("book"), it.optString("author"), it.optString("chapter")) },
        book = j.optJSONObject("book")?.let { BookRef(it.optString("title"), it.optString("author"), it.optInt("tint")) },
        reacts = j.optJSONObject("reacts")?.let { o -> o.keys().asSequence().associateWith { o.optJSONArray(it).strings() } }.orEmpty(),
        gone = j.optBoolean("gone"),
        edited = j.optBoolean("edited"),
        ch = j.optInt("ch"),
        chTitle = j.optString("chTitle"),
        kind = j.optString("kind"),
    )

    suspend fun edit(m: Msg, text: String): Result<Msg> = withContext(Dispatchers.IO) {
        runCatching { parseMsg(account.chat("edit", base().put("room", m.room).put("id", m.id).put("text", text)).getJSONObject("msg")).also { put(it) } }
    }

    suspend fun unsend(m: Msg): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            account.chat("unsend", base().put("room", m.room).put("id", m.id))
            val cur = _room.value
            _room.value = cur.copy(msgs = cur.msgs.filterNot { it.id == m.id })
        }
    }

    suspend fun search(room: String, q: String, upto: Int = Int.MAX_VALUE): Result<List<Msg>> = withContext(Dispatchers.IO) {
        runCatching { account.chat("search", base().put("room", room).put("q", q).put("upto", upto.coerceAtMost(100000))).optJSONArray("msgs").objects().map(::parseMsg) }
    }

    fun bookRoom(title: String, author: String): String {
        val key = title.trim().lowercase().replace(Regex("\\s+"), " ") + "|" + author.trim().lowercase().replace(Regex("\\s+"), " ")
        val hash = java.security.MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return "b:" + hash.take(16)
    }

    private val _sprint = MutableStateFlow<Sprint?>(null)
    val sprint: StateFlow<Sprint?> = _sprint.asStateFlow()

    suspend fun sprintOp(op: String, minutes: Int = 0, pages: Int = 0): Result<Sprint?> = withContext(Dispatchers.IO) {
        runCatching {
            val j = account.chat("sprint", base().put("op", op).put("minutes", minutes).put("pages", pages))
            j.optJSONObject("sprint")?.let(::parseSprint).also { sp -> _sprint.value = sp; _state.value = _state.value.copy(sprint = sp) }
        }
    }

    suspend fun sprintState(): Result<Sprint?> = withContext(Dispatchers.IO) {
        runCatching {
            account.chat("sprintState", base()).optJSONObject("sprint")?.let(::parseSprint).also { sp -> _sprint.value = sp; _state.value = _state.value.copy(sprint = sp) }
        }
    }

    private var sprintJob: Job? = null

    suspend fun startSprint(minutes: Int): Result<Sprint?> = sprintOp("start", minutes = minutes).onSuccess { sp -> sp?.let(::track) }

    suspend fun joinSprint(): Result<Sprint?> = sprintOp("join").onSuccess { sp -> sp?.let(::track) }

    suspend fun leaveSprint(): Result<Sprint?> = sprintOp("leave").onSuccess { sprintJob?.cancel() }

    fun sprintPages(): Int = (todayPages() - prefs.getInt("sprintBase", todayPages())).coerceAtLeast(0)

    private fun track(sp: Sprint) {
        if (!sp.joined || sp.done) return
        if (prefs.getLong("sprintId", 0L) != sp.id) prefs.edit().putLong("sprintId", sp.id).putInt("sprintBase", todayPages()).putBoolean("sprintStarted", false).apply()
        if (sprintJob?.isActive == true) return
        sprintJob = scope.launch {
            while (isActive) {
                val cur = _sprint.value ?: break
                if (cur.id != sp.id) break
                val now = System.currentTimeMillis()
                if (now < cur.startLocal) {
                    prefs.edit().putInt("sprintBase", todayPages()).apply()
                    delay(minOf(5_000L, cur.startLocal - now + 50))
                    continue
                }
                if (!prefs.getBoolean("sprintStarted", false)) prefs.edit().putBoolean("sprintStarted", true).apply()
                val last = now >= cur.endLocal
                runCatching { sprintOp("pages", pages = sprintPages()) }
                if (last) break
                delay(minOf(20_000L, (cur.endLocal - now).coerceAtLeast(1_000L)))
            }
        }
    }

    fun resumeSprint() {
        _sprint.value?.let(::track)
    }

    fun todayPages(): Int {
        val day = java.time.LocalDate.now().toString()
        return app.stats.days.value[day]?.values?.sumOf { it.pages } ?: 0
    }

    fun reportPages() {
        if (account.state.value.login == null || demoRoom != null) return
        val day = java.time.LocalDate.now().toString()
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("goalAt", 0L) < 10 * 60_000L) return
        val sentDay = prefs.getString("goalDay", "")
        val sent = if (sentDay == day) prefs.getInt("goalSent", 0) else 0
        val total = todayPages()
        val delta = total - sent
        if (delta <= 0) return
        prefs.edit().putLong("goalAt", now).apply()
        scope.launch {
            runCatching {
                account.chat("goal", base().put("pages", delta.coerceAtMost(600)))
                prefs.edit().putString("goalDay", day).putInt("goalSent", sent + delta.coerceAtMost(600)).apply()
            }
        }
    }

    suspend fun ideas(): Result<List<Idea>> = withContext(Dispatchers.IO) { runCatching { parseIdeas(account.chat("ideas", base())) } }

    suspend fun idea(op: String, fill: JSONObject.() -> Unit = {}): Result<List<Idea>> = withContext(Dispatchers.IO) {
        runCatching { parseIdeas(account.chat("idea", base().put("op", op).apply(fill))) }
    }

    private fun parseIdeas(j: JSONObject): List<Idea> = j.optJSONArray("ideas").objects().map {
        Idea(it.optLong("id"), it.optString("login"), it.optString("title"), it.optString("text"), it.optLong("at"), it.optInt("votes"), it.optBoolean("voted"), it.optString("status"), it.optString("note"))
    }

    fun pendingMentions(): List<Mention> {
        if (account.state.value.login == null) return emptyList()
        val after = maxOf(mentionSeen, prefs.getLong("notified", 0L))
        val j = account.chat("state", base().put("mention", after), timeout = 15_000)
        val list = j.optJSONObject("me")?.optJSONArray("mentions").objects().map { Mention(it.optLong("id"), it.optString("room"), it.optString("from"), it.optString("text"), it.optLong("at"), it.optString("title")) }
        list.maxOfOrNull { it.id }?.let { prefs.edit().putLong("notified", it).apply() }
        return list
    }

    data class Spot(val percent: Int, val chapter: String)

    fun rememberSpot(title: String, author: String, percent: Int, chapter: String) {
        val room = bookRoom(title, author)
        val p = percent.coerceIn(0, 100)
        val old = prefs.getString("spot:$room", null)
        val value = "$p|$chapter"
        if (old != value) prefs.edit().putString("spot:$room", value).putString("meta:$room", "$title|$author").apply()
    }

    fun spot(room: String): Spot? {
        val raw = prefs.getString("spot:$room", null) ?: return null
        return Spot(raw.substringBefore('|').toIntOrNull() ?: 0, raw.substringAfter('|', ""))
    }

    fun threadMeta(room: String): Pair<String, String>? {
        val raw = prefs.getString("meta:$room", null) ?: return null
        return raw.substringBefore('|') to raw.substringAfter('|', "")
    }

    fun spotForBook(title: String, author: String, progress: Double): Spot {
        return spot(bookRoom(title, author)) ?: Spot((progress * 100).toInt().coerceIn(0, 100), "")
    }

    var notifyOn: Boolean
        get() = prefs.getBoolean("notify", true)
        set(v) { prefs.edit().putBoolean("notify", v).apply() }

    var quietNight: Boolean
        get() = prefs.getBoolean("quiet", true)
        set(v) { prefs.edit().putBoolean("quiet", v).apply() }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
}
