package com.qwaicode.persiansubtitles.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.data.db.ProjectStatus
import com.qwaicode.persiansubtitles.domain.subtitle.ExportFormat
import com.qwaicode.persiansubtitles.network.GeminiClient
import com.qwaicode.persiansubtitles.ui.tabs.CopyrightTab
import com.qwaicode.persiansubtitles.ui.tabs.EditorTab
import com.qwaicode.persiansubtitles.ui.tabs.SettingsTab
import com.qwaicode.persiansubtitles.ui.tabs.StyleTab
import com.qwaicode.persiansubtitles.ui.tabs.TranslateTab

private enum class AppTab(val labelRes: Int, val icon: ImageVector) {
    TRANSLATE(R.string.tab_translate, Icons.Filled.Translate),
    EDITOR(R.string.tab_editor, Icons.Filled.Edit),
    STYLE(R.string.tab_style, Icons.Filled.Palette),
    COPYRIGHT(R.string.tab_copyright, Icons.Filled.Block),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

/**
 * The whole app. Each area is a separate tab with its own scroll state, so no two
 * sections ever share the screen and nothing can overlap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(AppTab.TRANSLATE) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    // Keeps each tab's own state — above all its scroll position — alive while
    // another tab is on screen.
    val stateHolder = rememberSaveableStateHolder()

    // System back / back gesture leaves the About page instead of closing the app.
    BackHandler(enabled = showAbout) { showAbout = false }

    // Only what the frame itself needs. Everything a tab reads is collected inside
    // that tab's own route, so a progress update no longer recomposes the scaffold.
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val quotaAlert by viewModel.quotaAlert.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()

    // ---- file pickers ---------------------------------------------------
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }

    var pendingFormat by remember { mutableStateOf(ExportFormat.SRT) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.export(it, pendingFormat) } }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = if (message.arg != null) {
                context.getString(message.res, message.arg)
            } else {
                context.getString(message.res)
            }
            // One thing at a time. A snackbar and the "ready" dialog used to appear in
            // the same moment and sit on top of each other; the message now waits for
            // the dialog to be gone, and because the channel buffers, nothing is lost.
            viewModel.completion.first { it == null }
            snackbarHostState.showSnackbar(text)
        }
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    Scaffold(
        // The keyboard shrinks the whole scaffold instead of covering it, so the
        // tab bar stays above the keyboard and every text field can be scrolled
        // into view while typing.
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(if (showAbout) R.string.about_title else R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (showAbout) {
                        // The About page used to be a dead end: the only way out was
                        // tapping the info icon again. It now has a real back arrow
                        // (auto-mirrored, so it points the RTL way), a back button at
                        // the end of the page and the system back gesture.
                        IconButton(onClick = { showAbout = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    } else {
                        IconButton(onClick = { showAbout = true }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = stringResource(R.string.about_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            if (!showAbout) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    AppTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                            label = { Text(stringResource(entry.labelRes), style = MaterialTheme.typography.labelSmall) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showAbout) {
                AboutScreen(
                    onOpenProfile = { openUrl(context.getString(R.string.about_profile_url)) },
                    onBack = { showAbout = false },
                )
                return@Box
            }

            // No cross-fade: an AnimatedContent keeps BOTH tabs composed during the
            // transition, which on a mid-range phone means the heavy editor list is
            // built while another tab is still on screen.
            //
            // The state holder is what makes switching feel instant. Without it the
            // outgoing tab's saved state — above all its scroll position — is thrown
            // away the moment the `when` picks another branch, so coming back rebuilt
            // the list from the top. Each tab now keeps its own state while the app is
            // alive, and only the visible one is composed.
            stateHolder.SaveableStateProvider(tab) {
                when (tab) {
                    AppTab.TRANSLATE -> TranslateRoute(
                        viewModel = viewModel,
                        onImport = {
                            importLauncher.launch(
                                arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")
                            )
                        },
                        onExport = { format ->
                            pendingFormat = format
                            exportLauncher.launch(viewModel.suggestedFileName(format))
                        },
                    )

                    AppTab.EDITOR -> EditorRoute(viewModel)
                    AppTab.STYLE -> StyleRoute(viewModel)
                    AppTab.COPYRIGHT -> CopyrightRoute(viewModel)
                    AppTab.SETTINGS -> SettingsRoute(
                        viewModel = viewModel,
                        onOpenApiKeyPage = { openUrl(GeminiClient.API_KEY_URL) },
                        onOpenUsagePage = { openUrl(context.getString(R.string.usage_page_url)) },
                    )
                }
            }
        }
    }

    // One dialog at a time: the finished-run dialog first, then a limit that was
    // reached, then the analysis progress.
    val currentQuota = quotaAlert
    val currentAnalysis = analysis
    if (completion == null && currentQuota != null) {
        QuotaDialog(
            alert = currentQuota,
            onDismiss = viewModel::dismissQuotaAlert,
            onUseSuggested = { viewModel.useSuggestedModel() },
            onReduceConcurrency = { viewModel.reduceConcurrencyForQuota() },
        )
    } else if (completion == null && currentAnalysis != null) {
        AnalysisDialog(
            state = currentAnalysis,
            onHide = viewModel::hideAnalysis,
            onPause = {
                viewModel.hideAnalysis()
                viewModel.pause()
            },
        )
    }

    completion?.let { info ->
        CompletionDialog(
            info = info,
            onDismiss = viewModel::dismissCompletion,
            onExport = {
                viewModel.dismissCompletion()
                pendingFormat = ExportFormat.SRT
                exportLauncher.launch(viewModel.suggestedFileName(ExportFormat.SRT))
            },
        )
    }
}

/*
 * One route per tab.
 *
 * The point is where the state is collected: this used to happen once at the top of
 * [AppRoot], so a single progress update during a run recomposed the whole scaffold —
 * top bar, tab bar and the visible tab — several times a second. Each route now
 * collects only the flows its own tab reads, and with `collectAsStateWithLifecycle`
 * it stops collecting entirely while the app is in the background.
 */

@Composable
private fun TranslateRoute(
    viewModel: MainViewModel,
    onImport: () -> Unit,
    onExport: (ExportFormat) -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val project by viewModel.project.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val brief by viewModel.contextBrief.collectAsStateWithLifecycle()
    val eta by viewModel.eta.collectAsStateWithLifecycle()

    TranslateTab(
        project = project,
        stats = stats,
        hasApiKey = settings.apiKey.isNotBlank(),
        modelName = models.firstOrNull { it.id == settings.model }?.label ?: settings.model,
        errorMessage = project
            ?.takeIf { it.status == ProjectStatus.ERROR }
            ?.let { p -> viewModel.errorResFor(p.phase)?.let { context.getString(it) } },
        brief = brief,
        eta = eta,
        timeShiftMs = settings.timeShiftMs,
        onShiftTiming = { delta -> viewModel.shiftTiming(delta) },
        onResetTiming = { viewModel.shiftTiming(0, reset = true) },
        language = settings.language,
        onLanguageChange = viewModel::setTargetLanguage,
        onImport = onImport,
        onStart = viewModel::start,
        onPause = viewModel::pause,
        onReview = viewModel::review,
        onPolish = viewModel::polish,
        onRereadSubtitle = viewModel::rereadSubtitle,
        onExport = onExport,
        onClear = viewModel::clearWorkspace,
    )
}

@Composable
private fun EditorRoute(viewModel: MainViewModel) {
    val visibleCues by viewModel.visibleCues.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    EditorTab(
        cues = visibleCues,
        stats = stats,
        query = query,
        filter = filter,
        onQueryChange = viewModel::setQuery,
        onFilterChange = viewModel::setFilter,
        onSave = viewModel::saveEdit,
        onRetranslate = viewModel::retranslateLine,
        onToggleAd = viewModel::toggleAd,
    )
}

@Composable
private fun StyleRoute(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    StyleTab(
        mode = settings.promptMode,
        presetId = settings.presetId,
        customPrompt = settings.customPrompt,
        temperature = settings.temperature,
        autoTone = settings.autoTone,
        autoTonePreset = settings.autoTonePreset,
        readWholeSubtitle = settings.readWholeSubtitle,
        userGlossary = settings.userGlossary,
        onModeChange = viewModel::setPromptMode,
        onPresetChange = viewModel::setPreset,
        onCustomPromptChange = viewModel::setCustomPrompt,
        onTemperatureChange = viewModel::setTemperature,
        onAutoToneChange = viewModel::setAutoTone,
        onUserGlossaryChange = viewModel::setUserGlossary,
    )
}

@Composable
private fun CopyrightRoute(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    CopyrightTab(
        removeAds = settings.removeAds,
        adEdgesOnly = settings.adEdgesOnly,
        adPatterns = settings.adPatterns,
        stripTags = settings.stripTags,
        signatureEnabled = settings.signatureEnabled,
        signatureText = settings.signatureText,
        signatureCount = settings.signatureCount,
        detectedAds = stats.ads,
        onRemoveAdsChange = viewModel::setRemoveAds,
        onEdgesOnlyChange = viewModel::setAdEdgesOnly,
        onPatternsChange = viewModel::setAdPatterns,
        onStripTagsChange = viewModel::setStripTags,
        onSignatureEnabledChange = viewModel::setSignatureEnabled,
        onSignatureTextChange = viewModel::setSignatureText,
        onSignatureCountChange = viewModel::setSignatureCount,
        onRescan = viewModel::rescanAds,
        onRestoreDefaults = viewModel::restoreDefaultPatterns,
    )
}

@Composable
private fun SettingsRoute(
    viewModel: MainViewModel,
    onOpenApiKeyPage: () -> Unit,
    onOpenUsagePage: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tokenUsage by viewModel.tokenUsage.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()

    SettingsTab(
        apiKey = settings.apiKey,
        model = settings.model,
        models = models,
        testState = testState,
        modelsLoading = modelsLoading,
        batchSize = settings.batchSize,
        concurrency = settings.concurrency,
        keepProperNames = settings.keepProperNames,
        autoReview = settings.autoReview,
        autoPolish = settings.autoPolish,
        wifiOnly = settings.wifiOnly,
        readWholeSubtitle = settings.readWholeSubtitle,
        bidiMode = settings.bidiMode,
        persianPunctuation = settings.persianPunctuation,
        onApiKeyChange = viewModel::setApiKey,
        onTest = viewModel::testConnection,
        onRefreshModels = viewModel::refreshModels,
        onModelChange = viewModel::setModel,
        onBatchSizeChange = viewModel::setBatchSize,
        onConcurrencyChange = viewModel::setConcurrency,
        onKeepProperNamesChange = viewModel::setKeepProperNames,
        onAutoReviewChange = viewModel::setAutoReview,
        onAutoPolishChange = viewModel::setAutoPolish,
        onWifiOnlyChange = viewModel::setWifiOnly,
        onReadWholeSubtitleChange = viewModel::setReadWholeSubtitle,
        onBidiModeChange = viewModel::setBidiMode,
        onPersianPunctuationChange = viewModel::setPersianPunctuation,
        onOpenApiKeyPage = onOpenApiKeyPage,
        tokenUsage = tokenUsage,
        onOpenUsagePage = onOpenUsagePage,
        onResetUsage = viewModel::resetTokenUsage,
        onSetManualLimits = viewModel::setManualLimits,
    )
}

/**
 * Reports the finished run. Until now the app only changed a status line, so a
 * user who was not watching the screen never learned that the file was ready.
 */
@Composable
private fun CompletionDialog(
    info: CompletionInfo,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        },
        title = { Text(stringResource(R.string.done_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.done_dialog_message,
                        info.translated,
                        info.total,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.flagged > 0) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.done_dialog_flagged, info.flagged),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (info.skipped > 0) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.done_dialog_skipped, info.skipped),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (info.ads > 0) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.done_dialog_ads, info.ads),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport) { Text(stringResource(R.string.export_srt)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

/** Used by the activity when the app was opened with a subtitle file. */
fun handleIncomingFile(uri: Uri?, viewModel: MainViewModel) {
    uri?.let(viewModel::import)
}
