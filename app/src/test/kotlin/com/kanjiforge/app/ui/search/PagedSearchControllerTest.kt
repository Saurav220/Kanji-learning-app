package com.kanjiforge.app.ui.search

import com.kanjiforge.app.data.reference.model.PagedResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagedSearchControllerTest {

    @Test
    fun debouncesSearchTracksHasMoreAndCancelsInFlightAppendWhenFilterChanges() = runTest {
        val appendGate = CompletableDeferred<Unit>()
        var failNext = false

        val controller = PagedSearchController<String, String>(
            initialFilter = "all",
            pageSize = 2,
            debounceMs = 250L,
            scope = backgroundScope,
        ) { query, filter, offset, limit ->
            if (failNext) throw IllegalStateException("boom")
            if (offset > 0 && filter == "all") {
                appendGate.await()
            }
            val source = when {
                filter == "empty" -> emptyList()
                query == "sun" -> listOf("日", "明", "晴")
                else -> listOf("$filter-1", "$filter-2", "$filter-3")
            }
            val slice = source.drop(offset).take(limit + 1)
            PagedResult(slice.take(limit), slice.size > limit)
        }

        // Initial load fires immediately without waiting 250ms debounce
        runCurrent()
        assertEquals(listOf("all-1", "all-2"), controller.state.value.items)
        assertEquals(SearchPhase.Ready(hasMore = true), controller.state.value.phase)

        // Start loadMore, which blocks on appendGate; then change filter before it completes
        controller.loadMore()
        assertEquals(SearchPhase.Appending, controller.state.value.phase)
        runCurrent()

        controller.updateFilter { "jlpt5" }
        runCurrent()
        appendGate.complete(Unit)
        runCurrent()

        assertEquals(listOf("jlpt5-1", "jlpt5-2"), controller.state.value.items)
        assertEquals(SearchPhase.Ready(hasMore = true), controller.state.value.phase)

        // Load second (final) page -> hasMore becomes false; further loadMore is a no-op
        controller.loadMore()
        runCurrent()
        assertEquals(listOf("jlpt5-1", "jlpt5-2", "jlpt5-3"), controller.state.value.items)
        assertEquals(SearchPhase.Ready(hasMore = false), controller.state.value.phase)

        controller.loadMore()
        assertEquals(SearchPhase.Ready(hasMore = false), controller.state.value.phase)

        // Debounce test
        controller.onQueryChange("s")
        controller.onQueryChange("sun")
        advanceTimeBy(100)
        runCurrent()
        // Query text updates immediately even before debounce fires
        assertEquals("sun", controller.state.value.query)
        assertEquals(listOf("jlpt5-1", "jlpt5-2", "jlpt5-3"), controller.state.value.items)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("日", "明"), controller.state.value.items)

        // Empty phase
        controller.updateFilter { "empty" }
        runCurrent()
        assertEquals(SearchPhase.Empty, controller.state.value.phase)

        // Error + Retry phase (clear query first so filter results apply)
        controller.onQueryChange("")
        advanceTimeBy(300)
        runCurrent()

        failNext = true
        controller.updateFilter { "err" }
        runCurrent()
        assertTrue(controller.state.value.phase is SearchPhase.Error)

        failNext = false
        controller.retry()
        runCurrent()
        assertEquals(listOf("err-1", "err-2"), controller.state.value.items)
    }
}
