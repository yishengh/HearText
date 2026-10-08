package com.yishenghuang.heartext.readium

import org.json.JSONObject
import org.readium.adapter.pdfium.navigator.migrateLegacyPdfiumLocator
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** Version the saved locator itself, so retries/reopening cannot migrate it twice. */
internal object PdfLocatorCodec {
    private const val VERSION = "heartextPdfiumLocatorVersion"

    fun encode(locator: Locator): String = locator.toJSON().apply {
        if (optString("type") == "application/pdf") put(VERSION, 1)
    }.toString()

    @OptIn(DelicateReadiumApi::class)
    suspend fun restore(publication: Publication, json: String?): Locator? {
        val objectValue = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val locator = runCatching { Locator.fromJSON(objectValue) }.getOrNull() ?: return null
        return if (publication.conformsTo(Publication.Profile.PDF) && objectValue.optInt(VERSION, 0) < 1) {
            publication.migrateLegacyPdfiumLocator(locator)
        } else locator
    }
}
