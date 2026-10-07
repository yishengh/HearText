package com.yishenghuang.heartext

import com.yishenghuang.heartext.network.ApiCatalogBook
import com.yishenghuang.heartext.network.ApiCatalogList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Confined to the UI thread; pagination always uses the last submitted query. */
internal class CatalogSearch(
    private val scope: CoroutineScope,
    private val fetch: suspend (String?, String?, Int) -> ApiCatalogList,
    private val errorMessage: () -> String
) {
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    data class State(
        val items: List<ApiCatalogBook> = emptyList(),
        val total: Int = 0,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val error: String? = null
    )
    private var query: String? = null
    private var category: String? = null
    private var page = 0
    private var generation = 0L
    private var job: Job? = null

    fun submit(query: String, category: String?) {
        generation++
        job?.cancel()
        this.query = query.trim().takeIf { it.isNotEmpty() }
        this.category = category
        page = 0
        mutable.value = State(loading = true)
        request(reset = true)
    }

    fun loadMore() {
        val current = state.value
        if (current.loading || current.loadingMore || !current.hasMore) return
        mutable.value = current.copy(loadingMore = true, error = null)
        request(reset = false)
    }

    private fun request(reset: Boolean) {
        val requestGeneration = generation
        val requestedQuery = query
        val requestedCategory = category
        val nextPage = page + 1
        job = scope.launch {
            try {
                val result = fetch(requestedQuery, requestedCategory, nextPage)
                if (generation != requestGeneration) return@launch
                page = nextPage
                val items = (if (reset) result.items else state.value.items + result.items).distinctBy { it.id }
                mutable.value = State(items, result.total,
                    hasMore = items.size < result.total && result.items.isNotEmpty())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == requestGeneration) mutable.value = state.value.copy(error = errorMessage())
            } finally {
                if (generation == requestGeneration) {
                    mutable.value = state.value.copy(loading = false, loadingMore = false)
                }
            }
        }
    }
}
