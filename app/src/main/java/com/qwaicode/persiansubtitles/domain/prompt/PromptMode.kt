package com.qwaicode.persiansubtitles.domain.prompt

/**
 * Which instruction decides the style of the translation.
 *
 * The two are mutually exclusive on purpose. Before this existed, a user who wrote
 * their own prompt still had a tone preset selected underneath, and both were sent
 * to Gemini — so the model got two style instructions at once and the preset kept
 * bleeding into a translation the user thought they had fully specified themselves.
 */
enum class PromptMode(val id: String) {

    /** A ready-made tone preset; a custom prompt, if present, is added on top. */
    PRESET("preset"),

    /** Only the user's own prompt. No preset is sent, and none is selected in the UI. */
    CUSTOM_ONLY("custom");

    companion object {
        fun byId(id: String?): PromptMode = entries.firstOrNull { it.id == id } ?: PRESET
    }
}
