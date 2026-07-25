package com.yishenghuang.heartext.readium

import android.app.Application
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.adapter.pdfium.navigator.PdfiumPreferencesEditor
import org.readium.adapter.pdfium.navigator.PdfiumSettings
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.shared.ExperimentalReadiumApi
import com.yishenghuang.heartext.data.PageTurnEffect
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.getOrElse
import java.io.File

typealias HearPdfNavigatorFactory =
    PdfNavigatorFactory<PdfiumSettings, PdfiumPreferences, PdfiumPreferencesEditor>

sealed class ReaderSession {
    abstract val bookId: String
    abstract val publication: Publication
    abstract val initialLocator: Locator?
    /** Listen / TTS is only for textual publications. */
    abstract val listeningEnabled: Boolean

    data class Epub(
        override val bookId: String,
        override val publication: Publication,
        override val initialLocator: Locator?,
        val navigatorFactory: EpubNavigatorFactory,
        val initialPreferences: EpubPreferences
    ) : ReaderSession() {
        override val listeningEnabled: Boolean = true
    }

    data class Pdf(
        override val bookId: String,
        override val publication: Publication,
        override val initialLocator: Locator?,
        val navigatorFactory: HearPdfNavigatorFactory,
        val initialPreferences: PdfiumPreferences
    ) : ReaderSession() {
        override val listeningEnabled: Boolean = false
    }
}

@OptIn(ExperimentalReadiumApi::class)
class ReaderSessionRepository(
    private val application: Application,
    private val readium: ReadiumFacade
) {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, ReaderSession>()

    operator fun get(bookId: String): ReaderSession? = sessions[bookId]

    suspend fun open(
        bookId: String,
        filePath: String,
        locatorJson: String?
    ): Result<ReaderSession> = mutex.withLock {
        sessions[bookId]?.let { return Result.success(it) }

        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure(IllegalStateException("Book file missing"))
        }

        val asset = readium.assetRetriever.retrieve(file).getOrElse {
            return Result.failure(IllegalStateException(it.message ?: "Unable to open asset"))
        }

        val publication = readium.publicationOpener.open(
            asset = asset,
            allowUserInteraction = false
        ).getOrElse {
            return Result.failure(IllegalStateException(it.message ?: "Unable to open publication"))
        }

        if (publication.isRestricted) {
            publication.close()
            return Result.failure(IllegalStateException("Protected publication is not supported yet"))
        }

        val initialLocator = locatorJson
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

        val session = when {
            publication.conformsTo(Publication.Profile.PDF) -> {
                ReaderSession.Pdf(
                    bookId = bookId,
                    publication = publication,
                    initialLocator = initialLocator,
                    navigatorFactory = PdfNavigatorFactory(
                        publication = publication,
                        pdfEngineProvider = PdfiumEngineProvider()
                    ),
                    initialPreferences = PdfiumPreferences(
                        fit = org.readium.r2.navigator.preferences.Fit.CONTAIN,
                        pageSpacing = 12.0,
                        scrollAxis = org.readium.r2.navigator.preferences.Axis.HORIZONTAL
                    )
                )
            }
            else -> {
                val epubPrefs = EpubPreferenceMapper.from(
                    // Defaults; live prefs applied in ReadiumHostFragment
                    com.yishenghuang.heartext.data.ReaderSettings(pageTurnEffect = PageTurnEffect.SLIDE)
                )
                ReaderSession.Epub(
                    bookId = bookId,
                    publication = publication,
                    initialLocator = initialLocator,
                    navigatorFactory = EpubNavigatorFactory(
                        publication = publication,
                        configuration = EpubNavigatorFactory.Configuration(
                            defaults = EpubDefaults(
                                scroll = false,
                                columnCount = ColumnCount.ONE,
                                pageMargins = 1.2
                            )
                        )
                    ),
                    initialPreferences = epubPrefs
                )
            }
        }

        sessions[bookId] = session
        Result.success(session)
    }

    suspend fun close(bookId: String) = mutex.withLock {
        sessions.remove(bookId)?.publication?.close()
    }

    suspend fun closeAll() = mutex.withLock {
        sessions.values.forEach { it.publication.close() }
        sessions.clear()
    }
}
