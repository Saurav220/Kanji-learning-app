package com.kanjiforge.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.data.reference.model.PagedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SearchPhase {
    data object Loading : SearchPhase
    data class Ready(val hasMore: Boolean) : SearchPhase
    data object Appending : SearchPhase
    data object Empty : SearchPhase
    data class Error(val message: String) : SearchPhase
}

data class PagedSearchState<F, T>(
    val query: String,
    val filter: F,
    val items: List<T>,
    val phase: SearchPhase,
)

@OptIn(FlowPreview::class)
class PagedSearchController<F, T>(
    initialFilter: F,
    private val pageSize: Int = 50,
    private val debounceMs: Long = 250L,
    private val scope: CoroutineScope,
    private val fetchPage: suspend (query: String, filter: F, offset: Int, limit: Int) -> PagedResult<T>,
) {
    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(initialFilter)
    private val retryTrigger = MutableStateFlow(0)

    private val _state = MutableStateFlow(
        PagedSearchState<F, T>(
            query = "",
            filter = initialFilter,
            items = emptyList(),
            phase = SearchPhase.Loading,
        ),
    )
    val state: StateFlow<PagedSearchState<F, T>> = _state.asStateFlow()

    private var loadMoreJob: Job? = null

    init {
        val debouncedQueries = queryFlow
            .drop(1)
            .let { if (debounceMs > 0L) it.debounce(debounceMs) else it }
            .onStart { emit(queryFlow.value) }

        scope.launch {
            combine(debouncedQueries, filterFlow, retryTrigger) { q, f, _ -> q to f }
                .collectLatest { (query, filter) ->
                    loadMoreJob?.cancel()
                    loadMoreJob = null
                    _state.update {
                        it.copy(
                            query = queryFlow.value,
                            filter = filterFlow.value,
                            items = emptyList(),
                            phase = SearchPhase.Loading,
                        )
                    }
                    try {
                        val page = fetchPage(query, filter, 0, pageSize)
                        val phase = if (page.items.isEmpty()) {
                            SearchPhase.Empty
                        } else {
                            SearchPhase.Ready(hasMore = page.hasMore)
                        }
                        _state.value = PagedSearchState(
                            query = queryFlow.value,
                            filter = filterFlow.value,
                            items = page.items,
                            phase = phase,
                        )
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        _state.value = PagedSearchState(
                            query = queryFlow.value,
                            filter = filterFlow.value,
                            items = emptyList(),
                            phase = SearchPhase.Error(error.message ?: "Could not load results."),
                        )
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        if (queryFlow.value == query) return
        loadMoreJob?.cancel()
        loadMoreJob = null
        _state.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun updateFilter(transform: (F) -> F) {
        val updated = transform(filterFlow.value)
        if (updated == filterFlow.value) return
        loadMoreJob?.cancel()
        loadMoreJob = null
        _state.update { it.copy(filter = updated) }
        filterFlow.value = updated
    }

    fun retry() {
        loadMoreJob?.cancel()
        loadMoreJob = null
        retryTrigger.update { it + 1 }
    }

    fun loadMore() {
        val current = _state.value
        val ready = current.phase as? SearchPhase.Ready ?: return
        if (!ready.hasMore) return

        val requestQuery = current.query
        val requestFilter = current.filter
        val existingItems = current.items
        val offset = existingItems.size

        loadMoreJob?.cancel()
        _state.update { it.copy(phase = SearchPhase.Appending) }

        loadMoreJob = scope.launch {
            try {
                val page = fetchPage(requestQuery, requestFilter, offset, pageSize)
                if (queryFlow.value != requestQuery || filterFlow.value != requestFilter) return@launch
                val combined = existingItems + page.items
                _state.value = PagedSearchState(
                    query = queryFlow.value,
                    filter = filterFlow.value,
                    items = combined,
                    phase = if (combined.isEmpty()) SearchPhase.Empty else SearchPhase.Ready(hasMore = page.hasMore),
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (queryFlow.value != requestQuery || filterFlow.value != requestFilter) return@launch
                _state.value = PagedSearchState(
                    query = queryFlow.value,
                    filter = filterFlow.value,
                    items = existingItems,
                    phase = SearchPhase.Error(error.message ?: "Could not load more results."),
                )
            }
        }
    }
}

open class PagedSearchViewModel<F, T>(
    initialFilter: F,
    pageSize: Int = 50,
    debounceMs: Long = 250L,
    fetchPage: suspend (query: String, filter: F, offset: Int, limit: Int) -> PagedResult<T>,
) : ViewModel() {
    private val controller = PagedSearchController(
        initialFilter = initialFilter,
        pageSize = pageSize,
        debounceMs = debounceMs,
        scope = viewModelScope,
        fetchPage = fetchPage,
    )

    val state: StateFlow<PagedSearchState<F, T>> = controller.state

    fun onQueryChange(query: String) = controller.onQueryChange(query)
    fun updateFilter(transform: (F) -> F) = controller.updateFilter(transform)
    fun loadMore() = controller.loadMore()
    fun retry() = controller.retry()
}
