package com.flerty.wikiflerty.reader

import android.content.Context
import java.io.File
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.contentServiceFactory
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingContainer
import org.readium.r2.shared.util.resource.map
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReadiumServices(context: Context) {
    private val appContext: Context = context.applicationContext
    private val httpClient = DefaultHttpClient()

    private val assetRetriever = AssetRetriever(
        contentResolver = context.contentResolver,
        httpClient = httpClient,
    )

    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )

    suspend fun open(file: File): Result<Publication> {
        val asset = assetRetriever.retrieve(file).getOrElse {
            return Result.failure(Exception("Формат файла не распознан (${it.message})"))
        }
        val publication = publicationOpener.open(
            asset,
            allowUserInteraction = false,
            onCreatePublication = {
                container = UserFontsContainer(TransformingContainer(container, ::boot), appContext)
                manifest = manifest.copy(readingOrder = manifest.readingOrder.map(::readable))
                servicesBuilder.contentServiceFactory = org.readium.r2.shared.publication.services.content.DefaultContentService.createFactory(
                    listOf(com.flerty.wikiflerty.tts.SafeContent),
                )
            },
        ).getOrElse {
            asset.close()
            return Result.failure(Exception("Не удалось открыть книгу (${it.message})"))
        }
        return Result.success(publication)
    }
}

internal const val BOOT = "<script>(function(){" +
    "var st=document.createElement('style');st.id='wf-boot';st.textContent='html{visibility:hidden !important}';" +
    "(document.head||document.documentElement).appendChild(st);" +
    "var wr=document.createElement('style');wr.textContent='body{overflow-wrap:break-word;word-wrap:break-word}" +
    "html.wf-solo:root:not(#wf-z) body img,html.wf-solo:root:not(#wf-z) body svg{width:100%!important;max-width:100%!important;height:auto!important;max-height:calc(100vh - 40px)!important;object-fit:contain!important;display:block!important;margin:0 auto!important}" +
    "html.wf-solo:root:not(#wf-z)[style*=readium-scroll-on] body img,html.wf-solo:root:not(#wf-z)[style*=readium-scroll-on] body svg{max-height:none!important}';(document.head||document.documentElement).appendChild(wr);" +
    "function solo(){try{var b=document.body;if(!b)return;var n=b.querySelectorAll('img,svg image').length;if(n===1){if(!((b.innerText||'').replace(/\\s+/g,'').length>2)){document.documentElement.classList.add('wf-solo');}}}catch(e){}}" +
    "function show(){var h=document.getElementById('wf-boot');if(h){h.parentNode.removeChild(h);}}" +
    "setTimeout(show,1500);" +
    "document.addEventListener('DOMContentLoaded',function(){" +
    "try{var b=window.WikiFlertySelection;if(b){if(b.boot){(0,eval)(b.boot());}}}catch(e){}" +
    "solo();show();});" +
    "})();</script>"

internal const val COMIC = "<style id='wf-comic'>" +
    "html:root:not(#wf-z){--RS__pageGutter:14px!important;--USER__pageMargins:1!important;--RS__maxLineLength:100vw!important}" +
    "html:root:not(#wf-z) body{max-width:none!important;width:auto!important}" +
    "html:root:not(#wf-z) body div.page{margin:0!important;padding:0!important}" +
    "html:root:not(#wf-z) body div.page img{width:100%!important;max-width:100%!important;max-height:calc(100vh - 40px)!important;height:auto!important;object-fit:contain!important}" +
    "html:root:not(#wf-z)[style*='readium-scroll-on'] body div.page{margin:0 0 4px!important}" +
    "html:root:not(#wf-z)[style*='readium-scroll-on'] body div.page img{width:100%!important;max-height:none!important;height:auto!important}" +
    "html:root:not(#wf-z)[style*='readium-scroll-on'] body{padding-top:28px!important;padding-bottom:28px!important}" +
    "</style>"

private class UserFontsContainer(
    private val inner: org.readium.r2.shared.util.data.Container<Resource>,
    private val context: Context,
) : org.readium.r2.shared.util.data.Container<Resource> {
    override val entries: Set<Url> get() = inner.entries

    override fun get(url: Url): Resource? {
        val text = url.toString()
        if (text.contains(UserFonts.PATH)) {
            UserFonts.file(context, text)?.let { return org.readium.r2.shared.util.file.FileResource(it) }
        }
        return inner[url]
    }

    override fun close() = inner.close()
}

private fun boot(url: Url, resource: Resource): Resource {
    val path = url.toString().substringBefore('?').lowercase()
    if (!(path.endsWith(".xhtml") || path.endsWith(".html") || path.endsWith(".htm"))) return resource
    return resource.map { bytes ->
        val text = String(bytes, Charsets.UTF_8)
        val at = text.indexOf("</head>", ignoreCase = true)
        if (at < 0 || text.contains("id='wf-boot'")) Try.success(bytes)
        else {
            val extra = if (text.contains("<div class=\"page\" id=\"page")) COMIC else ""
            Try.success((text.substring(0, at) + BOOT + extra + text.substring(at)).toByteArray(Charsets.UTF_8))
        }
    }
}

private fun readable(link: org.readium.r2.shared.publication.Link): org.readium.r2.shared.publication.Link {
    val type = link.mediaType
    if (type?.type == "image") return link
    if (type != null && (type.isHtml || type.isBitmap || type.isAudio || type.isVideo || type.matches(org.readium.r2.shared.util.mediatype.MediaType.PDF))) return link
    val path = link.href.toString().substringBefore('#').substringBefore('?').lowercase()
    val html = path.endsWith(".xhtml") || path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".xml") || type == null || type.subtype.contains("xml") || type.subtype.contains("html")
    return if (html) link.copy(mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML) else link
}

class ReaderSession(
    val bookId: String,
    val publication: Publication,
    val initialLocator: Locator?,
)
