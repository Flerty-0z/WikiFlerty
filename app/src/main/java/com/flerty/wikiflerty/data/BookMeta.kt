package com.flerty.wikiflerty.data

import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import org.readium.r2.shared.publication.Publication

class FileMeta(
    val description: String = "",
    val facts: Map<String, String> = emptyMap(),
    val series: List<BookSeries> = emptyList(),
) {
    fun over(base: FileMeta): FileMeta = FileMeta(
        description.ifBlank { base.description },
        base.facts + facts.filterValues { it.isNotBlank() },
        series.ifEmpty { base.series },
    )
}

object BookMetaReader {
    const val Version = 1

    private val isbnShape = Regex("(97[89])?\\d{9}[\\dXx]")
    private val yearShape = Regex("(1[4-9]\\d\\d|20\\d\\d)")

    fun plain(html: String): String =
        runCatching { android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }.getOrDefault(html)
            .replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("\\s*\\n\\s*\\n\\s*"), "\n\n")
            .trim()

    fun languageName(code: String): String {
        val clean = code.trim()
        if (clean.isEmpty()) return ""
        val name = runCatching { Locale.forLanguageTag(clean).getDisplayLanguage(Locale("ru")) }.getOrDefault("")
        return name.ifBlank { clean }.replaceFirstChar { it.uppercase() }
    }

    fun isbn(raw: String?): String? {
        val digits = raw.orEmpty().substringAfterLast(':').replace(Regex("[\\s\\-–]"), "")
        return digits.takeIf { isbnShape.matches(it) }?.uppercase()
    }

    fun year(raw: String?): String? = raw?.let { yearShape.find(it)?.value }

    fun genres(raw: List<String>): List<String> = raw
        .flatMap { it.split(',', ';', '/', '|') }
        .map { it.trim() }
        .filter { it.length in 2..40 }
        .map { Fb2Genres.name(it) ?: it }
        .filterNot { it.matches(Regex("[a-z0-9]*_[a-z0-9_]*")) }
        .map { it.replaceFirstChar { c -> c.uppercase() } }
        .distinctBy { it.lowercase() }
        .take(8)

    fun read(publication: Publication, file: File?): FileMeta {
        val m = publication.metadata
        val facts = LinkedHashMap<String, String>()
        m.publishers.joinToString(", ") { it.name.trim() }.takeIf { it.isNotBlank() }?.let { facts[BookFacts.Publisher] = it }
        year(m.published?.toString()?.take(4))?.let { facts[BookFacts.Year] = it }
        m.languages.firstOrNull()?.let { languageName(it) }?.takeIf { it.isNotBlank() }?.let { facts[BookFacts.Language] = it }
        m.translators.joinToString(", ") { it.name.trim() }.takeIf { it.isNotBlank() }?.let { facts[BookFacts.Translator] = it }
        isbn(m.identifier)?.let { facts[BookFacts.Isbn] = it }
        genres(m.subjects.map { it.name }).takeIf { it.isNotEmpty() }?.let { facts[BookFacts.Genres] = it.joinToString("|") }
        val series = m.belongsToSeries
            .mapNotNull { c -> c.name.trim().takeIf { it.isNotEmpty() }?.let { BookSeries(it, c.position) } }
            .ifEmpty { file?.let { runCatching { calibreSeries(it) }.getOrNull() }.orEmpty() }
        return FileMeta(plain(m.description.orEmpty()), facts, series)
    }

    private fun attr(tag: String, name: String): String? =
        Regex("$name\\s*=\\s*([\"'])(.*?)\\1").find(tag)?.groupValues?.get(2)
            ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&apos;", "'")?.replace("&lt;", "<")?.replace("&gt;", ">")

    private fun calibreSeries(file: File): List<BookSeries> {
        ZipFile(file).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: return emptyList()
            val path = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(zip.getInputStream(container).bufferedReader().use { it.readText() })?.groupValues?.get(1)
                ?: return emptyList()
            val entry = zip.getEntry(path) ?: return emptyList()
            if (entry.size > 2_000_000) return emptyList()
            val opf = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            var name: String? = null
            var index: Double? = null
            Regex("<meta\\b[^>]*>").findAll(opf).forEach { tag ->
                when (attr(tag.value, "name")) {
                    "calibre:series" -> name = attr(tag.value, "content")?.trim()
                    "calibre:series_index" -> index = attr(tag.value, "content")?.trim()?.toDoubleOrNull()
                }
            }
            val found = name?.takeIf { it.isNotEmpty() } ?: return emptyList()
            return listOf(BookSeries(found, index?.takeIf { it > 0 }))
        }
    }
}

object Fb2Genres {
    private val names = mapOf(
        "sf" to "Научная фантастика", "sf_history" to "Альтернативная история", "sf_action" to "Боевая фантастика",
        "sf_epic" to "Эпическая фантастика", "sf_heroic" to "Героическая фантастика", "sf_detective" to "Детективная фантастика",
        "sf_cyberpunk" to "Киберпанк", "sf_space" to "Космическая фантастика", "sf_social" to "Социальная фантастика",
        "sf_horror" to "Ужасы", "sf_humor" to "Юмористическая фантастика", "sf_fantasy" to "Фэнтези",
        "sf_fantasy_city" to "Городское фэнтези", "sf_postapocalyptic" to "Постапокалипсис", "sf_mystic" to "Мистика",
        "sf_stimpank" to "Стимпанк", "sf_technofantasy" to "Технофэнтези", "sf_etc" to "Фантастика",
        "fantasy" to "Фэнтези", "dark_fantasy" to "Тёмное фэнтези", "historical_fantasy" to "Историческое фэнтези",
        "humor_fantasy" to "Юмористическое фэнтези", "popadanec" to "Попаданцы", "popadancy" to "Попаданцы",
        "litrpg" to "ЛитРПГ", "love_sf" to "Любовная фантастика", "love_fantasy" to "Любовное фэнтези",
        "det_classic" to "Классический детектив", "det_police" to "Полицейский детектив", "det_action" to "Боевик",
        "det_irony" to "Иронический детектив", "det_history" to "Исторический детектив", "det_espionage" to "Шпионский детектив",
        "det_crime" to "Криминальный детектив", "det_political" to "Политический детектив", "det_maniac" to "Маньяки",
        "det_hard" to "Крутой детектив", "thriller" to "Триллер", "detective" to "Детектив",
        "prose_classic" to "Классическая проза", "prose_history" to "Историческая проза", "prose_contemporary" to "Современная проза",
        "prose_counter" to "Контркультура", "prose_rus_classic" to "Русская классика", "prose_su_classics" to "Советская классика",
        "prose_military" to "Военная проза", "prose" to "Проза", "foreign_prose" to "Зарубежная проза",
        "love_contemporary" to "Современные любовные романы", "love_history" to "Исторические любовные романы",
        "love_detective" to "Остросюжетные любовные романы", "love_short" to "Короткие любовные романы",
        "love_erotica" to "Эротика", "love" to "Любовные романы",
        "adv_western" to "Вестерн", "adv_history" to "Исторические приключения", "adv_indian" to "Приключения про индейцев",
        "adv_maritime" to "Морские приключения", "adv_geo" to "Путешествия и география", "adv_animal" to "Природа и животные",
        "adventure" to "Приключения",
        "child_tale" to "Сказки", "child_verse" to "Детские стихи", "child_prose" to "Детская проза",
        "child_sf" to "Детская фантастика", "child_det" to "Детские детективы", "child_adv" to "Детские приключения",
        "child_education" to "Детская образовательная литература", "children" to "Детская литература",
        "poetry" to "Поэзия", "dramaturgy" to "Драматургия",
        "antique_ant" to "Античная литература", "antique_european" to "Европейская старинная литература",
        "antique_russian" to "Древнерусская литература", "antique_east" to "Древневосточная литература",
        "antique_myths" to "Мифы и легенды", "antique" to "Старинная литература",
        "sci_history" to "История", "sci_psychology" to "Психология", "sci_culture" to "Культурология",
        "sci_religion" to "Религиоведение", "sci_philosophy" to "Философия", "sci_politics" to "Политика",
        "sci_business" to "Деловая литература", "sci_juris" to "Юриспруденция", "sci_linguistic" to "Языкознание",
        "sci_medicine" to "Медицина", "sci_phys" to "Физика", "sci_math" to "Математика", "sci_chem" to "Химия",
        "sci_biology" to "Биология", "sci_tech" to "Техника", "science" to "Научная литература", "sci_popular" to "Научпоп",
        "comp_www" to "Интернет", "comp_programming" to "Программирование", "comp_hard" to "Компьютерное железо",
        "comp_soft" to "Программы", "comp_db" to "Базы данных", "comp_osnet" to "ОС и сети", "computers" to "Компьютеры",
        "ref_encyc" to "Энциклопедии", "ref_dict" to "Словари", "ref_ref" to "Справочники", "ref_guide" to "Руководства",
        "reference" to "Справочная литература",
        "nonf_biography" to "Биографии и мемуары", "nonf_publicism" to "Публицистика", "nonf_criticism" to "Критика",
        "nonfiction" to "Документальная литература", "design" to "Искусство и дизайн",
        "religion_rel" to "Религия", "religion_esoterics" to "Эзотерика", "religion_self" to "Самосовершенствование",
        "religion" to "Религия",
        "humor_anecdote" to "Анекдоты", "humor_prose" to "Юмористическая проза", "humor_verse" to "Юмористические стихи",
        "humor" to "Юмор",
        "home_cooking" to "Кулинария", "home_pets" to "Домашние животные", "home_crafts" to "Хобби и ремёсла",
        "home_entertain" to "Развлечения", "home_health" to "Здоровье", "home_garden" to "Сад и огород",
        "home_diy" to "Сделай сам", "home_sport" to "Спорт", "home_sex" to "Эротика и секс", "home" to "Дом и семья",
        "ya" to "Young Adult", "young_adult" to "Young Adult", "network_literature" to "Сетевая литература",
        "fanfiction" to "Фанфик", "comics" to "Комиксы", "manga" to "Манга",
    )

    fun name(code: String): String? = names[code.trim().lowercase()]
}
