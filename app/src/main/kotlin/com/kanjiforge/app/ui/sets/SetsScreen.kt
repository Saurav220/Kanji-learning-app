package com.kanjiforge.app.ui.sets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.domain.CollectionDetailState
import com.kanjiforge.app.domain.CollectionsState
import com.kanjiforge.app.domain.ResolvedSetMember
import com.kanjiforge.app.domain.ReviewType
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.StudyCatalog
import com.kanjiforge.app.ui.components.EmptyMessage
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.KanjiGlyphBadge
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.LocalSnackbarHostState
import com.kanjiforge.app.ui.components.MetaPill
import com.kanjiforge.app.ui.components.ScreenHeading
import com.kanjiforge.app.ui.components.SectionLabel
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.study.StudyModePickerDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class CollectionsViewModel(
    private val studyCatalog: StudyCatalog,
) : ViewModel() {
    val state: StateFlow<CollectionsState> = studyCatalog.observeCollections()
        .stateIn(viewModelScope, SharingStarted.Lazily, CollectionsState.Loading)

    private val _statusMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val statusMessage: StateFlow<Pair<Boolean, String>?> = _statusMessage.asStateFlow()

    fun createFromFavorites(name: String, onCreated: () -> Unit) {
        viewModelScope.launch {
            runCatching { studyCatalog.createCollectionFromFavorites(name) }
                .onSuccess {
                    _statusMessage.value = true to "Collection created from saved items."
                    onCreated()
                }
                .onFailure {
                    _statusMessage.value = false to (it.message ?: "Could not create collection.")
                }
        }
    }
}

class CollectionDetailViewModel(
    private val setId: String,
    private val studyCatalog: StudyCatalog,
) : ViewModel() {
    val state: StateFlow<CollectionDetailState> = studyCatalog.observeCollectionDetail(setId)
        .stateIn(viewModelScope, SharingStarted.Lazily, CollectionDetailState.Loading)

    fun moveMember(type: ReviewType, itemId: String, direction: Int) {
        viewModelScope.launch {
            studyCatalog.moveCollectionMember(setId, type, itemId, direction)
        }
    }

    fun removeMember(type: ReviewType, itemId: String) {
        viewModelScope.launch {
            studyCatalog.removeCollectionMember(setId, type, itemId)
        }
    }

    fun restoreMember(type: ReviewType, itemId: String) {
        viewModelScope.launch {
            studyCatalog.setCollectionMembership(setId, type, itemId, true)
        }
    }

    fun deleteSet(onDeleted: () -> Unit) {
        viewModelScope.launch {
            studyCatalog.deleteCollection(setId)
            onDeleted()
        }
    }
}

@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onOpenCollection: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val haptics = rememberKanjiHaptics()
    var name by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "collections-heading") {
            ScreenHeading(
                "YOUR COLLECTIONS",
                "Saved sets",
                "Build ordered study lists from the kanji and words you save.",
            )
        }

        when (val current = state) {
            CollectionsState.Loading -> {
                item(key = "collections-loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is CollectionsState.Ready -> {
                val hasSaved = (current.favoriteKanjiCount + current.favoriteWordCount) > 0
                val submitCreate = {
                    if (name.isNotBlank() && hasSaved) {
                        focusManager.clearFocus()
                        haptics(KanjiHaptic.CONFIRM)
                        viewModel.createFromFavorites(name) { name = "" }
                    }
                }
                item(key = "collections-create") {
                    ForgeCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionLabel("Create from saved items")
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Collection name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = if (name.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { name = "" }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Clear name")
                                        }
                                    }
                                } else {
                                    null
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MetaPill("${current.favoriteKanjiCount} characters")
                                MetaPill("${current.favoriteWordCount} words")
                            }
                            Button(
                                enabled = name.isNotBlank() && hasSaved,
                                onClick = submitCreate,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Create collection") }
                            statusMessage?.let { (success, text) ->
                                Text(
                                    text,
                                    color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                if (current.sets.isEmpty()) {
                    item(key = "collections-empty") {
                        EmptyMessage(
                            "No collections yet",
                            "Save a few characters or words, then create a collection or add an item from its detail page.",
                            icon = Icons.Outlined.Bookmarks,
                        )
                    }
                } else {
                    item(key = "collections-list-label") {
                        SectionLabel("Your lists", badge = current.sets.size.toString(), modifier = Modifier.padding(top = 4.dp))
                    }
                    itemsIndexed(current.sets, key = { _, set -> set.id }) { _, set ->
                        val date = Instant.ofEpochMilli(set.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        ForgeCard(onClick = { onOpenCollection(set.id) }) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(set.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MetaPill("${set.kanjiCount} kanji")
                                        MetaPill("${set.wordCount} words")
                                        MetaPill(date.toString())
                                    }
                                }
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "Open ${set.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item(key = "collections-spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun CollectionDetailScreen(
    viewModel: CollectionDetailViewModel,
    onStartStudy: (SessionMode, String) -> Unit,
    onOpenKanji: (Int) -> Unit,
    onOpenWord: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = rememberKanjiHaptics()
    var confirmDelete by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(SessionMode.CARDS) }

    when (val current = state) {
        CollectionDetailState.Loading -> {
            Box(Modifier.fillMaxSize().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        CollectionDetailState.NotFound -> {
            EmptyMessage("Collection not found", "This collection may have been deleted.", icon = Icons.Outlined.Bookmarks)
        }
        is CollectionDetailState.Ready -> {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                item(key = "collection-heading") {
                    ScreenHeading(
                        "YOUR COLLECTION",
                        current.set.name,
                        "Members keep their saved order. Add items from kanji and word detail pages.",
                    )
                }
                item(key = "collection-summary") {
                    ForgeCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MetaPill(
                                    text = "${current.members.count { it.type == ReviewType.KANJI }} characters",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                MetaPill(text = "${current.members.count { it.type == ReviewType.WORD }} words")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    enabled = current.members.any { it.type == ReviewType.KANJI },
                                    onClick = {
                                        haptics(KanjiHaptic.TAP)
                                        showModePicker = true
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) { Text("Study characters") }
                                OutlinedButton(
                                    onClick = {
                                        haptics(KanjiHaptic.TAP)
                                        confirmDelete = true
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                if (current.members.isEmpty()) {
                    item(key = "collection-empty") {
                        EmptyMessage("This collection is empty", "Add a kanji or word from its detail page.", icon = Icons.Outlined.Bookmarks)
                    }
                } else {
                    itemsIndexed(current.members, key = { _, member -> "${member.type}:${member.itemId}" }) { index, member ->
                        CollectionMemberRow(
                            member = member,
                            index = index,
                            total = current.members.size,
                            onOpen = {
                                when {
                                    member.kanjiCode != null -> onOpenKanji(member.kanjiCode)
                                    member.wordId != null -> onOpenWord(member.wordId)
                                }
                            },
                            onMove = { direction -> viewModel.moveMember(member.type, member.itemId, direction) },
                            onRemove = {
                                viewModel.removeMember(member.type, member.itemId)
                                snackbarHostState?.let { host ->
                                    scope.launch {
                                        host.currentSnackbarData?.dismiss()
                                        val result = host.showSnackbar(
                                            message = "Removed ${member.title}",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreMember(member.type, member.itemId)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
                item(key = "collection-spacer") { Spacer(Modifier.height(16.dp)) }
            }

            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("Delete ${current.set.name}?") },
                    text = { Text("The collection and its ordered member list will be removed. Your saved favorites stay unchanged.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                haptics(KanjiHaptic.HEAVY)
                                confirmDelete = false
                                viewModel.deleteSet(onDeleted)
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { confirmDelete = false },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Cancel") }
                    },
                )
            }

            if (showModePicker) {
                StudyModePickerDialog(
                    selectedMode = selectedMode,
                    onSelectMode = { selectedMode = it },
                    onStart = {
                        showModePicker = false
                        onStartStudy(selectedMode, current.set.id)
                    },
                    onDismiss = { showModePicker = false },
                )
            }
        }
    }
}

@Composable
private fun CollectionMemberRow(
    member: ResolvedSetMember,
    index: Int,
    total: Int,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val canOpen = member.kanjiCode != null || member.wordId != null
    val haptics = rememberKanjiHaptics()
    ForgeCard(onClick = onOpen, enabled = canOpen) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (member.type == ReviewType.KANJI) {
                    KanjiGlyphBadge(glyph = member.title, size = 46.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (member.type == ReviewType.WORD) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(member.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            MetaPill("Word")
                        }
                    } else {
                        Text(member.subtitle.ifBlank { member.title }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (member.type == ReviewType.WORD && member.subtitle.isNotBlank()) {
                        Text(member.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (canOpen) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = index > 0,
                    onClick = {
                        haptics(KanjiHaptic.TICK)
                        onMove(-1)
                    },
                ) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move ${member.title} up")
                }
                IconButton(
                    enabled = index < total - 1,
                    onClick = {
                        haptics(KanjiHaptic.TICK)
                        onMove(1)
                    },
                ) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move ${member.title} down")
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        haptics(KanjiHaptic.TAP)
                        onRemove()
                    },
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove ${member.title}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
