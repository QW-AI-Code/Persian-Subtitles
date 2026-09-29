package com.qwaicode.persiansubtitles.domain.ai

import com.qwaicode.persiansubtitles.domain.prompt.StylePresets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What the app learned by reading the whole subtitle before translating a single
 * line.
 *
 * Batch translation without this is the reason a long film reads inconsistently:
 * batch 3 has no idea that "Emma" is the housekeeper and not a guest, that the two
 * characters address each other informally, or that "the estate" was translated as
 * «عمارت» forty lines earlier. The brief is produced once, stored with the project
 * and put into the prompt of *every* batch, so the whole file uses one set of
 * decisions.
 */
@Serializable
data class ContextBrief(
    val title: String = "",
    val genre: String = "",
    val setting: String = "",
    val summary: String = "",
    val tone: String = "",
    /** Formal or colloquial Persian — the single biggest consistency lever. */
    val formality: String = "",
    val characters: List<Character> = emptyList(),
    val glossary: List<Term> = emptyList(),
    val notes: String = "",
    /**
     * The tone preset the model picked after reading the film — one of the ids of
     * [com.qwaicode.persiansubtitles.domain.prompt.StylePresets]. Blank when the
     * answer named none or an unknown one.
     */
    val tonePreset: String = "",
    /** One short sentence: why that tone fits this film. Shown to the user. */
    val toneReason: String = "",
) {

    @Serializable
    data class Character(
        val name: String = "",
        val persian: String = "",
        val note: String = "",
    )

    @Serializable
    data class Term(
        val source: String = "",
        val persian: String = "",
    )

    val isEmpty: Boolean
        get() = summary.isBlank() && characters.isEmpty() && glossary.isEmpty()

    /**
     * The block that goes into the system instruction of every batch.
     *
     * It is capped, because it is sent again with each of the hundred-odd requests
     * of a film: a brief that grows without limit would eat the token budget the
     * actual dialogue needs. The order below is the order of usefulness, and
     * whatever no longer fits is dropped from the end.
     */
    fun toPromptBlock(maxChars: Int = MAX_PROMPT_CHARS, languageName: String = "فارسی"): String {
        if (isEmpty) return ""

        val parts = mutableListOf<String>()
        parts += "### شناخت اثر (حاصل مطالعهٔ کل زیرنویس، پیش از ترجمه)"
        if (title.isNotBlank()) parts += "عنوان: ${title.trim()}"
        if (genre.isNotBlank() || setting.isNotBlank()) {
            parts += listOfNotNull(
                genre.trim().takeIf { it.isNotBlank() }?.let { "گونه: $it" },
                setting.trim().takeIf { it.isNotBlank() }?.let { "فضا: $it" },
            ).joinToString(" | ")
        }
        if (summary.isNotBlank()) parts += "خلاصهٔ داستان: ${summary.trim()}"
        if (tone.isNotBlank()) parts += "لحن: ${tone.trim()}"
        if (formality.isNotBlank()) parts += "سطح زبان: ${formality.trim()}"

        if (characters.isNotEmpty()) {
            parts += "شخصیت‌ها (معادل $languageName هر نام را همیشه همین‌طور بنویس):"
            characters.forEach { c ->
                val name = c.name.trim().ifBlank { return@forEach }
                val fa = c.persian.trim().ifBlank { name }
                val note = c.note.trim().takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                parts += "- $name = $fa$note"
            }
        }

        if (glossary.isNotEmpty()) {
            parts += "اصطلاح‌های ثابت (در تمام فایل یکسان ترجمه شوند):"
            glossary.forEach { t ->
                val source = t.source.trim().ifBlank { return@forEach }
                val fa = t.persian.trim().ifBlank { return@forEach }
                parts += "- $source = $fa"
            }
        }

        if (notes.isNotBlank()) parts += "یادداشت: ${notes.trim()}"

        // Trim from the end until it fits, never mid-line.
        val kept = mutableListOf<String>()
        var length = 0
        for (part in parts) {
            val next = length + part.length + 1
            if (next > maxChars && kept.size > 1) break
            kept += part
            length = next
        }
        return kept.joinToString("\n")
    }

    companion object {
        const val MAX_PROMPT_CHARS = 1600

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        fun toStorage(brief: ContextBrief): String = json.encodeToString(serializer(), brief)

        fun fromStorage(raw: String?): ContextBrief? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
        }

        /**
         * Reads the model's answer. Written to survive what models actually send:
         * a code fence around the JSON, English key names instead of the asked-for
         * ones, a single string where a list was requested, a missing field.
         * A brief is a nice-to-have — it must never abort a translation.
         */
        fun parse(raw: String): ContextBrief? {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val root = runCatching { json.parseToJsonElement(cleaned) }.getOrNull() ?: return null
            val obj = when (root) {
                is JsonObject -> root
                is JsonArray -> root.firstOrNull() as? JsonObject ?: return null
                else -> return null
            }

            val brief = ContextBrief(
                title = obj.text("title", "name", "film", "movie"),
                genre = obj.text("genre", "type"),
                setting = obj.text("setting", "place", "world"),
                summary = obj.text("summary", "plot", "synopsis", "story"),
                tone = obj.text("tone", "style", "mood"),
                formality = obj.text("formality", "register", "address"),
                characters = obj.list("characters", "people", "cast").mapNotNull { item ->
                    val o = item as? JsonObject ?: return@mapNotNull null
                    val name = o.text("name", "en", "source", "original")
                    if (name.isBlank()) return@mapNotNull null
                    Character(
                        name = name,
                        persian = o.text("persian", "fa", "translation", "target"),
                        note = o.text("note", "role", "description"),
                    )
                },
                glossary = obj.list("glossary", "terms", "vocabulary").mapNotNull { item ->
                    val o = item as? JsonObject ?: return@mapNotNull null
                    val source = o.text("source", "en", "term", "original")
                    val fa = o.text("persian", "fa", "translation", "target")
                    if (source.isBlank() || fa.isBlank()) return@mapNotNull null
                    Term(source = source, persian = fa)
                },
                notes = obj.text("notes", "note", "warnings"),
                // Only a preset the app actually has is kept; a made-up name must
                // never end up in the settings.
                tonePreset = StylePresets.find(
                    obj.text("tone_preset", "preset", "style_preset", "tonePreset", "translation_style"),
                )?.id.orEmpty(),
                toneReason = obj.text("tone_reason", "preset_reason", "toneReason", "reason"),
            )
            return brief.takeUnless { it.isEmpty }
        }

        private fun JsonObject.text(vararg keys: String): String {
            keys.forEach { key ->
                val value = this[key]
                if (value is JsonPrimitive) {
                    val content = value.contentOrNull?.trim()
                    if (!content.isNullOrBlank() && content != "null") return content
                }
                if (value is JsonArray) {
                    val joined = value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .joinToString("، ")
                        .trim()
                    if (joined.isNotBlank()) return joined
                }
                if (value is JsonObject) {
                    val joined = value.values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .joinToString("، ")
                        .trim()
                    if (joined.isNotBlank()) return joined
                }
            }
            return ""
        }

        private fun JsonObject.list(vararg keys: String): List<kotlinx.serialization.json.JsonElement> {
            keys.forEach { key ->
                (this[key] as? JsonArray)?.let { if (it.isNotEmpty()) return it }
                // Some answers nest the list one level deeper: {"characters":{"list":[…]}}
                (this[key] as? JsonObject)?.let { nested ->
                    nested.values.firstOrNull { it is JsonArray }?.let { return (it as JsonArray) }
                }
            }
            return emptyList()
        }
    }
}
