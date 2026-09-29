package com.qwaicode.persiansubtitles.ui.tabs

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatTextdirectionRToL
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.domain.text.BidiMode
import com.qwaicode.persiansubtitles.network.FreeModelCatalog
import com.qwaicode.persiansubtitles.ui.ApiTestState
import com.qwaicode.persiansubtitles.ui.TokenUsageUi
import com.qwaicode.persiansubtitles.ui.components.BidiTextField
import com.qwaicode.persiansubtitles.ui.components.NoticeBox
import com.qwaicode.persiansubtitles.ui.components.OptionCard
import com.qwaicode.persiansubtitles.ui.components.Pill
import com.qwaicode.persiansubtitles.ui.components.SectionCard
import com.qwaicode.persiansubtitles.ui.components.SwitchRow
import com.qwaicode.persiansubtitles.ui.components.ltrDirection
import com.qwaicode.persiansubtitles.ui.theme.SuccessGreen
import kotlin.math.roundToInt

/** API key, free model list, speed and behaviour. */
@Composable
fun SettingsTab(
    apiKey: String,
    model: String,
    models: List<FreeModelCatalog.Entry>,
    testState: ApiTestState,
    modelsLoading: Boolean,
    batchSize: Int,
    concurrency: Int,
    keepProperNames: Boolean,
    autoReview: Boolean,
    autoPolish: Boolean,
    wifiOnly: Boolean,
    readWholeSubtitle: Boolean,
    bidiMode: BidiMode,
    persianPunctuation: Boolean,
    onApiKeyChange: (String) -> Unit,
    onTest: () -> Unit,
    onRefreshModels: () -> Unit,
    onModelChange: (String) -> Unit,
    onBatchSizeChange: (Int) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onKeepProperNamesChange: (Boolean) -> Unit,
    onAutoReviewChange: (Boolean) -> Unit,
    onAutoPolishChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onReadWholeSubtitleChange: (Boolean) -> Unit,
    onBidiModeChange: (BidiMode) -> Unit,
    onPersianPunctuationChange: (Boolean) -> Unit,
    onOpenApiKeyPage: () -> Unit,
    tokenUsage: TokenUsageUi,
    onOpenUsagePage: () -> Unit,
    onResetUsage: () -> Unit,
    onSetManualLimits: (model: String, requestsPerDay: Long?, tokensPerMinute: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.settings_api_title),
                icon = Icons.Filled.Key,
                subtitle = stringResource(R.string.settings_api_desc),
            ) {
                BidiTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    singleLine = true,
                    label = stringResource(R.string.settings_api_label),
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.settings_api_toggle_visibility),
                            )
                        }
                    },
                    // A key is Latin letters and digits: it must never be flipped.
                    forceLtr = true,
                )

                Button(
                    onClick = onTest,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = testState != ApiTestState.Running,
                ) {
                    if (testState == ApiTestState.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_api_test))
                }

                when (testState) {
                    is ApiTestState.Success -> NoticeBox(
                        text = stringResource(R.string.settings_api_ok, testState.modelCount),
                        icon = Icons.Filled.CheckCircle,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    is ApiTestState.Failure -> NoticeBox(
                        text = stringResource(testState.messageRes),
                        icon = Icons.Filled.Error,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )

                    else -> Unit
                }

                OutlinedButton(onClick = onOpenApiKeyPage, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_api_get_key))
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.settings_models_title),
                icon = Icons.Filled.Tune,
                subtitle = stringResource(R.string.settings_models_desc),
            ) {
                // One card per model instead of a radio button with a text next to it:
                // the badge «آزمایشی» used to be squeezed against the screen edge and
                // broke in the middle of the word, because the label beside it had
                // already taken the whole row.
                models.forEach { entry ->
                    OptionCard(
                        selected = model == entry.id,
                        onClick = { onModelChange(entry.id) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.titleSmall,
                                // The weight belongs to the label: the badge is then
                                // measured first and always gets the room it needs.
                                // The label itself wraps instead of being cut off
                                // with "…" — «(پیش‌نمایش)» used to lose its end.
                                modifier = Modifier.weight(1f),
                            )
                            if (entry.preview) {
                                Spacer(Modifier.width(8.dp))
                                Pill(
                                    text = stringResource(R.string.badge_preview),
                                    container = MaterialTheme.colorScheme.tertiaryContainer,
                                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            if (model == entry.id) {
                                Spacer(Modifier.width(6.dp))
                                Pill(
                                    text = stringResource(R.string.badge_active),
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = entry.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            // The model id is Latin and must never be flipped.
                            text = entry.id,
                            style = MaterialTheme.typography.labelSmall.ltrDirection(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onRefreshModels,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !modelsLoading,
                ) {
                    if (modelsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_models_refresh))
                }
                NoticeBox(
                    text = stringResource(R.string.settings_models_free_note),
                    icon = Icons.Filled.Lightbulb,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            TokenUsageSection(
                usage = tokenUsage,
                onOpenUsagePage = onOpenUsagePage,
                onResetUsage = onResetUsage,
                onSetManualLimits = onSetManualLimits,
            )
        }

        item {
            SectionCard(
                title = stringResource(R.string.settings_speed_title),
                icon = Icons.Filled.Speed,
                subtitle = stringResource(R.string.settings_speed_desc),
            ) {
                Text(
                    text = stringResource(R.string.settings_concurrency_value, concurrency),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = concurrency.toFloat(),
                    onValueChange = { v ->
                        val next = v.roundToInt()
                        if (next != concurrency) onConcurrencyChange(next)
                    },
                    valueRange = 1f..8f,
                    steps = 6,
                )
                NoticeBox(
                    text = stringResource(R.string.settings_concurrency_hint),
                    icon = Icons.Filled.Bolt,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.settings_batch_value, batchSize),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = batchSize.toFloat(),
                    onValueChange = { v ->
                        val next = v.roundToInt()
                        if (next != batchSize) onBatchSizeChange(next)
                    },
                    valueRange = 5f..50f,
                    steps = 8,
                )
                Text(
                    text = stringResource(R.string.settings_batch_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NoticeBox(
                    text = stringResource(R.string.settings_speed_estimate, concurrency * batchSize),
                    icon = Icons.Filled.Lightbulb,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = SuccessGreen,
                )
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.settings_behaviour_title),
                icon = Icons.Filled.Tune,
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_auto_review_title),
                    description = stringResource(R.string.settings_auto_review_desc),
                    checked = autoReview,
                    onCheckedChange = onAutoReviewChange,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_auto_polish_title),
                    description = stringResource(R.string.settings_auto_polish_desc),
                    checked = autoPolish,
                    onCheckedChange = onAutoPolishChange,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_read_whole_title),
                    description = stringResource(R.string.settings_read_whole_desc),
                    checked = readWholeSubtitle,
                    onCheckedChange = onReadWholeSubtitleChange,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_proper_names_title),
                    description = stringResource(R.string.settings_proper_names_desc),
                    checked = keepProperNames,
                    onCheckedChange = onKeepProperNamesChange,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_wifi_only_title),
                    description = stringResource(R.string.settings_wifi_only_desc),
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChange,
                )
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.settings_bidi_title),
                icon = Icons.Filled.FormatTextdirectionRToL,
                subtitle = stringResource(R.string.settings_bidi_desc),
            ) {
                BidiMode.entries.forEach { mode ->
                    val (labelRes, descRes) = when (mode) {
                        BidiMode.SMART -> R.string.bidi_smart to R.string.bidi_smart_desc
                        BidiMode.ISOLATE -> R.string.bidi_isolate to R.string.bidi_isolate_desc
                        BidiMode.STRONG -> R.string.bidi_strong to R.string.bidi_strong_desc
                        BidiMode.NONE -> R.string.bidi_none to R.string.bidi_none_desc
                    }
                    OptionCard(
                        selected = bidiMode == mode,
                        onClick = { onBidiModeChange(mode) },
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.settings_persian_punct_title),
                    description = stringResource(R.string.settings_persian_punct_desc),
                    checked = persianPunctuation,
                    onCheckedChange = onPersianPunctuationChange,
                )
            }
        }
    }
}
