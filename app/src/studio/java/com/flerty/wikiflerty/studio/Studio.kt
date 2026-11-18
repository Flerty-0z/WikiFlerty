package com.flerty.wikiflerty.studio

import android.content.Context
import android.net.Uri
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ui.NewsRelease
import com.flerty.wikiflerty.ui.Tips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    fun scenario(context: Context, id: String): Scenario? {
        val cached = File(context.filesDir, "scenarios/$id.json").takeIf { it.exists() }?.readText()
        val raw = cached ?: runCatching { context.assets.open("studio/scenarios/$id.json").bufferedReader().readText() }.getOrNull() ?: return null
        return Scenario.parse(id, raw)
    }
}
