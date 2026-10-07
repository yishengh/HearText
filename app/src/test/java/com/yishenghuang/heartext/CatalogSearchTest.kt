package com.yishenghuang.heartext

import com.yishenghuang.heartext.network.ApiCatalogBook
import com.yishenghuang.heartext.network.ApiCatalogList
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CatalogSearchTest {
    private fun book(id: String) = ApiCatalogBook(id, "fixture", id, id, null, "en", null,
        null, "public domain", "epub", 1, 0, false, 1, 0, 0)
    private fun page(id: String, page: Int = 1, total: Int = 1) =
        ApiCatalogList(listOf(book(id)), total, page, 50)

    @Test fun lateCancelledSearchCannotReplaceNewResultsOrLoadingState() = runBlocking {
        val first = CompletableDeferred<ApiCatalogList>()
        val second = CompletableDeferred<ApiCatalogList>()
        val search = CatalogSearch(this, { q, _, _ ->
            // Deliberately emulate a source that finishes despite cancellation.
            withContext(NonCancellable) { if (q == "old") first.await() else second.await() }
        }, { "Failed" })
        search.submit("old", null)
        yield()
        search.submit("new", null)
        yield()
        first.complete(page("old"))
        yield(); yield()
        assertTrue(search.state.value.loading)
        assertTrue(search.state.value.items.isEmpty())
        second.complete(page("new"))
        yield(); yield()
        assertEquals(listOf("new"), search.state.value.items.map { it.id })
        assertFalse(search.state.value.loading)
    }

    @Test fun paginationRetainsSubmittedQueryAndFailedPageCanBeRetried() = runBlocking {
        val requests = mutableListOf<Triple<String?, String?, Int>>()
        var fail = true
        val search = CatalogSearch(this, { q, category, number ->
            requests += Triple(q, category, number)
            if (number == 2 && fail) { fail = false; error("private server detail") }
            page("$number", number, 2)
        }, { "Localized failure" })
        search.submit("  query  ", "category")
        yield()
        search.loadMore()
        search.loadMore() // A second tap before launch must not enqueue another page.
        yield()
        assertEquals("Localized failure", search.state.value.error)
        assertEquals(listOf("1"), search.state.value.items.map { it.id })
        search.loadMore()
        yield()
        assertEquals(listOf(1, 2, 2), requests.map { it.third })
        assertTrue(requests.all { it.first == "query" && it.second == "category" })
        assertEquals(listOf("1", "2"), search.state.value.items.map { it.id })
        assertNull(search.state.value.error)
        assertFalse(search.state.value.hasMore)
    }
}
