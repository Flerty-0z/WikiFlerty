package com.flerty.wikiflerty.sync

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SyncException(message: String) : Exception(message)

/** A place in the cloud where the one sync file lives. */
interface CloudStore {
    val title: String
    /** The sync file's text, or null if there is none yet. */
    suspend fun download(): String?
    suspend fun upload(text: String)
}

// ---------------------------------------------------------------- HTTP helpers

internal object Http {
    fun open(url: String, method: String, headers: Map<String, String>): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    fun send(connection: HttpURLConnection, body: ByteArray?) {
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
        }
    }

    fun code(connection: HttpURLConnection): Int = try {
        connection.responseCode
    } catch (e: java.io.IOException) {
        throw SyncException("Нет связи с сервером. Проверь интернет.")
    }

    fun text(connection: HttpURLConnection): String = connection.inputStream.bufferedReader().use { it.readText() }

    fun error(connection: HttpURLConnection, service: String): SyncException {
        val code = runCatching { connection.responseCode }.getOrDefault(0)
        val raw = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        val detail = runCatching {
            val j = JSONObject(raw)
            j.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: j.optString("error_summary").takeIf { it.isNotBlank() }
                ?: j.optString("error_description").takeIf { it.isNotBlank() }
        }.getOrNull() ?: raw.take(160)
        val hint = when (code) {
            401, 403 -> "$service: нет доступа, подключи заново"
            in 500..599 -> "$service сейчас недоступен"
            else -> "$service: ошибка $code"
        }
        return SyncException(if (detail.isNotBlank()) "$hint ($detail)" else hint)
    }

    fun form(vararg pairs: Pair<String, String>): ByteArray =
        pairs.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }.toByteArray()
}

// ---------------------------------------------------------------- Google Drive

/** Google sign-in for the hidden app folder of the user's Google Drive. */
object GoogleAuth {
    private val SCOPE = Scope("https://www.googleapis.com/auth/drive.appdata")

    fun request(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(SCOPE)).build()

    /** Asks Google for access. The result may need the user's consent screen first. */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request()).await()

    /** A fresh access token without showing anything, or an error if the user must sign in again. */
    suspend fun token(context: Context): String {
        val result = authorize(context)
        if (result.hasResolution()) throw SyncException("Google Drive: нужно войти заново (раздел «Синхронизация»)")
        return result.accessToken ?: throw SyncException("Google Drive: не удалось получить доступ")
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/** The sync file in the app's hidden folder on Google Drive (appDataFolder). */
class GoogleDriveStore(private val context: Context) : CloudStore {
    override val title = "Google Drive"
    private val name = Backup.CLOUD_FILE

    private suspend fun headers() = mapOf("Authorization" to "Bearer ${GoogleAuth.token(context)}")

    private suspend fun findId(): String? {
        val h = headers()
        return withContext(Dispatchers.IO) {
            val q = URLEncoder.encode("name='$name'", "UTF-8")
            val c = Http.open("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id)", "GET", h)
            try {
                if (Http.code(c) !in 200..299) throw Http.error(c, title)
                JSONObject(Http.text(c)).optJSONArray("files")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }
            } finally {
                c.disconnect()
            }
        }
    }

    override suspend fun download(): String? {
        val id = findId() ?: return null
        val h = headers()
        return withContext(Dispatchers.IO) {
            val c = Http.open("https://www.googleapis.com/drive/v3/files/$id?alt=media", "GET", h)
            try {
                if (Http.code(c) !in 200..299) throw Http.error(c, title)
                Http.text(c)
            } finally {
                c.disconnect()
            }
        }
    }

    override suspend fun upload(text: String) {
        val id = findId()
        val h = headers()
        withContext(Dispatchers.IO) {
            val c = if (id == null) {
                val boundary = "wikiflerty${System.currentTimeMillis()}"
                val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
                val body = buildString {
                    append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(meta.toString())
                    append("\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n")
                    append(text)
                    append("\r\n--$boundary--")
                }.toByteArray(Charsets.UTF_8)
                Http.open(
                    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
                    "POST",
                    h + ("Content-Type" to "multipart/related; boundary=$boundary"),
                ).also { Http.send(it, body) }
            } else {
                Http.open(
                    "https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media",
                    "POST",
                    h + mapOf("X-HTTP-Method-Override" to "PATCH", "Content-Type" to "application/json"),
                ).also { Http.send(it, text.toByteArray(Charsets.UTF_8)) }
            }
            try {
                if (Http.code(c) !in 200..299) throw Http.error(c, title)
            } finally {
                c.disconnect()
            }
        }
    }
}
