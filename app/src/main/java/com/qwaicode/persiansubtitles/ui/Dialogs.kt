package com.qwaicode.persiansubtitles.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.domain.prompt.StylePresets
import com.qwaicode.persiansubtitles.fa
import com.qwaicode.persiansubtitles.toPersianDigits
import kotlinx.coroutines.delay
import kotlin.math.exp

/** How long the "prepare the text" stage is shown before the request stage. */
private const val READ_STAGE_MS = 1_500L

/**
 * Shown while the AI reads the whole subtitle before translating.
 *
 * Before this existed the app simply sat there for up to half a minute after
 * "start" with nothing moving, which looked exactly like a frozen app. The dialog
 * shows the three stages, a progress bar, the elapsed time and an estimate, and it
 * says so when Gemini makes us wait for a per-minute limit. It can be sent to the
 * background (the analysis continues) or used to pause the run.
 */
@Composable
fun AnalysisDialog(
    state: AnalysisProgress,
    onHide: () -> Unit,
    onPause: () -> Unit,
) {
    // A clock for the elapsed time; it stops at the moment the analysis ended.
    val now by produceState(System.currentTimeMillis(), state.startedAt, state.finished) {
        if (state.finished) {
            value = state.finishedAt
        } else {
            while (true) {
                value = System.currentTimeMillis()
                delay(250)
            }
        }
    }
    val elapsed = (now - state.startedAt).coerceAtLeast(0L)

    val target = when {
        state.finished -> 1f
        else -> {
            val read = (elapsed.toFloat() / READ_STAGE_MS).coerceAtMost(1f) * 0.08f
            // Approaches the end asymptotically: the bar keeps moving, but only the
            // real answer fills it.
            val t = (elapsed - READ_STAGE_MS).coerceAtLeast(0L).toFloat() / state.expectedMs.coerceAtLeast(1L)
            val send = (1.0 - exp(-2.2 * t)).toFloat() * 0.86f
            (read + send).coerceIn(0f, 0.95f)
        }
    }
    val progress by animateFloatAsState(targetValue = target, animationSpec = tween(400), label = "analysis")

    val stage = when {
        state.finished -> 3
        elapsed < READ_STAGE_MS -> 0
        else -> 1
    }

    AlertDialog(
        // Back hides the dialog like the button does; the analysis keeps running.
        onDismissRequest = onHide,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
        icon = {
            when {
                state.finished && state.succeeded -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                state.finished -> Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp),
                )
                else -> Icon(
                    Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text(
                stringResource(
                    when {
                        state.finished && state.succeeded -> R.string.analysis_done_title
                        state.finished -> R.string.analysis_failed_title
                        else -> R.string.analysis_title
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.finished) {
                    Text(
                        text = stringResource(R.string.analysis_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    strokeCap = StrokeCap.Round,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "٪" + (progress * 100).toInt().fa(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.analysis_elapsed, clock(elapsed)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!state.finished) {
                    Text(
                        text = stringResource(R.string.analysis_estimate, clock(state.expectedMs + READ_STAGE_MS)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                StageRow(stringResource(R.string.analysis_step_read, state.totalCues.fa()), done = stage > 0, active = stage == 0)
                StageRow(stringResource(R.string.analysis_step_send), done = stage > 1, active = stage == 1)
                StageRow(stringResource(R.string.analysis_step_apply), done = stage > 2, active = stage == 2)

                if (!state.finished && state.waitingForQuota) {
                    Hint(stringResource(R.string.analysis_waiting_quota), Icons.Filled.HourglassTop)
                } else if (!state.finished && elapsed > state.expectedMs * 3 / 2 + READ_STAGE_MS) {
                    Hint(stringResource(R.string.analysis_slow), Icons.Filled.HourglassTop)
                }

                if (state.finished) {
                    if (state.succeeded) {
                        state.title?.let {
                            Text(
                                text = stringResource(R.string.analysis_done_found, it),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        StylePresets.find(state.tonePresetId)?.let { preset ->
                            Text(
                                text = stringResource(R.string.analysis_done_tone, preset.title),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (state.title == null && StylePresets.find(state.tonePresetId) == null) {
                            Text(
                                text = stringResource(R.string.analysis_done_generic),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.analysis_failed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onHide) {
                Text(stringResource(if (state.finished) R.string.action_ok else R.string.action_hide_background))
            }
        },
        dismissButton = if (state.finished) {
            null
        } else {
            {
                TextButton(onClick = onPause) { Text(stringResource(R.string.action_pause)) }
            }
        },
    )
}

@Composable
private fun StageRow(text: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        when {
            done -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            active -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            else -> Icon(
                Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Hint(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The "limit reached" dialog.
 *
 * Two different situations, two different messages: the per-minute limit clears by
 * itself and the app keeps going, so the dialog only explains and offers to slow
 * down; the daily limit stops the run, so the dialog says when the quota comes back
 * and offers to continue right away on another free model.
 */
@Composable
fun QuotaDialog(
    alert: QuotaAlert,
    onDismiss: () -> Unit,
    onUseSuggested: () -> Unit,
    onReduceConcurrency: () -> Unit,
) {
    val suggestion = alert.suggestedModelLabel
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (alert.daily) Icons.Filled.Warning else Icons.Filled.HourglassTop,
                contentDescription = null,
                tint = if (alert.daily) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(stringResource(if (alert.daily) R.string.quota_daily_title else R.string.quota_minute_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (alert.daily) {
                    Text(
                        text = stringResource(R.string.quota_daily_message, alert.modelLabel),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.quota_reset_in, duration(alert.resetInMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (suggestion != null) {
                            stringResource(R.string.quota_suggest_model, suggestion)
                        } else {
                            stringResource(R.string.quota_no_suggestion)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.quota_minute_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (alert.concurrency > 2) {
                        Text(
                            text = stringResource(R.string.quota_minute_tip_concurrency, alert.concurrency.fa()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (suggestion != null) {
                        Text(
                            text = stringResource(R.string.quota_minute_tip_model, suggestion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                suggestion != null -> TextButton(onClick = onUseSuggested) {
                    Text(
                        stringResource(
                            if (alert.daily && alert.canResume) R.string.action_continue_with else R.string.action_use_model,
                            suggestion,
                        )
                    )
                }
                !alert.daily && alert.concurrency > 1 -> TextButton(onClick = onReduceConcurrency) {
                    Text(stringResource(R.string.action_reduce_concurrency))
                }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = if (suggestion != null || (!alert.daily && alert.concurrency > 1)) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(if (alert.daily) R.string.action_later else R.string.action_ok))
                }
            }
        } else {
            null
        },
    )
}

/** `83_000` → «۰۱:۲۳». */
private fun clock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val text = String.format(java.util.Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    return text.toPersianDigits()
}

/** «۵ ساعت و ۱۲ دقیقه» / «۴۰ دقیقه». */
@Composable
private fun duration(ms: Long): String {
    val minutes = ((ms + 59_999) / 60_000).toInt().coerceAtLeast(1)
    return if (minutes >= 60) {
        stringResource(R.string.quota_duration_hm, (minutes / 60).fa(), (minutes % 60).fa())
    } else {
        stringResource(R.string.quota_duration_m, minutes.fa())
    }
}
