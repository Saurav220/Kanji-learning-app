package com.kanjiforge.app.ui.kanji

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.data.reference.ReferenceCatalog
import com.kanjiforge.app.data.reference.model.KanjiFilter
import com.kanjiforge.app.data.reference.model.KanjiSummary
import com.kanjiforge.app.data.reference.model.RadicalSummary
import com.kanjiforge.app.data.reference.model.StudyRatingFilter
import com.kanjiforge.app.domain.StudyCatalog
import com.kanjiforge.app.domain.displayLabel
import com.kanjiforge.app.domain.gradeLabel
import com.kanjiforge.app.ui.components.AutoLoadMoreEffect
import com.kanjiforge.app.ui.components.EmptyMessage
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.KanjiGlyphBadge
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.KanjiListRow
import com.kanjiforge.app.ui.components.ScreenHeading
import com.kanjiforge.app.ui.components.SearchBarField
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.search.PagedSearchViewModel
import com.kanjiforge.app.ui.search.SearchPhase
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KanjiListViewModel(
    initialGrade: Int?,
    studyCatalog: StudyCatalog,
    private val reference: ReferenceCatalog,
) : PagedSearchViewModel<KanjiFilter, KanjiSummary>(
    initialFilter = KanjiFilter(grade = initialGrade),
    pageSize = 60,
    fetchPage = { query, filter, offset, limit ->
        studyCatalog.searchKanjiPage(query, filter, offset, limit)
    },
) {
    private val _radicals = MutableStateFlow<List<RadicalSummary>>(emptyList())
    val radicals: StateFlow<List<RadicalSummary>> = _radicals.asStateFlow()

    init {
        viewModelScope.launch {
            _radicals.value = runCatching { reference.listAllRadicals() }.getOrDefault(emptyList())
        }
    }
}

@Composable
fun KanjiListScreen(
    viewModel: KanjiListViewModel,
    onOpen: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val radicals by viewModel.radicals.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val haptics = rememberKanjiHaptics()
    val colors = KanjiForgeTheme.colors
    var showRadicalPicker by remember { mutableStateOf(false) }

    val filter = state.filter
    val hasActiveFilters = filter != KanjiFilter()
    val hasMore = (state.phase as? SearchPhase.Ready)?.hasMore == true
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
            item(key = "kanji-heading") {
                ScreenHeading(
                    eyebrow = "KANJI LIBRARY",
                    title = "Kanji dictionary",
                )
            }
            item(key = "kanji-search-field") {
                SearchBarField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "Search kanji, meaning, or reading",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "kanji-primary-filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter.savedOnly,
                        onClick = {
                            haptics(KanjiHaptic.TICK)
                            viewModel.updateFilter { it.copy(savedOnly = !it.savedOnly) }
                        },
                        colors = chipColors,
                        label = { Text("Saved") },
                    )
                    FilterChip(
                        selected = filter.dueOnly,
                        onClick = {
                            haptics(KanjiHaptic.TICK)
                            viewModel.updateFilter { it.copy(dueOnly = !it.dueOnly) }
                        },
                        colors = chipColors,
                        label = { Text("Due") },
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
                }
            }
            item(key = "kanji-secondary-filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceFilterChip(
                        title = "Grade",
                        selectedLabel = filter.grade?.let(::gradeLabel),
                        choices = (1..10).map { it to gradeLabel(it) },
                        onSelect = { selected -> viewModel.updateFilter { it.copy(grade = selected) } },
                    )
                    ChoiceFilterChip(
                        title = "Strokes",
                        selectedLabel = filter.strokeCount?.toString(),
                        choices = (1..33).map { it to it.toString() },
                        onSelect = { selected -> viewModel.updateFilter { it.copy(strokeCount = selected) } },
                    )
                    val selectedRadical = radicals.firstOrNull { it.code == filter.radical }
                    FilterChip(
                        selected = selectedRadical != null,
                        onClick = {
                            haptics(KanjiHaptic.TICK)
                            showRadicalPicker = true
                        },
                        colors = chipColors,
                        label = {
                            val suffix = selectedRadical?.let {
                                val name = it.meaning.ifBlank { "No. ${it.sequence ?: it.code}" }
                                ": ${it.character} $name"
                            }.orEmpty()
                            Text("Radical$suffix")
                        },
                        trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                    )
                    ChoiceFilterChip(
                        title = "Rating",
                        selectedLabel = filter.studyRating?.displayLabel(),
                        choices = StudyRatingFilter.entries.map { it to it.displayLabel() },
                        onSelect = { selected -> viewModel.updateFilter { it.copy(studyRating = selected) } },
                    )
                    if (hasActiveFilters || state.query.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                haptics(KanjiHaptic.TICK)
                                viewModel.onQueryChange("")
                                viewModel.updateFilter { KanjiFilter() }
                            },
                            label = { Text("Clear") },
                            trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                        )
                    }
                }
            }

            if (state.phase is SearchPhase.Loading) {
                item(key = "kanji-loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (state.phase is SearchPhase.Empty) {
                item(key = "kanji-empty") { EmptyMessage("No characters found", "Try another spelling or remove a filter.") }
            }

            itemsIndexed(itemRows, key = { _, row -> row.first().code }) { _, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { item ->
                        val metaTags = buildList {
                            item.jlpt?.let { level -> add("JLPT N$level" to colors.jlptColor(level)) }
                            item.grade?.let { grade -> add(gradeLabel(grade) to null) }
                            item.strokeCount?.let { strokes -> add("$strokes strokes" to null) }
                        }
                        ForgeCard(
                            onClick = { onOpen(item.code) },
                            modifier = Modifier.weight(1f),
                        ) {
                            KanjiListRow(
                                character = item.character,
                                title = item.meaning,
                                subtitle = "",
                                metaTags = metaTags,
                            )
                        }
                    }
                    if (row.size < columns) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            when (val phase = state.phase) {
                is SearchPhase.Error -> {
                    item(key = "kanji-error") {
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
                    item(key = "kanji-appending") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is SearchPhase.Ready -> {
                    if (phase.hasMore) {
                        item(key = "kanji-load-more") {
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
            item(key = "kanji-bottom-spacer") { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showRadicalPicker) {
        RadicalPickerDialog(
            radicals = radicals,
            selectedCode = filter.radical,
            onSelect = { code ->
                haptics(KanjiHaptic.TICK)
                viewModel.updateFilter { it.copy(radical = code) }
                showRadicalPicker = false
            },
            onDismiss = { showRadicalPicker = false },
        )
    }
}

@Composable
private fun <T> ChoiceFilterChip(
    title: String,
    selectedLabel: String?,
    choices: List<Pair<T, String>>,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberKanjiHaptics()
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Box {
        FilterChip(
            selected = selectedLabel != null,
            onClick = {
                haptics(KanjiHaptic.TICK)
                expanded = true
            },
            colors = chipColors,
            label = { Text(if (selectedLabel != null) "$title: $selectedLabel" else title) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Any") },
                onClick = {
                    haptics(KanjiHaptic.TICK)
                    onSelect(null)
                    expanded = false
                },
            )
            choices.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        haptics(KanjiHaptic.TICK)
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RadicalPickerDialog(
    radicals: List<RadicalSummary>,
    selectedCode: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(radicals, search) {
        val needle = search.trim()
        if (needle.isEmpty()) radicals
        else radicals.filter {
            it.character.contains(needle, ignoreCase = true) ||
                it.meaning.contains(needle, ignoreCase = true) ||
                it.reading.contains(needle, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Choose a radical") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBarField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Search radical, meaning, or reading",
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Done,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item(key = "radical-any") {
                        RadicalPickerRow(
                            glyph = null,
                            label = "Any radical",
                            selected = selectedCode == null,
                            onClick = { onSelect(null) },
                        )
                    }
                    items(filtered, key = { it.code }) { item ->
                        val label = buildString {
                            append(item.meaning.ifBlank { "Radical ${item.sequence ?: item.code}" })
                            if (item.reading.isNotBlank()) append(" (${item.reading})")
                        }
                        RadicalPickerRow(
                            glyph = item.character,
                            label = label,
                            selected = item.code == selectedCode,
                            onClick = { onSelect(item.code) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") } },
    )
}

@Composable
private fun RadicalPickerRow(
    glyph: String?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = KanjiForgeTheme.colors
    ForgeCard(
        onClick = onClick,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (selected) MaterialTheme.colorScheme.primary else colors.cardBorder,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!glyph.isNullOrBlank()) {
                KanjiGlyphBadge(glyph = glyph, size = 36.dp)
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
