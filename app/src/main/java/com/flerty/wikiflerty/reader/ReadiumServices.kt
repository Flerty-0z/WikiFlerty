package com.flerty.wikiflerty.reader

import android.content.Context
import java.io.File
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
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
            onCreatePublication = { container = TransformingContainer(container, ::boot) },
        ).getOrElse {
            asset.close()
            return Result.failure(Exception("Не удалось открыть книгу (${it.message})"))
        }
        return Result.success(publication)
    }
}

private const val BOOT = "<script>(function(){" +
    "var st=document.createElement('style');st.id='wf-boot';st.textContent='html{visibility:hidden !important}';" +
    "(document.head||document.documentElement).appendChild(st);" +
    "function show(){var h=document.getElementById('wf-boot');if(h){h.parentNode.removeChild(h);}}" +
    "setTimeout(show,1500);" +
    "document.addEventListener('DOMContentLoaded',function(){" +
    "try{var b=window.WikiFlertySelection;if(b){if(b.boot){(0,eval)(b.boot());}}}catch(e){}" +
    "show();});" +
    "})();</script>"

private fun boot(url: Url, resource: Resource): Resource {
    val path = url.toString().substringBefore('?').lowercase()
    if (!(path.endsWith(".xhtml") || path.endsWith(".html") || path.endsWith(".htm"))) return resource
    return resource.map { bytes ->
        val text = String(bytes, Charsets.UTF_8)
        val at = text.indexOf("</head>", ignoreCase = true)
        if (at < 0 || text.contains("id='wf-boot'")) Try.success(bytes)
        else Try.success((text.substring(0, at) + BOOT + text.substring(at)).toByteArray(Charsets.UTF_8))
    }
}

class ReaderSession(
    val bookId: String,
    val publication: Publication,
    val initialLocator: Locator?,
)
