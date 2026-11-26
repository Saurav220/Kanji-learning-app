package com.kanjiforge.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kanjiforge.app.data.backup.BackupManager
import com.kanjiforge.app.data.backup.BackupResult
import com.kanjiforge.app.domain.CardDirection
import com.kanjiforge.app.domain.QuizPrompt
import com.kanjiforge.app.domain.StudyPreferences
import com.kanjiforge.app.domain.StudyStore
import com.kanjiforge.app.domain.displayLabel
import com.kanjiforge.app.ui.components.ForgeCard
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.LocalSnackbarHostState
import com.kanjiforge.app.ui.components.ScreenHeading
import com.kanjiforge.app.ui.components.SectionLabel
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

class SettingsViewModel(
    private val store: StudyStore,
    private val backup: BackupManager,
) : ViewModel() {
    val preferences: StateFlow<StudyPreferences> = store.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyPreferences())

    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    fun updatePreferences(transform: (StudyPreferences) -> StudyPreferences) {
        viewModelScope.launch { store.updatePreferences(transform) }
    }

    fun exportBackup(openStream: () -> OutputStream?) {
        viewModelScope.launch {
            _backupResult.value = withContext(Dispatchers.IO) {
                val stream = runCatching { openStream() }.getOrNull()
                    ?: return@withContext BackupResult(false, "Could not open the backup destination.")
                backup.exportTo(stream)
            }
        }
    }

    fun importBackup(openStream: () -> InputStream?) {
        viewModelScope.launch {
            _backupResult.value = withContext(Dispatchers.IO) {
                val stream = runCatching { openStream() }.getOrNull()
                    ?: return@withContext BackupResult(false, "Could not read the backup file.")
                backup.importFrom(stream)
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()
    val haptics = rememberKanjiHaptics()
    val snackbarHostState = LocalSnackbarHostState.current
    var directionMenu by remember { mutableStateOf(false) }
    var quizPromptMenu by remember { mutableStateOf(false) }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    LaunchedEffect(backupResult) {
        val result = backupResult ?: return@LaunchedEffect
        haptics(if (result.success) KanjiHaptic.CONFIRM else KanjiHaptic.REJECT)
        snackbarHostState?.showSnackbar(result.message, duration = SnackbarDuration.Short)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.exportBackup { context.contentResolver.openOutputStream(uri) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importBackup { context.contentResolver.openInputStream(uri) }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeading(
            "SETTINGS",
            "Make it work for you",
            "Choose a comfortable pace and keep a portable copy of your learning state.",
        )

        ForgeCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Appearance")
                PreferenceSwitch(
                    title = "True black (AMOLED)",
                    description = "Use pure black backgrounds to save power on OLED displays.",
                    checked = preferences.amoledDark,
                ) { checked -> viewModel.updatePreferences { it.copy(amoledDark = checked) } }
            }
        }

        ForgeCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Study rhythm")
                Text("New characters each day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20, 30, 50, 100).forEach { value ->
                        FilterChip(
                            selected = preferences.dailyNew == value,
                            onClick = {
                                haptics(KanjiHaptic.TICK)
                                viewModel.updatePreferences { it.copy(dailyNew = value) }
                            },
                            colors = chipColors,
                            label = { Text(value.toString()) },
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Card prompt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(
                        onClick = {
                            haptics(KanjiHaptic.TAP)
                            directionMenu = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(preferences.cardDirection.displayLabel(), modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(directionMenu, { directionMenu = false }) {
                        CardDirection.entries.forEach { direction ->
                            DropdownMenuItem(
                                text = { Text(direction.displayLabel()) },
                                onClick = {
                                    haptics(KanjiHaptic.TICK)
                                    directionMenu = false
                                    viewModel.updatePreferences { it.copy(cardDirection = direction) }
                                },
                            )
                        }
                    }
                }

                Text("Default quiz focus", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(
                        onClick = {
                            haptics(KanjiHaptic.TAP)
                            quizPromptMenu = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(preferences.quizPrompt.displayLabel(), modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(quizPromptMenu, { quizPromptMenu = false }) {
                        QuizPrompt.entries.forEach { prompt ->
                            DropdownMenuItem(
                                text = { Text(prompt.displayLabel()) },
                                onClick = {
                                    haptics(KanjiHaptic.TICK)
                                    quizPromptMenu = false
                                    viewModel.updatePreferences { it.copy(quizPrompt = prompt) }
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                PreferenceSwitch(
                    title = "Repeat missed answers",
                    description = "Add a missed quiz card once at the end of this session.",
                    checked = preferences.repeatWrongAnswers,
                ) { checked -> viewModel.updatePreferences { it.copy(repeatWrongAnswers = checked) } }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                PreferenceSwitch(
                    title = "Autoplay Japanese readings",
                    description = "Use the Japanese voice installed on this Android device.",
                    checked = preferences.autoPlayAudio,
                ) { checked -> viewModel.updatePreferences { it.copy(autoPlayAudio = checked) } }
            }
        }

        ForgeCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Backup & provenance")
                Text("Your study data stays on this device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Export favorites, collections, preferences, review records, and session history. Version 2 JSON backups can be merged with this app or the KanjiForge browser prototype.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            haptics(KanjiHaptic.TAP)
                            exportLauncher.launch("kanjiforge-backup-${LocalDate.now()}.json")
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Export backup") }
                    OutlinedButton(
                        onClick = {
                            haptics(KanjiHaptic.TAP)
                            importLauncher.launch(arrayOf("application/json", "text/*"))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Import & merge") }
                }
                backupResult?.let { result ->
                    Text(
                        result.message,
                        color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 4.dp))
                Text("Content sources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Readable records come from the supplied Kanji Study 7.8.3 runtime snapshot. Encoded KLC and SRS fields and sparse Outlier binary payloads were not decoded. Stroke practice is a learner self-check; the local review schedule is a KanjiForge approximation. Japanese reading playback uses the device's system text-to-speech engine. No network, account, or automatic handwriting recognition is included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = rememberKanjiHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { next ->
                    haptics(KanjiHaptic.TICK)
                    onCheckedChange(next)
                },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
