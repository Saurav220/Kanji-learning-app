package com.kanjiforge.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kanjiforge.app.ui.theme.KanjiForgeTheme

@Composable
fun SearchBarField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Search,
    onImeAction: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val haptics = rememberKanjiHaptics()
    val colors = KanjiForgeTheme.colors

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(
                    onClick = {
                        haptics(KanjiHaptic.TICK)
                        onValueChange("")
                    },
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = colors.cardBorder,
        ),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                onImeAction()
            },
            onDone = {
                focusManager.clearFocus()
                onImeAction()
            },
        ),
    )
}

@Composable
fun AutoLoadMoreEffect(
    listState: LazyListState,
    enabled: Boolean,
    threshold: Int = 8,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, enabled, threshold) {
        if (!enabled) return@LaunchedEffect
        var triggeredForCount = -1
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems to lastVisible
        }.collect { (totalItems, lastVisible) ->
            if (totalItems > 0 && lastVisible >= totalItems - 1 - threshold && triggeredForCount != totalItems) {
                triggeredForCount = totalItems
                currentOnLoadMore()
            }
        }
    }
}
