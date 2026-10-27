package com.flerty.wikiflerty.reader

import android.content.Context
import java.io.File
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
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
        val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            return Result.failure(Exception("Не удалось открыть книгу (${it.message})"))
        }
        return Result.success(publication)
    }
}

class ReaderSession(
    val bookId: String,
    val publication: Publication,
    val initialLocator: Locator?,
)
