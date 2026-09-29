package com.qwaicode.persiansubtitles.ui.tabs

import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.domain.subtitle.SignatureInserter
import com.qwaicode.persiansubtitles.domain.subtitle.SignatureTemplates
import com.qwaicode.persiansubtitles.ui.components.BidiTextField
import com.qwaicode.persiansubtitles.ui.components.NoticeBox
import com.qwaicode.persiansubtitles.ui.components.SectionCard
import com.qwaicode.persiansubtitles.ui.components.SwitchRow
import com.qwaicode.persiansubtitles.ui.components.autoDirection

/**
 * Credit / advertising lines inside the subtitle: detection patterns, where to
 * look for them, tag stripping and the user's own signature.
 */
@Composable
fun CopyrightTab(
    removeAds: Boolean,
    adEdgesOnly: Boolean,
    adPatterns: String,
    stripTags: Boolean,
    signatureEnabled: Boolean,
    signatureText: String,
    signatureCount: Int,
    detectedAds: Int,
    onRemoveAdsChange: (Boolean) -> Unit,
    onEdgesOnlyChange: (Boolean) -> Unit,
    onPatternsChange: (String) -> Unit,
    onStripTagsChange: (Boolean) -> Unit,
    onSignatureEnabledChange: (Boolean) -> Unit,
    onSignatureTextChange: (String) -> Unit,
    onSignatureCountChange: (Int) -> Unit,
    onRescan: () -> Unit,
    onRestoreDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTemplates by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.copyright_title),
                icon = Icons.Filled.Block,
                subtitle = stringResource(R.string.copyright_desc),
            ) {
                SwitchRow(
                    title = stringResource(R.string.copyright_remove_title),
                    description = stringResource(R.string.copyright_remove_desc),
                    checked = removeAds,
                    onCheckedChange = onRemoveAdsChange,
                )
                SwitchRow(
                    title = stringResource(R.string.copyright_edges_title),
                    description = stringResource(R.string.copyright_edges_desc),
                    checked = adEdgesOnly,
                    onCheckedChange = onEdgesOnlyChange,
                )
                NoticeBox(
                    text = stringResource(R.string.copyright_detected, detectedAds),
                    icon = Icons.Filled.Lightbulb,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copyright_rescan))
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.copyright_patterns_title),
                icon = Icons.Filled.Code,
                subtitle = stringResource(R.string.copyright_patterns_desc),
            ) {
                BidiTextField(
                    value = adPatterns,
                    onValueChange = onPatternsChange,
                    minLines = 6,
                    maxLines = 14,
                    label = stringResource(R.string.copyright_patterns_label),
                )
                OutlinedButton(onClick = onRestoreDefaults, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copyright_restore_defaults))
                }
                SwitchRow(
                    title = stringResource(R.string.copyright_strip_tags_title),
                    description = stringResource(R.string.copyright_strip_tags_desc),
                    checked = stripTags,
                    onCheckedChange = onStripTagsChange,
                )
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.signature_title),
                icon = Icons.Filled.Draw,
                subtitle = stringResource(R.string.signature_desc),
            ) {
                SwitchRow(
                    title = stringResource(R.string.signature_enable),
                    description = null,
                    checked = signatureEnabled,
                    onCheckedChange = onSignatureEnabledChange,
                )
                BidiTextField(
                    value = signatureText,
                    onValueChange = onSignatureTextChange,
                    enabled = signatureEnabled,
                    minLines = 2,
                    maxLines = 4,
                    label = stringResource(R.string.signature_label),
                    placeholder = SignatureInserter.DEFAULT_TEXT,
                )

                FilledTonalButton(
                    onClick = { showTemplates = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.signature_templates_open))
                }

                Text(
                    text = stringResource(R.string.signature_count_value, signatureCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = signatureCount.toFloat(),
                    onValueChange = { v ->
                        val next = v.roundToInt()
                        if (next != signatureCount) onSignatureCountChange(next)
                    },
                    valueRange = SignatureInserter.MIN_COUNT.toFloat()..SignatureInserter.MAX_COUNT.toFloat(),
                    steps = SignatureInserter.MAX_COUNT - SignatureInserter.MIN_COUNT - 1,
                    enabled = signatureEnabled,
                )
                NoticeBox(
                    text = stringResource(R.string.signature_smart_hint),
                    icon = Icons.Filled.Schedule,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showTemplates) {
        SignatureTemplateDialog(
            currentSignature = signatureText,
            onDismiss = { showTemplates = false },
            onPick = { rendered ->
                onSignatureTextChange(rendered)
                if (!signatureEnabled) onSignatureEnabledChange(true)
                showTemplates = false
            },
        )
    }
}

/**
 * The template picker.
 *
 * Every frame is shown with the user's own name already in it, so what the list
 * shows is exactly what will end up in the file. The name is pulled out of the
 * current signature ([SignatureTemplates.extractName]), which means switching from
 * one frame to another keeps it instead of falling back to the default.
 */
@Composable
private fun SignatureTemplateDialog(
    currentSignature: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var name by remember { mutableStateOf(SignatureTemplates.extractName(currentSignature)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
        title = { Text(stringResource(R.string.signature_templates_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.signature_templates_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                BidiTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = stringResource(R.string.signature_templates_name),
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    // A dialog gives its content unbounded height, so a lazy list
                    // inside one needs a ceiling of its own.
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(SignatureTemplates.all, key = { it.id }) { template ->
                        val preview = SignatureTemplates.render(template.pattern, name)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onPick(preview) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = template.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodyMedium.autoDirection(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
