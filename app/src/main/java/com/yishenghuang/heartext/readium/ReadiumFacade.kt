package com.yishenghuang.heartext.readium

import android.content.Context
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Shared Readium services for industrial EPUB/PDF reading.
 * No LCP for MVP — local files only.
 */
class ReadiumFacade(context: Context) {
    private val appContext = context.applicationContext

    val httpClient = DefaultHttpClient()

    val assetRetriever = AssetRetriever(
        contentResolver = appContext.contentResolver,
        httpClient = httpClient
    )

    val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = appContext,
            assetRetriever = assetRetriever,
            httpClient = httpClient,
            pdfFactory = PdfiumDocumentFactory(appContext)
        )
    )
}
