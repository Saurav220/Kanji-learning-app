package com.kanjiforge.app.ui.words

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.domain.ReviewType
import com.kanjiforge.app.domain.StudyCatalog
import com.kanjiforge.app.domain.WordDetailState
import com.kanjiforge.app.ui.components.CollectionPickerButton
import com.kanjiforge.app.ui.components.EmptyMessage
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.JapaneseSpeechButton
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.LocalSnackbarHostState
import com.kanjiforge.app.ui.components.MetaPill
import com.kanjiforge.app.ui.components.SectionLabel
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WordDetailViewModel(
    val id: Long,
    private val studyCatalog: StudyCatalog,
) : ViewModel() {
    val state: StateFlow<WordDetailState> = studyCatalog.observeWordDetail(id)
        .stateIn(viewModelScope, SharingStarted.Lazily, WordDetailState.Loading)

    fun toggleFavorite(save: Boolean) {
        viewModelScope.launch {
            studyCatalog.toggleFavorite(ReviewType.WORD, id.toString(), save)
        }
    }

    suspend fun setCollectionMembership(setId: String, member: Boolean) {
        studyCatalog.setCollectionMembership(setId, ReviewType.WORD, id.toString(), member)
    }

    suspend fun createCollection(name: String) {
        studyCatalog.createCollectionWithItem(name, ReviewType.WORD, id.toString())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDetailScreen(
    viewModel: WordDetailViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberKanjiHaptics()
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val colors = KanjiForgeTheme.colors

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val current = state) {
            WordDetailState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            WordDetailState.NotFound -> {
                item {
                    EmptyMessage("Word not found", "This entry is unavailable in the local catalog.")
                }
            }
            is WordDetailState.Error -> {
                item { Text(current.message, color = MaterialTheme.colorScheme.error) }
            }
            is WordDetailState.Ready -> {
                val entry = current.word
                item {
                    ForgeCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (entry.common) {
                                    MetaPill(
                                        text = "Common",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                entry.jlpt?.let { level ->
                                    val tint = colors.jlptColor(level)
                                    MetaPill(
                                        text = "JLPT N$level",
                                        containerColor = tint.copy(alpha = 0.16f),
                                        contentColor = tint,
                                    )
                                }
                                entry.tags.take(4).forEach { tag ->
                                    MetaPill(text = tag)
                                }
                            }

                            Text(
                                entry.forms.joinToString(" · ").ifBlank { "Written form unavailable" },
                                fontSize = 32.sp,
                                lineHeight = 40.sp,
                                fontWeight = FontWeight.SemiBold,
                            )

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        entry.readings.joinToString(" · ").ifBlank { "Reading unavailable" },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (entry.romaji.isNotEmpty()) {
                                        Text(
                                            entry.romaji.joinToString(" · "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                ) {
                                    JapaneseSpeechButton(
                                        text = entry.readings.firstOrNull().orEmpty(),
                                        contentDescription = "Play word reading",
                                    )
                                }
                            }

                            if (entry.glosses.isNotEmpty() || entry.translation.isNotBlank()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    entry.glosses.forEachIndexed { index, gloss ->
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            Text(gloss, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                        }
                                    }
                                    if (entry.translation.isNotBlank()) {
                                        Text(
                                            entry.translation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val nextSaved = !current.isSaved
                                haptics(if (nextSaved) KanjiHaptic.CONFIRM else KanjiHaptic.TAP)
                                viewModel.toggleFavorite(nextSaved)
                                snackbarHostState?.let { host ->
                                    scope.launch {
                                        host.currentSnackbarData?.dismiss()
                                        host.showSnackbar(
                                            if (nextSaved) "Saved word" else "Removed from saved",
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(
                                if (current.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (current.isSaved) "Saved word" else "Save word")
                        }
                        CollectionPickerButton(
                            choices = current.collectionChoices,
                            onToggleCollection = { setId, member -> viewModel.setCollectionMembership(setId, member) },
                            onCreateCollection = { name -> viewModel.createCollection(name) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (entry.notes.isNotBlank()) {
                    item {
                        ForgeCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SectionLabel("Notes")
                                Text(entry.notes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
