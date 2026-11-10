package com.flerty.wikiflerty.reader

import android.content.Context
import com.flerty.wikiflerty.data.convert.PdfPageMap
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

object PdfLocators {
    fun isPage(loc: Locator): Boolean = loc.href.toString() == "page"

    fun pageOf(loc: Locator, map: PdfPageMap?, total: Int): Int? {
        if (isPage(loc)) return loc.locations.position?.minus(1)?.coerceAtLeast(0)
        map?.pageOf(loc.href.toString(), loc.locations.progression ?: 0.0)?.let { return it }
        val p = loc.locations.totalProgression ?: return null
        return if (total > 1) (p * (total - 1)).roundToInt().coerceIn(0, total - 1) else 0
    }

    fun textLocator(map: PdfPageMap, page: Int): Locator? {
        val part = map.hrefOf(page) ?: return null
        val href = Url(part.href) ?: return null
        val span = (part.to - part.from + 1).coerceAtLeast(1)
        val p = page.coerceIn(part.from, part.to)
        val anchor = PdfPageMap.anchor(p)
        return Locator(
            href = href,
            mediaType = MediaType.XHTML,
            locations = Locator.Locations(
                fragments = listOf(anchor),
                progression = (p - part.from).toDouble() / span,
                totalProgression = if (map.pages > 1) p.toDouble() / (map.pages - 1) else 0.0,
                otherLocations = mapOf("cssSelector" to "#$anchor"),
            ),
        )
    }

    fun toText(loc: Locator?, map: PdfPageMap?): Locator? {
        if (loc == null || !isPage(loc)) return loc
        val page = loc.locations.position?.minus(1) ?: return null
        return map?.let { textLocator(it, page) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("pdf", Context.MODE_PRIVATE)

    fun original(context: Context, bookId: String): Boolean = prefs(context).getBoolean("orig_$bookId", false)

    fun setOriginal(context: Context, bookId: String, value: Boolean) {
        prefs(context).edit().putBoolean("orig_$bookId", value).apply()
    }
}
