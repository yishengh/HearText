package com.yishenghuang.heartext.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yishenghuang.heartext.BookOverviewViewModel
import com.yishenghuang.heartext.CatalogDetailViewModel
import com.yishenghuang.heartext.CatalogPreviewViewModel
import com.yishenghuang.heartext.HearTextApp
import com.yishenghuang.heartext.LibraryViewModel
import com.yishenghuang.heartext.PlayerViewModel
import com.yishenghuang.heartext.ProfileViewModel
import com.yishenghuang.heartext.ReaderViewModel
import com.yishenghuang.heartext.StoreViewModel
import com.yishenghuang.heartext.tts.TtsPlaybackState
import com.yishenghuang.heartext.ui.auth.AuthGate
import com.yishenghuang.heartext.ui.auth.AuthUiState
import com.yishenghuang.heartext.ui.auth.AuthViewModel
import com.yishenghuang.heartext.ui.components.BookTransitionOverlay
import com.yishenghuang.heartext.ui.components.FloatingTabBar
import com.yishenghuang.heartext.ui.components.MainTab
import com.yishenghuang.heartext.ui.components.index
import com.yishenghuang.heartext.ui.components.tabFromIndex
import com.yishenghuang.heartext.ui.library.LibraryScreen
import com.yishenghuang.heartext.ui.navigation.Routes
import com.yishenghuang.heartext.ui.overview.BookOverviewScreen
import com.yishenghuang.heartext.ui.player.GlobalMiniPlayer
import com.yishenghuang.heartext.ui.player.PlayerScreen
import com.yishenghuang.heartext.ui.profile.AboutScreen
import com.yishenghuang.heartext.ui.profile.FeedbackScreen
import com.yishenghuang.heartext.ui.profile.LanguageSettingsScreen
import com.yishenghuang.heartext.ui.profile.ListenSettingsScreen
import com.yishenghuang.heartext.ui.profile.ProfileScreen
import com.yishenghuang.heartext.ui.profile.StorageScreen
import com.yishenghuang.heartext.ui.profile.SupportScreen
import com.yishenghuang.heartext.ui.profile.TypographySettingsScreen
import com.yishenghuang.heartext.ui.reader.ReaderScreen
import com.yishenghuang.heartext.ui.store.CatalogDetailScreen
import com.yishenghuang.heartext.ui.store.CatalogPreviewScreen
import com.yishenghuang.heartext.ui.store.StoreScreen
import com.yishenghuang.heartext.ui.theme.AppColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay

private const val AUTH_PREFS = "heartext_auth"
private const val KEY_OFFLINE_MODE = "offline_mode"

@Composable
fun HearTextRoot() {
    val app = LocalContext.current.applicationContext as HearTextApp
    val authPrefs = remember {
        app.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    }
    var offlineMode by rememberSaveable {
        mutableStateOf(authPrefs.getBoolean(KEY_OFFLINE_MODE, false))
    }
    fun setOfflineMode(value: Boolean) {
        offlineMode = value
        authPrefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()
    }

    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AuthViewModel(app.container.bookRepository, syncEnabled = !offlineMode) }
        }
    )
    LaunchedEffect(offlineMode) { authViewModel.setSyncEnabled(!offlineMode) }
    val authMessage by authViewModel.message.collectAsStateWithLifecycle()
    val authMessageText = authMessage?.let { stringResource(it) }
    LaunchedEffect(authMessageText) {
        authMessageText?.let {
            android.widget.Toast.makeText(app, it, android.widget.Toast.LENGTH_LONG).show()
            authViewModel.clearMessage()
        }
    }

    if (!offlineMode) {
        AuthGate(
            authViewModel = authViewModel,
            onContinueOffline = { setOfflineMode(true) }
        ) {
            MainNav(
                app = app,
                authViewModel = authViewModel,
                onRequestSignIn = { setOfflineMode(false) }
            )
        }
    } else {
        MainNav(
            app = app,
            authViewModel = authViewModel,
            onRequestSignIn = { setOfflineMode(false) }
        )
    }
}

@Composable
private fun MainNav(
    app: HearTextApp,
    authViewModel: AuthViewModel,
    onRequestSignIn: () -> Unit
) {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }
    var tabBarVisible by remember { mutableStateOf(true) }
    var showTransition by remember { mutableStateOf(false) }
    var transitionCover by remember { mutableStateOf<String?>(null) }
    var transitionTitle by remember { mutableStateOf("") }
    var readerReady by remember { mutableStateOf(false) }
    var pendingBookId by remember { mutableStateOf<String?>(null) }
    val hazeState = remember { HazeState() }
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val playbackSession by app.container.playbackCoordinator.session.collectAsStateWithLifecycle()

    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val onPlayerOrReader = currentRoute?.startsWith("player/") == true ||
        currentRoute?.startsWith("reader/") == true
    val showMiniPlayer = playbackSession.active && !onPlayerOrReader && !showTransition

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* MediaSession notification still works if denied on some OEMs */ }

    LaunchedEffect(playbackSession.active, playbackSession.playbackState) {
        if (!playbackSession.active) return@LaunchedEffect
        if (playbackSession.playbackState != TtsPlaybackState.Speaking) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(currentRoute, showTransition) {
        val hideTab = showTransition ||
            currentRoute?.startsWith("reader/") == true ||
            currentRoute?.startsWith("player/") == true ||
            currentRoute?.startsWith("overview/") == true ||
            currentRoute?.startsWith("catalog/") == true ||
            currentRoute?.startsWith("catalog_preview/") == true ||
            currentRoute == Routes.ListenSettings.route ||
            currentRoute == Routes.TypographySettings.route ||
            currentRoute == Routes.StorageSettings.route ||
            currentRoute == Routes.About.route ||
            currentRoute == Routes.Support.route ||
            currentRoute == Routes.Feedback.route ||
            currentRoute == Routes.LanguageSettings.route
        if (hideTab) {
            tabBarVisible = false
        } else {
            delay(300)
            tabBarVisible = true
        }
    }

    LaunchedEffect(currentRoute) {
        selectedTab = when {
            currentRoute == Routes.Library.route -> 1
            currentRoute == Routes.Profile.route ||
                currentRoute == Routes.Voice.route ||
                currentRoute == Routes.ListenSettings.route ||
                currentRoute == Routes.TypographySettings.route ||
                currentRoute == Routes.StorageSettings.route ||
                currentRoute == Routes.About.route ||
                currentRoute == Routes.Support.route ||
                currentRoute == Routes.Feedback.route ||
                currentRoute == Routes.LanguageSettings.route -> 2
            else -> 0
        }
    }

    LaunchedEffect(pendingBookId) {
        val bookId = pendingBookId ?: return@LaunchedEffect
        delay(500)
        if (pendingBookId != bookId || !showTransition) return@LaunchedEffect
        navController.navigate(Routes.Reader.create(bookId))
        pendingBookId = null
    }

    LaunchedEffect(showTransition, readerReady) {
        if (showTransition && !readerReady) {
            delay(2_500)
            if (showTransition && !readerReady) {
                readerReady = true
            }
        }
    }

    fun goTab(tab: MainTab) {
        selectedTab = tab.index()
        val route = when (tab) {
            MainTab.Store -> Routes.Store.route
            MainTab.Library -> Routes.Library.route
            MainTab.Profile -> Routes.Profile.route
        }
        navController.navigate(route) {
            popUpTo(Routes.Store.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openReaderWithTransition(bookId: String, title: String, coverPath: String?) {
        transitionCover = coverPath
        transitionTitle = title
        readerReady = false
        showTransition = true
        pendingBookId = bookId
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Store.route,
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
        ) {
            composable(Routes.Store.route) {
                val vm: StoreViewModel = viewModel(factory = StoreViewModel.factory(app))
                StoreScreen(
                    viewModel = vm,
                    onOpenCatalog = { navController.navigate(Routes.CatalogDetail.create(it)) },
                    onRequestSignIn = onRequestSignIn
                )
            }
            composable(Routes.Library.route) {
                val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))
                LibraryScreen(
                    viewModel = vm,
                    onOpenBook = { navController.navigate(Routes.Overview.create(it)) },
                    onContinue = { bookId, title, cover ->
                        openReaderWithTransition(bookId, title, cover)
                    }
                )
            }
            composable(Routes.Profile.route) {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(app))
                ProfileScreen(
                    viewModel = vm,
                    authSignedIn = authState is AuthUiState.SignedIn,
                    onSignOut = { authViewModel.signOut() },
                    onRequestSignIn = onRequestSignIn,
                    onOpenListenSettings = {
                        navController.navigate(Routes.ListenSettings.route)
                    },
                    onOpenTypography = {
                        navController.navigate(Routes.TypographySettings.route)
                    },
                    onOpenStorage = {
                        navController.navigate(Routes.StorageSettings.route)
                    },
                    onOpenAbout = {
                        navController.navigate(Routes.About.route)
                    },
                    onOpenSupport = {
                        navController.navigate(Routes.Support.route)
                    },
                    onOpenLanguage = {
                        navController.navigate(Routes.LanguageSettings.route)
                    }
                )
            }
            composable(Routes.ListenSettings.route) {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(app))
                ListenSettingsScreen(
                    viewModel = vm,
                    authSignedIn = authState is AuthUiState.SignedIn,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.TypographySettings.route) {
                TypographySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.StorageSettings.route) {
                StorageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.About.route) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Support.route) {
                SupportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFeedback = { navController.navigate(Routes.Feedback.route) }
                )
            }
            composable(Routes.Feedback.route) {
                FeedbackScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LanguageSettings.route) {
                LanguageSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CatalogDetail.route,
                arguments = listOf(navArgument("catalogId") { type = NavType.StringType })
            ) { entry ->
                val catalogId = entry.arguments?.getString("catalogId") ?: return@composable
                val vm: CatalogDetailViewModel =
                    viewModel(factory = CatalogDetailViewModel.factory(app, catalogId))
                CatalogDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onPreview = { navController.navigate(Routes.CatalogPreview.create(it)) },
                    onOpenShelved = { id, title, cover ->
                        // Drop catalog detail/preview so Back from reader returns to Store,
                        // not the download/detail screen (which would re-trigger open).
                        navController.popBackStack(Routes.Store.route, inclusive = false)
                        openReaderWithTransition(id, title, cover)
                    }
                )
            }
            composable(
                route = Routes.CatalogPreview.route,
                arguments = listOf(navArgument("catalogId") { type = NavType.StringType })
            ) { entry ->
                val catalogId = entry.arguments?.getString("catalogId") ?: return@composable
                val vm: CatalogPreviewViewModel =
                    viewModel(factory = CatalogPreviewViewModel.factory(app, catalogId))
                CatalogPreviewScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onGetFullBook = {
                        navController.popBackStack()
                        navController.navigate(Routes.CatalogDetail.create(catalogId))
                    }
                )
            }
            composable(
                route = Routes.Overview.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                val vm: BookOverviewViewModel =
                    viewModel(factory = BookOverviewViewModel.factory(app, bookId))
                BookOverviewScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onKeepReading = { id, title, cover ->
                        openReaderWithTransition(id, title, cover)
                    }
                )
            }
            composable(
                route = Routes.Reader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                val vm: ReaderViewModel = viewModel(factory = ReaderViewModel.factory(app, bookId))
                ReaderScreen(
                    viewModel = vm,
                    onBack = {
                        showTransition = false
                        navController.popBackStack()
                    },
                    onOpenPlayer = { id ->
                        navController.navigate(Routes.Player.create(id))
                    },
                    onLoadingComplete = { readerReady = true }
                )
            }
            composable(
                route = Routes.Player.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                val vm: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(app, bookId))
                PlayerScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenReader = { id ->
                        navController.navigate(Routes.Reader.create(id)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            AnimatedVisibility(
                visible = showMiniPlayer,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200))
            ) {
                GlobalMiniPlayer(
                    session = playbackSession,
                    onOpenPlayer = {
                        navController.navigate(Routes.Player.create(playbackSession.bookId))
                    },
                    onPlayPause = { app.container.playbackCoordinator.playPause() },
                    onStop = { app.container.playbackCoordinator.stop() },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = if (tabBarVisible) 8.dp else 12.dp)
                )
            }

            AnimatedVisibility(
                visible = tabBarVisible,
                enter = fadeIn(tween(350)),
                exit = fadeOut(tween(250))
            ) {
                FloatingTabBar(
                    selected = tabFromIndex(selectedTab),
                    hazeState = hazeState,
                    onSelect = ::goTab
                )
            }
        }

        if (showTransition) {
            BookTransitionOverlay(
                title = transitionTitle,
                coverPath = transitionCover,
                isReady = readerReady,
                onBack = {
                    pendingBookId = null
                    readerReady = false
                    showTransition = false
                    if (currentRoute?.startsWith("reader/") == true) {
                        navController.popBackStack()
                    }
                },
                onTransitionComplete = { showTransition = false }
            )
        }
    }
}
