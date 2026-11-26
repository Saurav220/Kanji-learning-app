package com.kanjiforge.app.ui.words

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanjiforge.app.data.reference.ReferenceCatalog
import com.kanjiforge.app.data.reference.model.PartOfSpeech
import com.kanjiforge.app.data.reference.model.WordFilter
import com.kanjiforge.app.data.reference.model.WordSummary
import com.kanjiforge.app.ui.components.AutoLoadMoreEffect
import com.kanjiforge.app.ui.components.EmptyMessage
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.MetaPill
import com.kanjiforge.app.ui.components.ScreenHeading
import com.kanjiforge.app.ui.components.SearchBarField
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.search.PagedSearchViewModel
import com.kanjiforge.app.ui.search.SearchPhase
import com.kanjiforge.app.ui.theme.KanjiForgeTheme

class WordListViewModel(
    reference: ReferenceCatalog,
) : PagedSearchViewModel<WordFilter, WordSummary>(
    initialFilter = WordFilter(),
    pageSize = 50,
    fetchPage = { query, filter, offset, limit ->
        reference.searchWords(query, filter, offset, limit)
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordListScreen(
    viewModel: WordListViewModel,
    onOpen: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val haptics = rememberKanjiHaptics()
    val colors = KanjiForgeTheme.colors
    val filter = state.filter
    val hasActive = filter != WordFilter() || state.query.isNotBlank()
    val hasMore = (state.phase as? SearchPhase.Ready)?.hasMore == true
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    AutoLoadMoreEffect(
        listState = listState,
        enabled = hasMore,
        onLoadMore = viewModel::loadMore,
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 700.dp) 2 else 1
        val itemRows = remember(state.items, columns) { state.items.chunked(columns) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = if (columns > 1) 24.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "word-heading") {
                ScreenHeading(
                    eyebrow = "WORD LIBRARY",
                    title = "Word dictionary",
                )
            }
            item(key = "word-search-field") {
                SearchBarField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "Search words, readings, or romaji",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "word-primary-filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter.commonOnly,
                        onClick = {
                            haptics(KanjiHaptic.TICK)
                            viewModel.updateFilter { it.copy(commonOnly = !it.commonOnly) }
                        },
                        colors = chipColors,
                        label = { Text("Common") },
                    )
                    (1..5).forEach { value ->
                        FilterChip(
                            selected = filter.jlpt == value,
                            onClick = {
                                haptics(KanjiHaptic.TICK)
                                viewModel.updateFilter { it.copy(jlpt = if (it.jlpt == value) null else value) }
                            },
                            colors = chipColors,
                            label = { Text("N$value") },
                        )
                    }
                    if (hasActive) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                haptics(KanjiHaptic.TICK)
                                viewModel.onQueryChange("")
                                viewModel.updateFilter { WordFilter() }
                            },
                            label = { Text("Clear") },
                            trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                        )
                    }
                }
            }
            item(key = "word-pos-filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PartOfSpeech.entries.forEach { pos ->
                        FilterChip(
                            selected = filter.partOfSpeech == pos,
                            onClick = {
                                haptics(KanjiHaptic.TICK)
                                viewModel.updateFilter { it.copy(partOfSpeech = if (it.partOfSpeech == pos) null else pos) }
                            },
                            colors = chipColors,
                            label = { Text(pos.label) },
                        )
                    }
                }
            }

            if (state.phase is SearchPhase.Loading) {
                item(key = "word-loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (state.phase is SearchPhase.Empty) {
                item(key = "word-empty") { EmptyMessage("No words found", "Try a shorter query or change the filters.") }
            }

            itemsIndexed(itemRows, key = { _, row -> row.first().id }) { _, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { word ->
                        ForgeCard(
                            onClick = { onOpen(word.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 74.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            word.forms.firstOrNull() ?: word.readings.firstOrNull().orEmpty(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            if (word.common) {
                                                MetaPill(
                                                    text = "Common",
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            }
                                            word.jlpt?.let { level ->
                                                val tint = colors.jlptColor(level)
                                                MetaPill(
                                                    text = "N$level",
                                                    containerColor = tint.copy(alpha = 0.14f),
                                                    contentColor = tint,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        word.readings.joinToString(" · ") + word.romaji.firstOrNull()?.let { "  ·  $it" }.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        word.glosses.firstOrNull() ?: word.translation.ifBlank { "Meaning unavailable" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (row.size < columns) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            when (val phase = state.phase) {
                is SearchPhase.Error -> {
                    item(key = "word-error") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(phase.message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = viewModel::retry, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Retry")
                            }
                        }
                    }
                }
                SearchPhase.Appending -> {
                    item(key = "word-appending") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is SearchPhase.Ready -> {
                    if (phase.hasMore) {
                        item(key = "word-load-more") {
                            TextButton(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text("Load more")
                            }
                        }
                    }
                }
                SearchPhase.Loading, SearchPhase.Empty -> Unit
            }
            item(key = "word-bottom-spacer") { Spacer(Modifier.height(16.dp)) }
        }
    }
}
