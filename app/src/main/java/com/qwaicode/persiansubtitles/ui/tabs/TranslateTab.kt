package com.qwaicode.persiansubtitles.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.ui.components.AppIcons
import com.qwaicode.persiansubtitles.fa
import com.qwaicode.persiansubtitles.faGrouped
import com.qwaicode.persiansubtitles.toPersianDigits
import com.qwaicode.persiansubtitles.domain.progress.EtaEstimator
import com.qwaicode.persiansubtitles.domain.prompt.StylePresets
import com.qwaicode.persiansubtitles.data.db.ProjectEntity
import com.qwaicode.persiansubtitles.data.db.ProjectStatus
import com.qwaicode.persiansubtitles.domain.ai.ContextBrief
import com.qwaicode.persiansubtitles.domain.subtitle.ExportFormat
import com.qwaicode.persiansubtitles.ui.Stats
import com.qwaicode.persiansubtitles.ui.components.InfoBlock
import com.qwaicode.persiansubtitles.ui.components.NoticeBox
import com.qwaicode.persiansubtitles.ui.components.Pill
import com.qwaicode.persiansubtitles.ui.components.autoDirection
import com.qwaicode.persiansubtitles.ui.components.ltrDirection
import com.qwaicode.persiansubtitles.ui.components.SectionCard
import com.qwaicode.persiansubtitles.ui.components.StatBox
import com.qwaicode.persiansubtitles.ui.components.TargetLanguageCard
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguage
import com.qwaicode.persiansubtitles.ui.theme.SuccessGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslateTab(
    project: ProjectEntity?,
    stats: Stats,
    hasApiKey: Boolean,
    modelName: String,
    errorMessage: String?,
    brief: ContextBrief?,
    eta: EtaEstimator.Estimate?,
    timeShiftMs: Int,
    onShiftTiming: (Int) -> Unit,
    onResetTiming: () -> Unit,
    language: TargetLanguage,
    onLanguageChange: (String) -> Unit,
    onImport: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReview: () -> Unit,
    onPolish: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onClear: () -> Unit,
    onRereadSubtitle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val running = project?.status == ProjectStatus.RUNNING ||
        project?.status == ProjectStatus.REVIEW ||
        project?.status == ProjectStatus.POLISH

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!hasApiKey) {
            item {
                NoticeBox(
                    text = stringResource(R.string.translate_no_api_key),
                    icon = Icons.Filled.Warning,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.translate_project_title),
                icon = Icons.Filled.Subtitles,
            ) {
                if (project == null) {
                    Text(
                        text = stringResource(R.string.translate_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.translate_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // A file name is Latin text and must stay left-to-right: in an
                    // RTL card it otherwise breaks apart at every dot and bracket.
                    Text(
                        text = project.fileName,
                        style = MaterialTheme.typography.bodyMedium.ltrDirection(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // A flow row: with the language pill added, four pills no longer
                    // fit next to each other on a narrow phone.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusPill(project.status)
                        Pill(
                            text = project.totalCues.faGrouped(),
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Pill(
                            text = project.format.uppercase(),
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Pill(
                            text = "${language.flag} ${language.nameFa}",
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                FilledTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_import))
                }
            }
        }

        // Chosen before or after importing a file; locked while a run is going.
        item {
            TargetLanguageCard(
                language = language,
                enabled = !running,
                hasTranslations = stats.translated > 0,
                onChange = onLanguageChange,
            )
        }

        if (project != null) {
            item {
                SectionCard(
                    title = stringResource(R.string.translate_progress_title),
                    icon = AppIcons.FactCheck,
                ) {
                    LinearProgressIndicator(
                        progress = { stats.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "٪" + (stats.progress * 100).toInt().fa(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        // Speed and time left, only while lines are actually being
                        // translated; the review and polish passes have no fixed end.
                        if (project.status == ProjectStatus.RUNNING) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (eta == null) {
                                    stringResource(R.string.eta_calculating)
                                } else {
                                    stringResource(
                                        R.string.eta_line,
                                        eta.linesPerMinute.fa(),
                                        formatDuration(eta.remainingMs),
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox(
                            value = stats.translated.faGrouped(),
                            caption = stringResource(R.string.stat_translated),
                            accent = SuccessGreen,
                            modifier = Modifier.weight(1f),
                        )
                        StatBox(
                            value = stats.remaining.faGrouped(),
                            caption = stringResource(R.string.stat_remaining),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox(
                            value = stats.flagged.faGrouped(),
                            caption = stringResource(R.string.stat_flagged),
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                        StatBox(
                            value = stats.ads.faGrouped(),
                            caption = stringResource(R.string.stat_ads),
                            accent = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (errorMessage != null) {
                        NoticeBox(
                            text = errorMessage,
                            icon = Icons.Filled.Warning,
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // What the app understood about the film. Shown because it decides the
            // wording of every line: if a name or the level of politeness is wrong
            // here, it is wrong in the whole file — and then re-reading helps.
            if (brief != null) {
                item { ContextBriefCard(brief = brief, onReread = onRereadSubtitle) }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.translate_control_title),
                    icon = Icons.Filled.PlayArrow,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.translate_model_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Pill(
                            text = modelName,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    if (running) {
                        Button(
                            onClick = onPause,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_pause))
                        }
                    } else {
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (stats.translated > 0) R.string.action_resume else R.string.action_start
                                )
                            )
                        }
                    }

                    OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                        Icon(AppIcons.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_review))
                    }
                    Text(
                        text = stringResource(R.string.translate_review_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // The editorial pass. Separate from "review" on purpose: review
                    // re-translates what is missing, this one corrects the Persian
                    // that is already there.
                    OutlinedButton(onClick = onPolish, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Spellcheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_polish))
                    }
                    Text(
                        text = stringResource(R.string.translate_polish_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.translate_output_title),
                    icon = Icons.Filled.Download,
                    subtitle = stringResource(R.string.translate_export_desc),
                ) {
                    TimingSyncBlock(
                        shiftMs = timeShiftMs,
                        onShift = onShiftTiming,
                        onReset = onResetTiming,
                    )
                    ExportFormat.entries.forEach { format ->
                        val label = when (format) {
                            ExportFormat.SRT -> R.string.export_srt
                            ExportFormat.VTT -> R.string.export_vtt
                            ExportFormat.BILINGUAL_SRT -> R.string.export_bilingual
                            ExportFormat.TXT -> R.string.export_txt
                        }
                        FilledTonalButton(
                            onClick = { onExport(format) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(label))
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.translate_workspace_title),
                    icon = Icons.Filled.Delete,
                    subtitle = stringResource(R.string.translate_workspace_desc),
                ) {
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_clear))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.clear_confirm_title)) },
            text = { Text(stringResource(R.string.clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClear()
                    }
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * What the app understood about the film.
 *
 * Rebuilt because of the screenshot: the tone and the level of address used to sit
 * in two chips side by side, and a chip that has to share the row with another one
 * squeezes a whole sentence — «محاوره، صمیمی بین دوستان و خانواده» — into a column
 * of single words. They are full-width blocks now, so a long value simply wraps.
 *
 * The card also collapses. A brief with ten characters and twenty glossary entries
 * is several screens long, and it sat directly above the start button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextBriefCard(brief: ContextBrief, onReread: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val details = brief.characters.size + brief.glossary.size

    SectionCard(
        title = stringResource(R.string.brief_title),
        icon = Icons.Filled.MenuBook,
    ) {
        val title = brief.title.trim()
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.autoDirection(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val tags = listOfNotNull(
            brief.genre.trim().takeIf { it.isNotBlank() },
            brief.setting.trim().takeIf { it.isNotBlank() },
        )
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                tags.forEach { tag ->
                    Pill(
                        text = tag,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        if (brief.summary.isNotBlank()) {
            InfoBlock(
                label = stringResource(R.string.brief_summary),
                value = brief.summary.trim(),
                icon = Icons.Filled.MenuBook,
            )
        }
        // The tone the AI picked for the translation after reading the film.
        StylePresets.find(brief.tonePreset)?.let { preset ->
            val reason = brief.toneReason.trim()
            InfoBlock(
                label = stringResource(R.string.brief_tone_preset),
                value = if (reason.isBlank()) preset.title else "${preset.title}\n$reason",
                icon = Icons.Filled.AutoFixHigh,
            )
        }
        if (brief.tone.isNotBlank()) {
            InfoBlock(
                label = stringResource(R.string.brief_tone),
                value = brief.tone.trim(),
                icon = Icons.Filled.Palette,
            )
        }
        if (brief.formality.isNotBlank()) {
            InfoBlock(
                label = stringResource(R.string.brief_formality),
                value = brief.formality.trim(),
                icon = Icons.Filled.RecordVoiceOver,
            )
        }

        if (details > 0) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (expanded) R.string.brief_hide_details else R.string.brief_show_details,
                        details,
                    )
                )
            }
        }

        if (expanded) {
            if (brief.characters.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.brief_characters),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                brief.characters.take(10).forEach { character ->
                    CharacterRow(character)
                }
            }

            if (brief.glossary.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.brief_glossary),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                brief.glossary.take(12).forEach { term ->
                    TermRow(source = term.source, persian = term.persian)
                }
            }
        }

        OutlinedButton(onClick = onReread, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.brief_reread))
        }
    }
}

/**
 * One character. The Persian name leads, the original name follows in its own
 * left-to-right style — a Latin name inside a right-to-left line used to break apart
 * at every dot and hyphen — and the role gets its own line instead of being appended
 * to an ever-growing bullet string.
 */
@Composable
private fun CharacterRow(character: ContextBrief.Character) {
    val persian = character.persian.trim().ifBlank { character.name.trim() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Both sides wrap instead of being clipped: a long original name used to
            // take the whole row and push the Persian name out of the card.
            Text(
                text = persian,
                style = MaterialTheme.typography.titleSmall.autoDirection(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = character.name.trim(),
                style = MaterialTheme.typography.labelMedium.ltrDirection(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        val note = character.note.trim()
        if (note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall.autoDirection(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One fixed term: the original on the left, the agreed Persian on the right. */
@Composable
private fun TermRow(source: String, persian: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = persian.trim(),
            style = MaterialTheme.typography.bodySmall.autoDirection(),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = source.trim(),
            style = MaterialTheme.typography.labelSmall.ltrDirection(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
fun StatusPill(status: String) {
    val (labelRes, container, content) = when (status) {
        ProjectStatus.RUNNING -> Triple(
            R.string.status_running,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ProjectStatus.REVIEW -> Triple(
            R.string.status_review,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ProjectStatus.POLISH -> Triple(
            R.string.status_polish,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ProjectStatus.PAUSED -> Triple(
            R.string.status_paused,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        ProjectStatus.DONE -> Triple(
            R.string.status_done,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ProjectStatus.ERROR -> Triple(
            R.string.status_error,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> Triple(
            R.string.status_idle,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Pill(text = stringResource(labelRes), container = container, content = content)
}

/**
 * Shifts the whole exported file against the video. Only the export moves: the
 * project keeps its original timings, so a wrong nudge is undone with one tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimingSyncBlock(shiftMs: Int, onShift: (Int) -> Unit, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sync_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Pill(
                text = if (shiftMs == 0) {
                    stringResource(R.string.sync_none)
                } else {
                    stringResource(R.string.sync_value, formatShift(shiftMs))
                },
                container = if (shiftMs == 0) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                content = if (shiftMs == 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
        }
        Text(
            text = stringResource(R.string.sync_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(-1000, -100, 100, 1000).forEach { delta ->
                OutlinedButton(onClick = { onShift(delta) }) {
                    Text(
                        stringResource(
                            if (delta < 0) R.string.sync_earlier else R.string.sync_later,
                            formatShift(kotlin.math.abs(delta), signed = false),
                        )
                    )
                }
            }
            if (shiftMs != 0) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.sync_reset)) }
            }
        }
    }
}

/** `1500` → «+۱٫۵»; seconds with one decimal, Persian digits and decimal sign. */
private fun formatShift(ms: Int, signed: Boolean = true): String {
    val pattern = if (signed) "%+.1f" else "%.1f"
    return String.format(java.util.Locale.US, pattern, ms / 1000.0)
        .removeSuffix(".0")
        .replace('.', '٫')
        .toPersianDigits()
}

/** A human duration for the ETA: «۳ دقیقه», «۱ ساعت و ۱۰ دقیقه», «کمتر از یک دقیقه». */
@Composable
private fun formatDuration(ms: Long): String {
    val minutes = ((ms + 59_999) / 60_000).toInt()
    return when {
        minutes <= 1 -> stringResource(R.string.eta_under_minute)
        minutes < 60 -> stringResource(R.string.eta_minutes, minutes.fa())
        else -> stringResource(R.string.eta_hours, (minutes / 60).fa(), (minutes % 60).fa())
    }
}
