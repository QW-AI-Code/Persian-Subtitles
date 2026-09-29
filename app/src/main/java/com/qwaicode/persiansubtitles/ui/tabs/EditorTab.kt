package com.qwaicode.persiansubtitles.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.fa
import com.qwaicode.persiansubtitles.toPersianDigits
import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.ui.EditorFilter
import com.qwaicode.persiansubtitles.ui.Stats
import com.qwaicode.persiansubtitles.ui.components.BidiTextField
import com.qwaicode.persiansubtitles.ui.components.Pill
import com.qwaicode.persiansubtitles.ui.components.autoDirection

/**
 * Line-by-line editor. Every cue can be corrected by hand, sent back to the AI
 * on its own, or marked as a credit line.
 */
@Composable
fun EditorTab(
    cues: List<CueEntity>,
    stats: Stats,
    query: String,
    filter: EditorFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (EditorFilter) -> Unit,
    onSave: (Int, String) -> Unit,
    onRetranslate: (Int) -> Unit,
    onToggleAd: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<CueEntity?>(null) }
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            BidiTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = stringResource(R.string.editor_search_hint),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.height(10.dp))
            FilterMenu(
                filter = filter,
                stats = stats,
                shown = cues.size,
                onFilterChange = onFilterChange,
            )
        }

        if (stats.total == 0) {
            EmptyState(stringResource(R.string.editor_empty))
            return@Column
        }

        if (cues.isEmpty()) {
            EmptyState(stringResource(R.string.editor_no_results))
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Stable keys let Compose reuse rows instead of rebuilding the list
            // on every progress update — this is what keeps scrolling smooth. The
            // content type tells it which rows are interchangeable, so an ad row is
            // never recycled into a dialogue row and back.
            items(items = cues, key = { it.id }, contentType = { it.isAd }) { cue ->
                CueRow(
                    cue = cue,
                    onClick = { editing = cue },
                    onRetranslate = { onRetranslate(cue.id) },
                    onToggleAd = { onToggleAd(cue.id, !cue.isAd) },
                )
            }
        }
    }

    editing?.let { cue ->
        EditDialog(
            cue = cue,
            onDismiss = { editing = null },
            onSave = { text ->
                onSave(cue.id, text)
                editing = null
            },
            onRetranslate = {
                onRetranslate(cue.id)
                editing = null
            },
        )
    }
}

/**
 * The filter, as one dropdown instead of a row of chips.
 *
 * The chips were a scrolling row that never fit: on a normal phone the fifth one was
 * cut off at the edge of the screen and «ویرایش‌شده» was unreadable. A menu shows
 * every option at full length, with the number of lines behind each one, and gives
 * the row back to the search field.
 */
@Composable
private fun FilterMenu(
    filter: EditorFilter,
    stats: Stats,
    shown: Int,
    onFilterChange: (EditorFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val entries = listOf(
        Triple(EditorFilter.ALL, R.string.filter_all, stats.total),
        Triple(EditorFilter.UNTRANSLATED, R.string.filter_untranslated, stats.remaining),
        Triple(EditorFilter.FLAGGED, R.string.filter_flagged, stats.flagged),
        Triple(EditorFilter.EDITED, R.string.filter_edited, stats.edited),
        Triple(EditorFilter.ADS, R.string.filter_ads, stats.ads),
    )
    val current = entries.firstOrNull { it.first == filter } ?: entries.first()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(current.second),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(6.dp))
                Pill(
                    text = current.third.fa(),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                entries.forEach { (value, label, count) ->
                    DropdownMenuItem(
                        onClick = {
                            onFilterChange(value)
                            expanded = false
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(210.dp),
                            ) {
                                Text(
                                    text = stringResource(label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (value == filter) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                Pill(
                                    text = count.fa(),
                                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        leadingIcon = {
                            if (value == filter) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.editor_result_count, shown),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun CueRow(
    cue: CueEntity,
    onClick: () -> Unit,
    onRetranslate: () -> Unit,
    onToggleAd: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (cue.isAd) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    text = cue.id.fa(),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRange(cue.startMs, cue.endMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (cue.edited) {
                    Pill(
                        text = stringResource(R.string.badge_edited),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (cue.flagged) {
                    Spacer(Modifier.width(6.dp))
                    Pill(
                        text = stringResource(R.string.badge_flagged),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            // The English source and the Persian translation each get their own
            // direction, so a mixed line is not scrambled and its punctuation
            // stays on the correct side.
            Text(
                text = cue.source,
                style = MaterialTheme.typography.bodySmall.autoDirection(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = cue.translated?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.editor_not_translated),
                style = MaterialTheme.typography.bodyMedium.autoDirection(),
                color = if (cue.isTranslated) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onClick) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRetranslate) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_retranslate), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleAd) {
                    Icon(
                        Icons.Filled.Block,
                        contentDescription = stringResource(R.string.action_toggle_ad),
                        tint = if (cue.isAd) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditDialog(
    cue: CueEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRetranslate: () -> Unit,
) {
    var text by remember(cue.id) { mutableStateOf(cue.translated ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_dialog_title, cue.id)) },
        text = {
            Column {
                Text(
                    text = cue.source,
                    style = MaterialTheme.typography.bodySmall.autoDirection(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                BidiTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    maxLines = 8,
                    label = stringResource(R.string.editor_dialog_field),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetranslate) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_retranslate))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** 00:01:02 → 00:01:05, always with ASCII digits so it stays readable in RTL. */
private fun formatRange(startMs: Long, endMs: Long): String =
    "${clock(startMs)} ← ${clock(endMs)}".toPersianDigits()

private fun clock(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
}
