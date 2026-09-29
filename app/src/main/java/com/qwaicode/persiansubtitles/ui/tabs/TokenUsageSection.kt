package com.qwaicode.persiansubtitles.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.toPersianDigits
import com.qwaicode.persiansubtitles.ui.LimitUi
import com.qwaicode.persiansubtitles.ui.ModelUsageUi
import com.qwaicode.persiansubtitles.ui.TokenUsageUi
import com.qwaicode.persiansubtitles.ui.components.NoticeBox
import com.qwaicode.persiansubtitles.ui.components.Pill
import com.qwaicode.persiansubtitles.ui.components.SectionCard
import com.qwaicode.persiansubtitles.ui.components.ltrDirection
import com.qwaicode.persiansubtitles.ui.theme.Amber300
import com.qwaicode.persiansubtitles.ui.theme.ErrorRed
import com.qwaicode.persiansubtitles.ui.theme.SuccessGreen

/**
 * Token and request usage of the Gemini key, per model.
 *
 * The numbers are Google's own: every answer carries a `usageMetadata` block and
 * the app adds those up. The limits come from Google as well — a refused request
 * names the limit it hit and its value — or from the user, because Google offers no
 * endpoint that reports the remaining quota of an API key.
 */
@Composable
fun TokenUsageSection(
    usage: TokenUsageUi,
    onOpenUsagePage: () -> Unit,
    onResetUsage: () -> Unit,
    onSetManualLimits: (model: String, requestsPerDay: Long?, tokensPerMinute: Long?) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    SectionCard(
        title = stringResource(R.string.usage_title),
        icon = Icons.Filled.DataUsage,
        subtitle = stringResource(R.string.usage_desc),
    ) {
        usage.models.forEach { model ->
            ModelUsageCard(model = model, onEditLimits = { editing = model.id })
        }

        NoticeBox(
            text = stringResource(R.string.usage_reset_in, formatDuration(usage.resetInMs)),
            icon = Icons.Filled.RestartAlt,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NoticeBox(
            text = stringResource(R.string.usage_source_note),
            icon = Icons.Filled.Info,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FilledTonalButton(onClick = onOpenUsagePage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.usage_open_ai_studio))
        }
        OutlinedButton(onClick = onResetUsage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.usage_reset))
        }
    }

    val target = editing?.let { id -> usage.models.firstOrNull { it.id == id } }
    if (target != null) {
        LimitsDialog(
            model = target,
            onDismiss = { editing = null },
            onSave = { rpd, tpm ->
                onSetManualLimits(target.id, rpd, tpm)
                editing = null
            },
        )
    }
}

@Composable
private fun ModelUsageCard(model: ModelUsageUi, onEditLimits: () -> Unit) {
    val border = if (model.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .border(1.dp, border.copy(alpha = 0.6f), MaterialTheme.shapes.small)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = model.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.labelSmall.ltrDirection(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (model.selected) {
                Spacer(Modifier.width(8.dp))
                Pill(
                    text = stringResource(R.string.badge_active),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        UsageMeter(
            title = stringResource(R.string.usage_requests_today),
            used = model.requestsToday,
            limit = model.requestsPerDay,
        )
        model.inputTokensPerDay?.let { limit ->
            UsageMeter(
                title = stringResource(R.string.usage_input_tokens_today),
                used = model.promptTokens,
                limit = limit,
            )
        }
        UsageMeter(
            title = stringResource(R.string.usage_requests_minute),
            used = model.requestsLastMinute.toLong(),
            limit = model.requestsPerMinute,
        )
        UsageMeter(
            title = stringResource(R.string.usage_tokens_minute),
            used = model.inputTokensLastMinute,
            limit = model.inputTokensPerMinute,
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.usage_total_today, grouped(model.totalTokens)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.usage_breakdown,
                    grouped(model.promptTokens),
                    grouped(model.outputTokens),
                    grouped(model.thoughtTokens),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = onEditLimits, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.usage_set_limits))
        }
    }
}

/** One counter with its limit: a bar, "used of limit" and what is left. */
@Composable
private fun UsageMeter(title: String, used: Long, limit: LimitUi?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (limit?.manual == true) {
                Pill(
                    text = stringResource(R.string.usage_limit_manual),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (limit != null && limit.value > 0) {
            val fraction = (used.toFloat() / limit.value).coerceIn(0f, 1f)
            val color: Color = when {
                fraction >= 0.9f -> ErrorRed
                fraction >= 0.7f -> Amber300
                else -> SuccessGreen
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )
            Text(
                text = stringResource(
                    R.string.usage_used_of,
                    grouped(used),
                    grouped(limit.value),
                    grouped((limit.value - used).coerceAtLeast(0)),
                    (fraction * 100).toInt().toString().toPersianDigits(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.usage_used_no_limit, grouped(used)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LimitsDialog(
    model: ModelUsageUi,
    onDismiss: () -> Unit,
    onSave: (requestsPerDay: Long?, tokensPerMinute: Long?) -> Unit,
) {
    var rpd by remember(model.id) {
        mutableStateOf(model.requestsPerDay?.takeIf { it.manual }?.value?.toString().orEmpty())
    }
    var tpm by remember(model.id) {
        mutableStateOf(model.inputTokensPerMinute?.takeIf { it.manual }?.value?.toString().orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
        title = { Text(stringResource(R.string.usage_limits_dialog_title, model.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.usage_limits_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = rpd,
                    onValueChange = { rpd = digitsOnly(it) },
                    label = { Text(stringResource(R.string.usage_limit_rpd)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.ltrDirection(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tpm,
                    onValueChange = { tpm = digitsOnly(it) },
                    label = { Text(stringResource(R.string.usage_limit_tpm)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.ltrDirection(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(rpd.toLongOrNull(), tpm.toLongOrNull()) }) {
                Text(stringResource(R.string.usage_limits_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Keeps digits only; Persian and Arabic-Indic digits typed on a Persian keyboard become ASCII. */
private fun digitsOnly(input: String): String = buildString {
    for (ch in input) {
        when (ch) {
            in '0'..'9' -> append(ch)
            in '۰'..'۹' -> append('0' + (ch - '۰'))
            in '٠'..'٩' -> append('0' + (ch - '٠'))
        }
    }
}.take(12)

/** `1234567` → `۱٬۲۳۴٬۵۶۷`. */
private fun grouped(value: Long): String {
    val digits = value.coerceAtLeast(0).toString()
    return digits.reversed().chunked(3).joinToString("\u066C").reversed().toPersianDigits()
}

@Composable
private fun formatDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return stringResource(
        R.string.usage_duration,
        hours.toString().toPersianDigits(),
        minutes.toString().toPersianDigits(),
    )
}
