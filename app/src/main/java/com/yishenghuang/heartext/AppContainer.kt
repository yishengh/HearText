package com.yishenghuang.heartext

import android.app.Application
import android.content.Context
import com.clerk.api.Clerk
import com.yishenghuang.heartext.data.AnnotationRepository
import com.yishenghuang.heartext.data.AppDatabase
import com.yishenghuang.heartext.data.AppLanguage
import com.yishenghuang.heartext.data.BookRepository
import com.yishenghuang.heartext.data.CatalogRepository
import com.yishenghuang.heartext.data.CloudSyncRepository
import com.yishenghuang.heartext.data.OfflineVoiceRepository
import com.yishenghuang.heartext.data.ReaderPreferences
import com.yishenghuang.heartext.network.AuthTokenProvider
import com.yishenghuang.heartext.network.HearTextApi
import com.yishenghuang.heartext.tts.OfflineTtsEngine
import com.yishenghuang.heartext.tts.PlaybackCoordinator
import com.yishenghuang.heartext.tts.SystemTtsEngine
import com.yishenghuang.heartext.tts.TtsController
import com.yishenghuang.heartext.readium.ReadiumFacade
import com.yishenghuang.heartext.readium.ReaderSessionRepository
import com.yishenghuang.heartext.util.CrashReporting
import com.yishenghuang.heartext.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class HearTextApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        val prefs = base.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("app_language", AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        val language = runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.SYSTEM)
        super.attachBaseContext(LocaleHelper.wrap(base, language))
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporting.init()
        val publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY
        if (publishableKey.isNotBlank()) {
            Clerk.initialize(this, publishableKey = publishableKey)
        }
        container = AppContainer(this)
        LocaleHelper.apply(container.appPreferences.language.value)
        com.yishenghuang.heartext.data.ReaderTypefaces.init(this)
    }
}

class AppContainer(app: Application) {
    internal val applicationScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

    val authTokenProvider = AuthTokenProvider()
    val api = HearTextApi(authTokenProvider)
    val database = AppDatabase.get(app)
    val coverStore = com.yishenghuang.heartext.data.CoverStore(app)
    val cloudSync = CloudSyncRepository(database.bookDao(), api, authTokenProvider, coverStore)
    val annotationRepository = AnnotationRepository(
        database.annotationDao(),
        api,
        authTokenProvider,
        database.bookDao()
    )
    val bookRepository = BookRepository(app, database.bookDao(), coverStore, cloudSync, database.annotationDao(), annotationRepository)
    val catalogRepository = CatalogRepository(
        app,
        api,
        authTokenProvider,
        database.bookDao(),
        coverStore
    )

    val offlineVoiceRepository = OfflineVoiceRepository(app, api, authTokenProvider)
    val readerPreferences = ReaderPreferences(app)
    val appPreferences = com.yishenghuang.heartext.data.AppPreferences(app)
    val fontStore = com.yishenghuang.heartext.data.FontStore(app)
    val readingStats = com.yishenghuang.heartext.data.ReadingStatsStore(app)
    val systemTts = SystemTtsEngine(app)
    val offlineTts = OfflineTtsEngine(app, offlineVoiceRepository)
    val ttsController = TtsController(app, applicationScope, systemTts, offlineTts)
    val playbackCoordinator = PlaybackCoordinator(
        app = app,
        scope = applicationScope,
        bookRepository = bookRepository,
        readerPreferences = readerPreferences,
        tts = ttsController
    )
    val readium = ReadiumFacade(app)
    val readerSessions = ReaderSessionRepository(app, readium)
}
