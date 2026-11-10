package com.flerty.wikiflerty.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object CoverSearch {
    data class Candidate(val image: String, val title: String, val author: String, val description: String?)

    private fun get(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "WikiFlerty/1.0 (Android reader)")
        }
        return try {
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun google(title: String, author: String): List<Candidate> {
        val q = buildString {
            append("intitle:").append(title.trim())
            if (author.isNotBlank()) append(" inauthor:").append(author.trim().split(' ').last())
        }
        val body = get("https://www.googleapis.com/books/v1/volumes?maxResults=12&printType=books&q=" + enc(q))
            ?: return emptyList()
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val info = items.optJSONObject(i)?.optJSONObject("volumeInfo") ?: return@mapNotNull null
            val links = info.optJSONObject("imageLinks") ?: return@mapNotNull null
            val raw = links.optString("thumbnail").ifBlank { links.optString("smallThumbnail") }.ifBlank { return@mapNotNull null }
            val image = raw.replace("http://", "https://").replace("&edge=curl", "").replace(Regex("&zoom=\\d"), "&zoom=1") + "&fife=w600-h900"
            val authors = info.optJSONArray("authors")?.let { a -> (0 until a.length()).joinToString(", ") { a.optString(it) } }.orEmpty()
            Candidate(image, info.optString("title"), authors, info.optString("description").takeIf { it.isNotBlank() }?.let(::stripHtml))
        }
    }

    private fun openLibrary(title: String, author: String): List<Candidate> {
        val url = buildString {
            append("https://openlibrary.org/search.json?limit=12&fields=title,author_name,cover_i&title=").append(enc(title.trim()))
            if (author.isNotBlank()) append("&author=").append(enc(author.trim()))
        }
        val body = get(url) ?: return emptyList()
        val docs = JSONObject(body).optJSONArray("docs") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i ->
            val d = docs.optJSONObject(i) ?: return@mapNotNull null
            val id = d.optLong("cover_i", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val authors = d.optJSONArray("author_name")?.let { a -> (0 until a.length()).joinToString(", ") { a.optString(it) } }.orEmpty()
            Candidate("https://covers.openlibrary.org/b/id/$id-L.jpg", d.optString("title"), authors, null)
        }
    }

    private fun stripHtml(s: String) = android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()

    suspend fun find(title: String, author: String): List<Candidate> = coroutineScope {
        val a = async(Dispatchers.IO) { runCatching { google(title, author) }.getOrDefault(emptyList()) }
        val b = async(Dispatchers.IO) { runCatching { openLibrary(title, author) }.getOrDefault(emptyList()) }
        val (g, o) = awaitAll(a, b)
        val merged = mutableListOf<Candidate>()
        val max = maxOf(g.size, o.size)
        for (i in 0 until max) {
            g.getOrNull(i)?.let(merged::add)
            o.getOrNull(i)?.let(merged::add)
        }
        merged.distinctBy { it.image }.take(18)
    }

    suspend fun bitmap(url: String, sample: Int): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 15_000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val bytes = conn.inputStream.use { it.readBytes() }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.takeIf { it.width > 20 && it.height > 20 }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    suspend fun download(context: android.content.Context, url: String): Uri? = withContext(Dispatchers.IO) {
        val bmp = bitmap(url, 1) ?: return@withContext null
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "cover-web-${System.currentTimeMillis()}.jpg")
        runCatching { file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) } }.getOrNull() ?: return@withContext null
        Uri.fromFile(file)
    }
}

@Composable
fun CoverSearchSheet(title: String, author: String, onDismiss: () -> Unit, onPick: (Uri, String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    val results by produceState<List<CoverSearch.Candidate>?>(null, title, author) {
        value = runCatching { CoverSearch.find(title, author) }.getOrDefault(emptyList())
    }
    AppSheet(onDismiss, title = "Найти обложку") {
        val close = sheetClose(onDismiss)
        AnimatedContent(
            targetState = results?.let { if (it.isEmpty()) 1 else 2 } ?: 0,
            transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(140)) },
            label = "cover-search",
        ) { state ->
            when (state) {
                0 -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(34.dp)) }
                1 -> Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(AppIcons.Search, null, tint = AppColors.Faint, modifier = Modifier.size(30.dp))
                    Text("Ничего не нашлось", color = AppColors.Muted, fontSize = 15.sp)
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    results.orEmpty().chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { c ->
                                Box(Modifier.weight(1f)) {
                                    CandidateTile(c, busy == c.image) {
                                        if (busy != null) return@CandidateTile
                                        busy = c.image
                                        scope.launch {
                                            val uri = CoverSearch.download(context, c.image)
                                            busy = null
                                            if (uri != null) {
                                                onPick(uri, c.description)
                                                close()
                                            } else {
                                                android.widget.Toast.makeText(context, "Не удалось загрузить", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateTile(c: CoverSearch.Candidate, loading: Boolean, onClick: () -> Unit) {
    val shape = appCorner(10)
    val image by produceState<ImageBitmap?>(null, c.image) { value = CoverSearch.bitmap(c.image, 2)?.asImageBitmap() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .liveClick(shape, sound = FeedbackSound.Toggle, pressedScale = 0.94f, onClick = onClick)
                .clip(shape)
                .border(1.dp, AppColors.PanelBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.Crossfade(image, Modifier.fillMaxSize(), animationSpec = Motion.fade(220), label = "cand") { bmp ->
                if (bmp != null) Image(bmp, c.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().fieldSurface(shape), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(18.dp), AppColors.Faint) }
            }
            if (loading) Box(Modifier.fillMaxSize().clip(shape), contentAlignment = Alignment.Center) { KitSpinner(Modifier.size(26.dp)) }
        }
        Text(c.title, color = AppColors.Muted, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
