package com.kanjiforge.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.data.reference.ReferenceCatalog
import com.kanjiforge.app.data.reference.model.KanaSummary
import com.kanjiforge.app.data.reference.model.OutlierCharacterSummary
import com.kanjiforge.app.data.reference.model.OutlierSummary
import com.kanjiforge.app.data.reference.model.RadicalSummary
import com.kanjiforge.app.ui.components.AutoLoadMoreEffect
import com.kanjiforge.app.ui.components.EmptyMessage
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.JapaneseSpeechButton
import com.kanjiforge.app.ui.components.KanjiGlyphBadge
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.MetaPill
import com.kanjiforge.app.ui.components.ScreenHeading
import com.kanjiforge.app.ui.components.SearchBarField
import com.kanjiforge.app.ui.components.StrokeOrderControls
import com.kanjiforge.app.ui.components.StrokePathCanvas
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.components.rememberStrokePlaybackController
import com.kanjiforge.app.ui.search.PagedSearchController
import com.kanjiforge.app.ui.search.SearchPhase
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class KanaScriptFilter { ALL, HIRAGANA, KATAKANA }

sealed interface StaticLibraryState<out T> {
    data object Loading : StaticLibraryState<Nothing>
    data class Ready<T>(val items: List<T>) : StaticLibraryState<T>
    data class Error(val message: String) : StaticLibraryState<Nothing>
}

class KanaLibraryViewModel(private val reference: ReferenceCatalog) : ViewModel() {
    private val allKana = MutableStateFlow<StaticLibraryState<KanaSummary>>(StaticLibraryState.Loading)
    private val _query = MutableStateFlow("")
    private val _scriptFilter = MutableStateFlow(KanaScriptFilter.ALL)

    val query: StateFlow<String> = _query.asStateFlow()
    val scriptFilter: StateFlow<KanaScriptFilter> = _scriptFilter.asStateFlow()

    val state: StateFlow<StaticLibraryState<KanaSummary>> =
        combine(allKana, _query, _scriptFilter) { loaded, q, script ->
            when (loaded) {
                StaticLibraryState.Loading -> StaticLibraryState.Loading
                is StaticLibraryState.Error -> loaded
                is StaticLibraryState.Ready -> {
                    val needle = q.trim()
                    val filtered = loaded.items.filter { item ->
                        val matchesScript = when (script) {
                            KanaScriptFilter.ALL -> true
                            KanaScriptFilter.HIRAGANA -> !item.katakana
                            KanaScriptFilter.KATAKANA -> item.katakana
                        }
                        val matchesQuery = needle.isEmpty() ||
                            item.character.contains(needle, ignoreCase = true) ||
                            item.reading.contains(needle, ignoreCase = true) ||
                            item.meaning.contains(needle, ignoreCase = true)
                        matchesScript && matchesQuery
                    }
                    StaticLibraryState.Ready(filtered)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, StaticLibraryState.Loading)

    init {
        load()
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun selectScript(filter: KanaScriptFilter) {
        _scriptFilter.value = filter
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            allKana.value = StaticLibraryState.Loading
            try {
                allKana.value = StaticLibraryState.Ready(reference.listKana())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                allKana.value = StaticLibraryState.Error(error.message ?: "Could not load kana charts.")
            }
        }
    }
}

class RadicalsLibraryViewModel(private val reference: ReferenceCatalog) : ViewModel() {
    private val allRadicals = MutableStateFlow<StaticLibraryState<RadicalSummary>>(StaticLibraryState.Loading)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<StaticLibraryState<RadicalSummary>> =
        combine(allRadicals, _query) { loaded, q ->
            when (loaded) {
                StaticLibraryState.Loading -> StaticLibraryState.Loading
                is StaticLibraryState.Error -> loaded
                is StaticLibraryState.Ready -> {
                    val needle = q.trim()
                    val filtered = if (needle.isEmpty()) loaded.items else loaded.items.filter { item ->
                        item.character.contains(needle, ignoreCase = true) ||
                            item.meaning.contains(needle, ignoreCase = true) ||
                            item.reading.contains(needle, ignoreCase = true)
                    }
                    StaticLibraryState.Ready(filtered)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, StaticLibraryState.Loading)

    init {
        load()
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            allRadicals.value = StaticLibraryState.Loading
            try {
                allRadicals.value = StaticLibraryState.Ready(reference.listAllRadicals())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                allRadicals.value = StaticLibraryState.Error(error.message ?: "Could not load radicals.")
            }
        }
    }
}

class OutlierLibraryViewModel(private val reference: ReferenceCatalog) : ViewModel() {
    private val articlePager = PagedSearchController<Unit, OutlierSummary>(
        initialFilter = Unit,
        pageSize = 50,
        scope = viewModelScope,
        fetchPage = { query, _, offset, limit -> reference.searchOutlier(query, offset, limit) },
    )
    val articleState = articlePager.state

    var showAncientForms by mutableStateOf(false)
        private set

    private val allCharacters = MutableStateFlow<StaticLibraryState<OutlierCharacterSummary>>(StaticLibraryState.Loading)
    private val _characterQuery = MutableStateFlow("")
    val characterQuery: StateFlow<String> = _characterQuery.asStateFlow()

    val characterState: StateFlow<StaticLibraryState<OutlierCharacterSummary>> =
        combine(allCharacters, _characterQuery) { loaded, q ->
            when (loaded) {
                StaticLibraryState.Loading -> StaticLibraryState.Loading
                is StaticLibraryState.Error -> loaded
                is StaticLibraryState.Ready -> {
                    val needle = q.trim()
                    val filtered = if (needle.isEmpty()) loaded.items else loaded.items.filter {
                        it.name.contains(needle, ignoreCase = true)
                    }
                    StaticLibraryState.Ready(filtered)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, StaticLibraryState.Loading)

    init {
        loadCharacters()
    }

    fun selectTab(ancientForms: Boolean) {
        showAncientForms = ancientForms
    }

    fun onArticleQueryChange(query: String) = articlePager.onQueryChange(query)
    fun loadMoreArticles() = articlePager.loadMore()
    fun retryArticles() = articlePager.retry()

    fun onCharacterQueryChange(query: String) {
        _characterQuery.value = query
    }

    fun retryCharacters() = loadCharacters()

    private fun loadCharacters() {
        viewModelScope.launch {
            allCharacters.value = StaticLibraryState.Loading
            try {
                allCharacters.value = StaticLibraryState.Ready(reference.listAllOutlierCharacters())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                allCharacters.value = StaticLibraryState.Error(error.message ?: "Could not load ancient forms.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KanaLibraryScreen(viewModel: KanaLibraryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val scriptFilter by viewModel.scriptFilter.collectAsStateWithLifecycle()
    val haptics = rememberKanjiHaptics()
    val colors = KanjiForgeTheme.colors
    var selectedKana by remember { mutableStateOf<KanaSummary?>(null) }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 62.dp),
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "kana-heading", span = { GridItemSpan(maxLineSpan) }) {
            ScreenHeading(
                "REFERENCE LIBRARY",
                "Kana charts",
                "Tap any character to inspect stroke order and hear pronunciation.",
            )
        }
        item(key = "kana-search", span = { GridItemSpan(maxLineSpan) }) {
            SearchBarField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = "Search kana, reading, or example",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "kana-filters", span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = scriptFilter == KanaScriptFilter.ALL,
                    onClick = {
                        haptics(KanjiHaptic.TICK)
                        viewModel.selectScript(KanaScriptFilter.ALL)
                    },
                    colors = chipColors,
                    label = { Text("All") },
                )
                FilterChip(
                    selected = scriptFilter == KanaScriptFilter.HIRAGANA,
                    onClick = {
                        haptics(KanjiHaptic.TICK)
                        viewModel.selectScript(KanaScriptFilter.HIRAGANA)
                    },
                    colors = chipColors,
                    label = { Text("Hiragana") },
                )
                FilterChip(
                    selected = scriptFilter == KanaScriptFilter.KATAKANA,
                    onClick = {
                        haptics(KanjiHaptic.TICK)
                        viewModel.selectScript(KanaScriptFilter.KATAKANA)
                    },
                    colors = chipColors,
                    label = { Text("Katakana") },
                )
            }
        }

        when (val current = state) {
            StaticLibraryState.Loading -> {
                item(key = "kana-loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is StaticLibraryState.Error -> {
                item(key = "kana-error", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(current.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retry, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Retry")
                        }
                    }
                }
            }
            is StaticLibraryState.Ready -> {
                if (current.items.isEmpty()) {
                    item(key = "kana-empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyMessage("No entries found", "Try another query or script filter.")
                    }
                } else {
                    gridItems(current.items, key = { "kana-${it.character}" }) { item ->
                        ForgeCard(
                            onClick = { selectedKana = item },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    item.character,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    item.reading,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "kana-spacer", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }
    }

    selectedKana?.let { kana ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedKana = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            val details = listOf(kana.example, kana.meaning, kana.translation, kana.origin)
                .filter(String::isNotBlank)
                .joinToString(" · ")
            val tags = listOfNotNull(
                if (kana.katakana) "Katakana" else "Hiragana",
                if (kana.archaic) "Archaic" else null,
                if (kana.diacritic) "Diacritic" else null,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KanjiGlyphBadge(glyph = kana.character, size = 68.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(kana.reading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tags.forEach { tag ->
                                MetaPill(
                                    text = tag,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        if (details.isNotBlank()) {
                            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.cardBorder),
                    ) {
                        JapaneseSpeechButton(kana.character, contentDescription = "Play kana reading")
                    }
                }

                if (kana.strokePaths.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val playbackController = rememberStrokePlaybackController(
                        resetKey = kana.character,
                        totalStrokes = kana.strokePaths.size,
                        initialShowGuide = true,
                    )
                    val playback = playbackController.state
                    val activeVisible = if (playback.isPlaying || playback.visibleCount > 0) playback.visibleCount else 0

                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StrokePathCanvas(
                            paths = kana.strokePaths,
                            modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth().aspectRatio(1f),
                            showGuide = playback.showGuide,
                            activeCount = activeVisible,
                        )
                    }
                    StrokeOrderControls(
                        state = playback,
                        onTogglePlay = playbackController::togglePlay,
                        onStepNext = playbackController::stepNext,
                        showGuideToggle = true,
                        onToggleGuide = playbackController::toggleGuide,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadicalsLibraryScreen(viewModel: RadicalsLibraryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "radicals-heading") {
            ScreenHeading("REFERENCE LIBRARY", "Radicals", "Recovered radical meanings and stroke data.")
        }
        item(key = "radicals-search") {
            SearchBarField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = "Search radical, meaning, or reading",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (val current = state) {
            StaticLibraryState.Loading -> {
                item(key = "radicals-loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is StaticLibraryState.Error -> {
                item(key = "radicals-error") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(current.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retry, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Retry")
                        }
                    }
                }
            }
            is StaticLibraryState.Ready -> {
                if (current.items.isEmpty()) {
                    item(key = "radicals-empty") { EmptyMessage("No radicals found", "Try another search term.") }
                } else {
                    items(current.items, key = { "radical-${it.code}" }) { item ->
                        ForgeCard {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KanjiGlyphBadge(glyph = item.character.ifBlank { "·" }, size = 50.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(item.meaning.ifBlank { "Meaning unavailable" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        item.reading.takeIf(String::isNotBlank)?.let { MetaPill(it) }
                                        item.strokeCount?.let { MetaPill("$it strokes") }
                                        item.sequence?.let { MetaPill("No. $it") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "radicals-spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun OutlierLibraryScreen(viewModel: OutlierLibraryViewModel) {
    val articleState by viewModel.articleState.collectAsStateWithLifecycle()
    val characterState by viewModel.characterState.collectAsStateWithLifecycle()
    val characterQuery by viewModel.characterQuery.collectAsStateWithLifecycle()
    val showCharacters = viewModel.showAncientForms
    val articleListState = rememberLazyListState()
    val characterListState = rememberLazyListState()
    val haptics = rememberKanjiHaptics()
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    val hasMoreArticles = !showCharacters && (articleState.phase as? SearchPhase.Ready)?.hasMore == true
    AutoLoadMoreEffect(
        listState = articleListState,
        enabled = hasMoreArticles,
        onLoadMore = viewModel::loadMoreArticles,
    )

    LazyColumn(
        state = if (showCharacters) characterListState else articleListState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "outlier-heading") {
            ScreenHeading(
                "OUTLIER REFERENCE",
                "Ideas behind the forms",
                "Readable article text and historical character forms from the local snapshot.",
            )
        }
        item(key = "outlier-tabs") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showCharacters,
                    onClick = {
                        haptics(KanjiHaptic.TICK)
                        viewModel.selectTab(false)
                    },
                    colors = chipColors,
                    label = { Text("Articles") },
                )
                FilterChip(
                    selected = showCharacters,
                    onClick = {
                        haptics(KanjiHaptic.TICK)
                        viewModel.selectTab(true)
                    },
                    colors = chipColors,
                    label = { Text("Ancient forms") },
                )
            }
        }

        if (!showCharacters) {
            item(key = "outlier-article-search") {
                SearchBarField(
                    value = articleState.query,
                    onValueChange = viewModel::onArticleQueryChange,
                    placeholder = "Search readable articles",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (articleState.phase is SearchPhase.Loading) {
                item(key = "outlier-article-loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (articleState.phase is SearchPhase.Empty) {
                item(key = "outlier-article-empty") { EmptyMessage("No articles found", "Try another keyword.") }
            }
            items(articleState.items, key = { "outlier-${it.id}" }) { item ->
                ForgeCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.keyConcept) {
                                MetaPill(
                                    text = "KEY CONCEPT",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        if (item.ancientForm.isNotBlank()) {
                            MetaPill(text = "Ancient form: ${item.ancientForm}")
                        }
                        Text(
                            item.text.ifBlank { "No readable article text in the snapshot." },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            when (val phase = articleState.phase) {
                is SearchPhase.Error -> item(key = "outlier-article-error") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(phase.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retryArticles, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Retry")
                        }
                    }
                }
                SearchPhase.Appending -> item(key = "outlier-article-appending") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is SearchPhase.Ready -> if (phase.hasMore) {
                    item(key = "outlier-article-loadmore") {
                        TextButton(
                            onClick = viewModel::loadMoreArticles,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Load more")
                        }
                    }
                }
                SearchPhase.Loading, SearchPhase.Empty -> Unit
            }
        } else {
            item(key = "outlier-char-search") {
                SearchBarField(
                    value = characterQuery,
                    onValueChange = viewModel::onCharacterQueryChange,
                    placeholder = "Search ancient forms",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (val current = characterState) {
                StaticLibraryState.Loading -> item(key = "outlier-char-loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is StaticLibraryState.Error -> item(key = "outlier-char-error") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(current.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retryCharacters, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Retry")
                        }
                    }
                }
                is StaticLibraryState.Ready -> {
                    if (current.items.isEmpty()) {
                        item(key = "outlier-char-empty") { EmptyMessage("No ancient forms found", "Try another keyword.") }
                    } else {
                        items(current.items, key = { "outlier-char-${it.id}" }) { item ->
                            ForgeCard {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        MetaPill(text = "Historical form")
                                    }
                                    if (item.paths.isNotEmpty()) {
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            StrokePathCanvas(
                                                paths = item.paths,
                                                modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth().aspectRatio(1f),
                                                showGuide = false,
                                                activeCount = item.paths.size,
                                                viewBox = item.viewBox,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "outlier-spacer") { Spacer(Modifier.height(16.dp)) }
    }
}
