package com.qwaicode.persiansubtitles.ui.tabs

import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.domain.prompt.PromptMode
import com.qwaicode.persiansubtitles.domain.prompt.StylePresets
import com.qwaicode.persiansubtitles.domain.prompt.UserGlossary
import com.qwaicode.persiansubtitles.ui.components.Pill
import com.qwaicode.persiansubtitles.ui.components.SwitchRow
import com.qwaicode.persiansubtitles.ui.components.NoticeBox
import com.qwaicode.persiansubtitles.ui.components.BidiTextField
import com.qwaicode.persiansubtitles.ui.components.SectionCard

/**
 * Tone presets and the user's own prompt.
 *
 * The two are now an either/or, chosen by the radio button in each card's header.
 * Before that, writing a custom prompt did not switch the presets off: a tone was
 * still ticked underneath and went into every request together with the user's own
 * instruction, so the model received two style briefs at once.
 */
@Composable
fun StyleTab(
    mode: PromptMode,
    presetId: String,
    customPrompt: String,
    temperature: Float,
    autoTone: Boolean,
    autoTonePreset: String,
    readWholeSubtitle: Boolean,
    userGlossary: String,
    onModeChange: (PromptMode) -> Unit,
    onPresetChange: (String) -> Unit,
    onCustomPromptChange: (String) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onAutoToneChange: (Boolean) -> Unit,
    onUserGlossaryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presetsActive = mode == PromptMode.PRESET
    val glossaryCount = remember(userGlossary) { UserGlossary.parse(userGlossary).size }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The AI picks the tone after reading the film. First on the page, because
        // it decides what happens to the preset list right below it.
        item {
            SectionCard(
                title = stringResource(R.string.style_auto_tone_card),
                icon = Icons.Filled.AutoFixHigh,
            ) {
                SwitchRow(
                    title = stringResource(R.string.style_auto_tone_title),
                    description = stringResource(R.string.style_auto_tone_desc),
                    checked = autoTone,
                    onCheckedChange = onAutoToneChange,
                )
                if (autoTone && !readWholeSubtitle) {
                    NoticeBox(
                        text = stringResource(R.string.style_auto_tone_needs_read),
                        icon = Icons.Filled.Lightbulb,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else if (autoTone && !presetsActive) {
                    NoticeBox(
                        text = stringResource(R.string.style_auto_tone_custom),
                        icon = Icons.Filled.Lightbulb,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (autoTonePreset.isNotBlank() && autoTonePreset == presetId) {
                    NoticeBox(
                        text = stringResource(R.string.style_auto_tone_applied, StylePresets.byId(autoTonePreset).title),
                        icon = Icons.Filled.AutoFixHigh,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.style_presets_title),
                icon = Icons.Filled.Palette,
                subtitle = stringResource(R.string.style_presets_desc),
            ) {
                ModeChoice(
                    label = stringResource(R.string.style_mode_preset),
                    selected = presetsActive,
                    onClick = { onModeChange(PromptMode.PRESET) },
                )

                // Switched off, not hidden: the user can still see which tone would
                // be used, but nothing here is selected or sent while their own
                // prompt is in charge.
                Column(
                    modifier = Modifier.alpha(if (presetsActive) 1f else 0.45f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    StylePresets.all.forEach { preset ->
                        val selected = presetsActive && presetId == preset.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    enabled = presetsActive,
                                    role = Role.RadioButton,
                                    onClick = { onPresetChange(preset.id) },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                enabled = presetsActive,
                            )
                            Spacer(Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = preset.title, style = MaterialTheme.typography.bodyMedium)
                                if (presetsActive && autoTonePreset == preset.id) {
                                    Spacer(Modifier.height(4.dp))
                                    Pill(
                                        text = stringResource(R.string.badge_auto_tone),
                                        container = MaterialTheme.colorScheme.primaryContainer,
                                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.style_custom_title),
                icon = Icons.Filled.AutoAwesome,
                subtitle = stringResource(R.string.style_custom_desc),
            ) {
                ModeChoice(
                    label = stringResource(R.string.style_mode_custom),
                    selected = !presetsActive,
                    onClick = { onModeChange(PromptMode.CUSTOM_ONLY) },
                )

                if (!presetsActive && customPrompt.isBlank()) {
                    NoticeBox(
                        text = stringResource(R.string.style_mode_custom_empty),
                        icon = Icons.Filled.Lightbulb,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                BidiTextField(
                    value = customPrompt,
                    onValueChange = onCustomPromptChange,
                    minLines = 4,
                    maxLines = 10,
                    label = stringResource(R.string.style_custom_label),
                )

                Text(
                    text = stringResource(R.string.style_examples_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StylePresets.customExamples.forEach { example ->
                    AssistChip(
                        onClick = {
                            val separator = if (customPrompt.isBlank()) "" else "\n"
                            onCustomPromptChange(customPrompt + separator + example)
                        },
                        label = { Text(example, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.style_glossary_title),
                icon = Icons.Filled.Translate,
                subtitle = stringResource(R.string.style_glossary_desc),
            ) {
                BidiTextField(
                    value = userGlossary,
                    onValueChange = onUserGlossaryChange,
                    minLines = 4,
                    maxLines = 12,
                    label = stringResource(R.string.style_glossary_label),
                )
                Text(
                    text = if (glossaryCount > 0) {
                        stringResource(R.string.style_glossary_count, glossaryCount)
                    } else {
                        stringResource(R.string.style_glossary_example)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (glossaryCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.style_temperature_title),
                icon = Icons.Filled.Thermostat,
                subtitle = stringResource(R.string.style_temperature_desc),
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%.2f", temperature),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = temperature,
                    onValueChange = { v ->
                        // Snap to the 0.1 steps and write only when the step changes:
                        // every drag tick used to be a DataStore write.
                        val next = (v * 10f).roundToInt() / 10f
                        if (next != temperature) onTemperatureChange(next)
                    },
                    valueRange = 0f..1.4f,
                    steps = 13,
                )
                NoticeBox(
                    text = stringResource(R.string.style_temperature_hint),
                    icon = Icons.Filled.Lightbulb,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The header radio of a card: which of the two style sources is in charge. */
@Composable
private fun ModeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
    }
}
