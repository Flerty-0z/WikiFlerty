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
    )
    data class Preview(val id: Long, val login: String, val text: String, val at: Long, val room: String = "", val roomTitle: String = "")
    data class Room(val id: String, val title: String, val icon: String, val online: Int, val last: Preview?)
    data class Mention(val id: Long, val room: String, val from: String, val text: String, val at: Long)
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
                mentions = m.optJSONArray("mentions").objects().map { Mention(it.optLong("id"), it.optString("room"), it.optString("from"), it.optString("text"), it.optLong("at")) },
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
        )
    }

    fun open(roomId: String) {
        pollJob?.cancel()
        demoRoom?.let { d ->
            _room.value = d.copy(room = roomId, msgs = d.msgs.map { it.copy(room = roomId) })
            return
        }
        _room.value = RoomView(room = roomId)
        pollJob = scope.launch {
            var ver = 0L
            var fails = 0
            val me = account.state.value.login
            while (isActive) {
                try {
                    val j = account.chat("poll", base().put("room", roomId).put("after", ver), timeout = 45_000)
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
                    _room.value = cur.copy(msgs = merged, loaded = true, online = j.optInt("online"), off = false, error = null, more = if (first) fresh.size >= 60 else cur.more)
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
            val j = account.chat("history", base().put("room", cur.room).put("before", first))
            val msgs = j.optJSONArray("msgs").objects().map(::parseMsg)
            val now = _room.value
            if (now.room == cur.room) _room.value = now.copy(msgs = merge(msgs, now.msgs), more = j.optBoolean("more"))
        }
    }

    suspend fun send(room: String, text: String, reply: Long?, quote: Quote? = null, book: BookRef? = null): Result<Msg> = withContext(Dispatchers.IO) {
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
    )

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
}
