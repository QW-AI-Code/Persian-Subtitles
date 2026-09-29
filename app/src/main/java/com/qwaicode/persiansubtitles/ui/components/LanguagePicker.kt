package com.qwaicode.persiansubtitles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguage
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguages

/** One-tap shortcuts shown under the current language: the most requested ones. */
private val QUICK_CODES = listOf("fa", "en", "ar", "tr", "de", "fr", "es", "ru")

/**
 * The target-language card of the translate tab: the current language with its flag,
 * a row of flag shortcuts and a button that opens the full, searchable list.
 *
 * Switching the language of a file that already has translations clears them — they
 * are in the old language — so that case asks first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetLanguageCard(
    language: TargetLanguage,
    enabled: Boolean,
    hasTranslations: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    fun request(code: String) {
        if (code == language.code) return
        if (hasTranslations) pending = code else onChange(code)
    }

    SectionCard(
        title = stringResource(R.string.language_title),
        icon = Icons.Filled.Language,
        subtitle = stringResource(R.string.language_desc),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = enabled) { showPicker = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(text = language.flag, fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = language.nameFa, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.bodySmall.autoDirection(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QUICK_CODES.map(TargetLanguages::byCode).forEach { quick ->
                val selected = quick.code == language.code
                Text(
                    text = quick.flag,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .clickable(enabled = enabled) { request(quick.code) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        FilledTonalButton(
            onClick = { showPicker = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.language_choose, TargetLanguages.all.size))
        }

        if (!enabled) {
            Text(
                text = stringResource(R.string.language_locked_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPicker) {
        LanguagePickerDialog(
            selected = language.code,
            onPick = { code ->
                showPicker = false
                request(code)
            },
            onDismiss = { showPicker = false },
        )
    }

    pending?.let { code ->
        val target = TargetLanguages.byCode(code)
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.language_confirm_title, "${target.flag} ${target.nameFa}")) },
            text = { Text(stringResource(R.string.language_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    onChange(code)
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The full list of languages with their flags, searchable by any of their names. */
@Composable
fun LanguagePickerDialog(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { TargetLanguages.search(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Language, contentDescription = null) },
        title = { Text(stringResource(R.string.language_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.language_search_hint)) },
                    textStyle = MaterialTheme.typography.bodyMedium.autoDirection(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.language_no_result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.code }) { lang ->
                        LanguageRow(
                            language = lang,
                            selected = lang.code == selected,
                            onClick = { onPick(lang.code) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun LanguageRow(language: TargetLanguage, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = language.flag, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.nameFa,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.labelSmall.autoDirection(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
