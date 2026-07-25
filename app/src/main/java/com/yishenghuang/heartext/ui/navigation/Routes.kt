package com.yishenghuang.heartext.ui.navigation

sealed class Routes(val route: String) {
    data object Store : Routes("store")
    data object Library : Routes("library")
    data object Profile : Routes("profile")
    data object ListenSettings : Routes("listen_settings")
    data object TypographySettings : Routes("typography_settings")
    data object StorageSettings : Routes("storage_settings")
    data object About : Routes("about")
    data object Support : Routes("support")
    data object Feedback : Routes("feedback")
    data object LanguageSettings : Routes("language_settings")
    data object CatalogDetail : Routes("catalog/{catalogId}") {
        fun create(catalogId: String) = "catalog/$catalogId"
    }
    data object CatalogPreview : Routes("catalog_preview/{catalogId}") {
        fun create(catalogId: String) = "catalog_preview/$catalogId"
    }
    data object Overview : Routes("overview/{bookId}") {
        fun create(bookId: String) = "overview/$bookId"
    }
    data object Reader : Routes("reader/{bookId}") {
        fun create(bookId: String) = "reader/$bookId"
    }
    data object Player : Routes("player/{bookId}") {
        fun create(bookId: String) = "player/$bookId"
    }

    /** @deprecated Kept for deep-link compatibility; prefer [Store]. */
    data object Home : Routes("home")
    /** @deprecated Prefer [Profile]. */
    data object Voice : Routes("voice")
}
