package com.yishenghuang.heartext

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.*
import com.yishenghuang.heartext.ui.overview.BookOverviewScreen
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.IOException

class OverviewStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedObservationCanRetryAndMissingBookDoesNotSpinForever() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        var observations = 0
        val dao = database.bookDao()
        val failingDao = object : BookDao by dao {
            override fun observeBook(id: String) = flow {
                if (observations++ == 0) throw IOException("private database detail")
                emitAll(dao.observeBook(id))
            }
        }
        val vm = BookOverviewViewModel("overview", BookRepository(context, failingDao, CoverStore(context)))
        store.put("overview", vm)
        var backs = 0
        try {
            compose.setContent { MaterialTheme { BookOverviewScreen(vm, { backs++ }, { _, _, _ -> }) } }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(context.getString(R.string.error_overview_load)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(context.getString(R.string.error_book_not_found)).fetchSemanticsNodes().isNotEmpty()
            }
            runBlocking { dao.upsert(BookEntity("overview", "Overview fixture", "Author", BookFormat.TXT, "/fixture.txt")) }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Overview fixture").fetchSemanticsNodes().isNotEmpty()
            }
            runBlocking { dao.delete("overview") }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(context.getString(R.string.error_book_not_found)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            assertEquals(1, backs)
        } finally {
            compose.runOnIdle { store.clear() }
            database.close()
        }
    }
}
