package com.qwaicode.persiansubtitles.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguage
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguages
import com.qwaicode.persiansubtitles.domain.prompt.PromptMode
import com.qwaicode.persiansubtitles.domain.subtitle.CopyrightCleaner
import com.qwaicode.persiansubtitles.domain.subtitle.SignatureInserter
import com.qwaicode.persiansubtitles.domain.text.BidiMode
import com.qwaicode.persiansubtitles.network.FreeModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "persian_subtitles_settings")

/** Everything the user can configure. Persisted with DataStore. */
data class AppSettings(
    val apiKey: String = "",
    val model: String = FreeModelCatalog.defaultModel,
    val presetId: String = "fluent",
    val customPrompt: String = "",
    /** Preset plus custom prompt, or the custom prompt alone. */
    val promptMode: PromptMode = PromptMode.PRESET,
    val temperature: Float = 0.65f,
    val batchSize: Int = 20,
    /** Parallel requests to Gemini. Higher = faster, but the free quota is hit sooner. */
    val concurrency: Int = 4,
    val keepProperNames: Boolean = true,
    val autoReview: Boolean = true,
    /**
     * Run the editorial scan after the translation: unify the Persian letters and
     * spacing locally, and send the lines that are still wrong (an English word left
     * standing, a truncated sentence) back to the model.
     */
    val autoPolish: Boolean = true,
    val removeAds: Boolean = true,
    val adEdgesOnly: Boolean = true,
    val adPatterns: String = CopyrightCleaner.DEFAULT_PATTERNS_TEXT,
    val stripTags: Boolean = false,
    val signatureEnabled: Boolean = false,
    /** Pre-filled with the project's own credit; the user can replace it. */
    val signatureText: String = SignatureInserter.DEFAULT_TEXT,
    /** How many credit lines are spread over the subtitle. */
    val signatureCount: Int = SignatureInserter.DEFAULT_COUNT,
    val wifiOnly: Boolean = false,
    /**
     * How the exported file marks the direction of a Persian line. Configurable
     * because players disagree about how much of the BiDi algorithm they implement.
     */
    val bidiMode: BidiMode = BidiMode.SMART,
    /** `,` `;` `?` become `،` `؛` `؟` in Persian lines of the exported file. */
    val persianPunctuation: Boolean = true,
    /** Read the whole subtitle once before translating, for a consistent result. */
    val readWholeSubtitle: Boolean = true,
    /**
     * The language the subtitle is translated into, as a code of
     * [com.qwaicode.persiansubtitles.domain.lang.TargetLanguages]. Persian by default.
     */
    val targetLanguage: String = TargetLanguages.PERSIAN_CODE,
    val cachedModels: String = "",
    /**
     * After reading the whole subtitle, let the AI pick the tone preset that fits the
     * film and switch the setting to it.
     */
    val autoTone: Boolean = true,
    /** The preset the AI picked last; drives the "chosen automatically" badge. */
    val autoTonePreset: String = "",
    /**
     * The user's own fixed terms, one per line: `source = translation`. Sent with
     * every batch and ranked above the brief the model built itself.
     */
    val userGlossary: String = "",
    /**
     * Moves every cue of the exported file by this many milliseconds, for a
     * subtitle that runs early or late against the video. Negative = earlier.
     */
    val timeShiftMs: Int = 0,
) {
    /** The resolved target language; an unknown code falls back to Persian. */
    val language: TargetLanguage get() = TargetLanguages.byCode(targetLanguage)
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val presetId = stringPreferencesKey("preset_id")
        val customPrompt = stringPreferencesKey("custom_prompt")
        val promptMode = stringPreferencesKey("prompt_mode")
        val temperature = floatPreferencesKey("temperature")
        val batchSize = intPreferencesKey("batch_size")
        val concurrency = intPreferencesKey("concurrency")
        val keepProperNames = booleanPreferencesKey("keep_proper_names")
        val autoReview = booleanPreferencesKey("auto_review")
        val autoPolish = booleanPreferencesKey("auto_polish")
        val removeAds = booleanPreferencesKey("remove_ads")
        val adEdgesOnly = booleanPreferencesKey("ad_edges_only")
        val adPatterns = stringPreferencesKey("ad_patterns")
        val stripTags = booleanPreferencesKey("strip_tags")
        val signatureEnabled = booleanPreferencesKey("signature_enabled")
        val signatureText = stringPreferencesKey("signature_text")
        val signatureCount = intPreferencesKey("signature_count")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val bidiMode = stringPreferencesKey("bidi_mode")
        val persianPunctuation = booleanPreferencesKey("persian_punctuation")
        val readWholeSubtitle = booleanPreferencesKey("read_whole_subtitle")
        val cachedModels = stringPreferencesKey("cached_models")
        val targetLanguage = stringPreferencesKey("target_language")
        val autoTone = booleanPreferencesKey("auto_tone")
        val autoTonePreset = stringPreferencesKey("auto_tone_preset")
        val userGlossary = stringPreferencesKey("user_glossary")
        val timeShiftMs = intPreferencesKey("time_shift_ms")

        /** Which key the cached model list belongs to (a hash, never the key itself). */
        val modelsKeyFingerprint = stringPreferencesKey("models_key_fingerprint")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            apiKey = p[Keys.apiKey] ?: defaults.apiKey,
            // A model saved by an older build may no longer be offered — fall back
            // instead of sending requests that the free key rejects.
            model = p[Keys.model]?.takeIf { FreeModelCatalog.isFree(it) } ?: defaults.model,
            presetId = p[Keys.presetId] ?: defaults.presetId,
            customPrompt = p[Keys.customPrompt] ?: defaults.customPrompt,
            promptMode = PromptMode.byId(p[Keys.promptMode]),
            temperature = p[Keys.temperature] ?: defaults.temperature,
            batchSize = p[Keys.batchSize] ?: defaults.batchSize,
            concurrency = p[Keys.concurrency] ?: defaults.concurrency,
            keepProperNames = p[Keys.keepProperNames] ?: defaults.keepProperNames,
            autoReview = p[Keys.autoReview] ?: defaults.autoReview,
            autoPolish = p[Keys.autoPolish] ?: defaults.autoPolish,
            removeAds = p[Keys.removeAds] ?: defaults.removeAds,
            adEdgesOnly = p[Keys.adEdgesOnly] ?: defaults.adEdgesOnly,
            adPatterns = p[Keys.adPatterns] ?: defaults.adPatterns,
            stripTags = p[Keys.stripTags] ?: defaults.stripTags,
            signatureEnabled = p[Keys.signatureEnabled] ?: defaults.signatureEnabled,
            signatureText = p[Keys.signatureText] ?: defaults.signatureText,
            signatureCount = p[Keys.signatureCount] ?: defaults.signatureCount,
            wifiOnly = p[Keys.wifiOnly] ?: defaults.wifiOnly,
            bidiMode = BidiMode.byId(p[Keys.bidiMode]),
            persianPunctuation = p[Keys.persianPunctuation] ?: defaults.persianPunctuation,
            readWholeSubtitle = p[Keys.readWholeSubtitle] ?: defaults.readWholeSubtitle,
            targetLanguage = TargetLanguages.byCode(p[Keys.targetLanguage]).code,
            cachedModels = p[Keys.cachedModels] ?: defaults.cachedModels,
            autoTone = p[Keys.autoTone] ?: defaults.autoTone,
            autoTonePreset = p[Keys.autoTonePreset] ?: defaults.autoTonePreset,
            userGlossary = p[Keys.userGlossary] ?: defaults.userGlossary,
            timeShiftMs = p[Keys.timeShiftMs] ?: defaults.timeShiftMs,
        )
    }

    suspend fun setApiKey(value: String) = put { it[Keys.apiKey] = value.trim() }
    suspend fun setModel(value: String) = put { it[Keys.model] = value }
    suspend fun setPreset(value: String) = put { it[Keys.presetId] = value }
    suspend fun setCustomPrompt(value: String) = put { it[Keys.customPrompt] = value }
    suspend fun setPromptMode(value: PromptMode) = put { it[Keys.promptMode] = value.id }
    suspend fun setTemperature(value: Float) = put { it[Keys.temperature] = value }
    suspend fun setBatchSize(value: Int) = put { it[Keys.batchSize] = value }
    suspend fun setConcurrency(value: Int) = put { it[Keys.concurrency] = value.coerceIn(1, 8) }
    suspend fun setKeepProperNames(value: Boolean) = put { it[Keys.keepProperNames] = value }
    suspend fun setAutoReview(value: Boolean) = put { it[Keys.autoReview] = value }
    suspend fun setAutoPolish(value: Boolean) = put { it[Keys.autoPolish] = value }
    suspend fun setRemoveAds(value: Boolean) = put { it[Keys.removeAds] = value }
    suspend fun setAdEdgesOnly(value: Boolean) = put { it[Keys.adEdgesOnly] = value }
    suspend fun setAdPatterns(value: String) = put { it[Keys.adPatterns] = value }
    suspend fun setStripTags(value: Boolean) = put { it[Keys.stripTags] = value }
    suspend fun setSignatureEnabled(value: Boolean) = put { it[Keys.signatureEnabled] = value }
    suspend fun setSignatureText(value: String) = put { it[Keys.signatureText] = value }
    suspend fun setSignatureCount(value: Int) = put {
        it[Keys.signatureCount] = value.coerceIn(SignatureInserter.MIN_COUNT, SignatureInserter.MAX_COUNT)
    }
    suspend fun setWifiOnly(value: Boolean) = put { it[Keys.wifiOnly] = value }
    suspend fun setBidiMode(value: BidiMode) = put { it[Keys.bidiMode] = value.id }
    suspend fun setPersianPunctuation(value: Boolean) = put { it[Keys.persianPunctuation] = value }
    suspend fun setReadWholeSubtitle(value: Boolean) = put { it[Keys.readWholeSubtitle] = value }
    suspend fun setTargetLanguage(code: String) = put { it[Keys.targetLanguage] = TargetLanguages.byCode(code).code }
    suspend fun setCachedModels(value: String) = put { it[Keys.cachedModels] = value }
    suspend fun setAutoTone(value: Boolean) = put { it[Keys.autoTone] = value }

    /** Switches the tone to what the AI picked, and remembers that it was the AI. */
    suspend fun applyAutoTone(presetId: String) = put {
        it[Keys.presetId] = presetId
        it[Keys.autoTonePreset] = presetId
    }

    /**
     * Forgets everything the AI decided for the previous subtitle.
     *
     * The tone the AI picked is stored in the settings (so the Style tab can show
     * it), which is exactly why it used to survive "clear workspace": the next film
     * opened with the old film's tone still ticked and still badged «انتخاب هوش
     * مصنوعی». The preset only goes back to the default if it is still the one the
     * AI chose — a tone the user picked by hand is their decision and stays.
     * The sync offset belongs to one file as well and is reset with it.
     */
    suspend fun resetForNewSubtitle() = put {
        val chosenByAi = it[Keys.autoTonePreset]
        if (!chosenByAi.isNullOrBlank() && it[Keys.presetId] == chosenByAi) {
            it.remove(Keys.presetId)
        }
        it.remove(Keys.autoTonePreset)
        it.remove(Keys.timeShiftMs)
    }

    /**
     * Stores a freshly loaded model list. When the list was loaded for a key that
     * was not seen before — the user just entered it — the recommended free model
     * is selected; afterwards a refresh keeps whatever the user picked, unless that
     * model is no longer offered.
     */
    suspend fun applyLoadedModels(apiKey: String, encodedModels: String, preferredModel: String?, offered: Set<String>) = put {
        val fingerprint = fingerprint(apiKey)
        val newKey = it[Keys.modelsKeyFingerprint] != fingerprint
        it[Keys.cachedModels] = encodedModels
        it[Keys.modelsKeyFingerprint] = fingerprint
        val current = it[Keys.model]
        if (preferredModel != null && (newKey || current == null || current !in offered)) {
            it[Keys.model] = preferredModel
        }
    }

    suspend fun setUserGlossary(value: String) = put { it[Keys.userGlossary] = value }
    suspend fun setTimeShiftMs(value: Int) = put {
        it[Keys.timeShiftMs] = value.coerceIn(-MAX_TIME_SHIFT_MS, MAX_TIME_SHIFT_MS)
    }

    companion object {
        /** Ten minutes either way is far beyond any real sync problem. */
        const val MAX_TIME_SHIFT_MS = 600_000

        private fun fingerprint(apiKey: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(apiKey.trim().toByteArray(Charsets.UTF_8))
            return digest.take(12).joinToString("") { "%02x".format(it) }
        }
    }

    private suspend inline fun put(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
