package com.kanjiforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kanjiforge.app.domain.CollectionChoice
import kotlinx.coroutines.launch

@Composable
fun CollectionPickerButton(
    choices: List<CollectionChoice>,
    onToggleCollection: suspend (setId: String, member: Boolean) -> Unit,
    onCreateCollection: suspend (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptics = rememberKanjiHaptics()
    var showingDialog by remember { mutableStateOf(false) }
    var collectionName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val memberCount = choices.count { it.containsItem }
    OutlinedButton(
        onClick = {
            haptics(KanjiHaptic.TAP)
            showingDialog = true
            errorMessage = null
        },
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (memberCount > 0) "Collections ($memberCount)" else "Add to collection")
    }

    if (showingDialog) {
        val submitCreate = {
            if (collectionName.isNotBlank()) {
                focusManager.clearFocus()
                scope.launch {
                    errorMessage = null
                    runCatching { onCreateCollection(collectionName) }
                        .onSuccess {
                            haptics(KanjiHaptic.CONFIRM)
                            collectionName = ""
                        }
                        .onFailure {
                            haptics(KanjiHaptic.REJECT)
                            errorMessage = it.message ?: "Could not create the collection."
                        }
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showingDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Collections") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = collectionName,
                        onValueChange = { collectionName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New collection name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = if (collectionName.isNotEmpty()) {
                            {
                                IconButton(onClick = { collectionName = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear collection name")
                                }
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                    )
                    Button(
                        enabled = collectionName.isNotBlank(),
                        onClick = submitCreate,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Create and add") }

                    if (choices.isNotEmpty()) {
                        Text(
                            "EXISTING COLLECTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        choices.forEach { choice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(
                                        value = choice.containsItem,
                                        role = Role.Checkbox,
                                        onValueChange = { checked ->
                                            haptics(KanjiHaptic.TICK)
                                            scope.launch {
                                                errorMessage = null
                                                runCatching { onToggleCollection(choice.id, checked) }
                                                    .onFailure { errorMessage = it.message ?: "Could not update collection." }
                                            }
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = choice.containsItem,
                                    onCheckedChange = null,
                                )
                                Text(
                                    choice.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showingDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Done") }
            },
        )
    }
}
