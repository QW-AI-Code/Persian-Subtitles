package com.qwaicode.persiansubtitles.domain.lang

/**
 * The writing system of a target language.
 *
 * It decides three things the rest of the app used to hard-code for Persian: which
 * letters prove that a line was actually translated, whether the exported file needs
 * right-to-left marks, and whether `,` `;` `?` are replaced by their Arabic-script
 * forms.
 */
enum class Script {
    ARABIC, HEBREW, LATIN, CYRILLIC, GREEK, HAN, JAPANESE, HANGUL, DEVANAGARI, BENGALI, THAI;

    /** True when a character belongs to this script. */
    fun owns(ch: Char): Boolean = when (this) {
        ARABIC -> ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFF'
        HEBREW -> ch in '\u0590'..'\u05FF'
        LATIN -> ch in 'A'..'Z' || ch in 'a'..'z' || ch in '\u00C0'..'\u024F' || ch in '\u1E00'..'\u1EFF'
        CYRILLIC -> ch in '\u0400'..'\u04FF'
        GREEK -> ch in '\u0370'..'\u03FF' || ch in '\u1F00'..'\u1FFF'
        HAN -> ch in '\u4E00'..'\u9FFF' || ch in '\u3400'..'\u4DBF'
        JAPANESE -> ch in '\u3040'..'\u30FF' || ch in '\u4E00'..'\u9FFF'
        HANGUL -> ch in '\uAC00'..'\uD7AF' || ch in '\u1100'..'\u11FF' || ch in '\u3130'..'\u318F'
        DEVANAGARI -> ch in '\u0900'..'\u097F'
        BENGALI -> ch in '\u0980'..'\u09FF'
        THAI -> ch in '\u0E00'..'\u0E7F'
    }

    /** Right-to-left scripts get direction marks in the exported file. */
    val rtl: Boolean get() = this == ARABIC || this == HEBREW

    /**
     * Ideographic and syllabic scripts pack a sentence into far fewer characters than
     * English, so "shorter than a quarter of the source" does not mean "truncated".
     */
    val compact: Boolean get() = this == HAN || this == JAPANESE || this == HANGUL
}

/**
 * A language the subtitle can be translated into.
 *
 * @param code BCP-47 code; also used as the suffix of the exported file (`movie.de.srt`).
 * @param flag the country flag shown next to the name in the picker.
 * @param nameFa the name as the Persian UI shows it.
 * @param nameEn the English name, put into the prompt so the model cannot misread it.
 * @param nativeName the name in the language itself, shown under the Persian name.
 */
data class TargetLanguage(
    val code: String,
    val flag: String,
    val nameFa: String,
    val nameEn: String,
    val nativeName: String,
    val script: Script,
) {
    val isPersian: Boolean get() = code == TargetLanguages.PERSIAN_CODE
    val rtl: Boolean get() = script.rtl

    /** `،` `؛` `؟` only make sense in Arabic-script languages, not in Hebrew. */
    val arabicPunctuation: Boolean get() = script == Script.ARABIC

    /**
     * How the language is named inside the Persian prompt: «فارسی» for Persian (so the
     * Persian prompt stays exactly what it was), «زبان آلمانی (German)» otherwise.
     */
    val promptName: String get() = if (isPersian) "فارسی" else "زبان $nameFa ($nameEn)"

    /**
     * The key the model is asked to put the translation under. Persian keeps the
     * historical `fa`; every other language uses the neutral `translation`. The answer
     * parser accepts both.
     */
    val jsonKey: String get() = if (isPersian) "fa" else "translation"

    /** True when [text] contains at least one letter of this language's script. */
    fun hasOwnLetters(text: String): Boolean = text.any { script.owns(it) }
}

object TargetLanguages {

    const val PERSIAN_CODE = "fa"

    /** Persian first — the app's home language — then roughly by number of viewers. */
    val all: List<TargetLanguage> = listOf(
        TargetLanguage("fa", "🇮🇷", "فارسی", "Persian (Farsi)", "فارسی", Script.ARABIC),
        TargetLanguage("en", "🇬🇧", "انگلیسی", "English", "English", Script.LATIN),
        TargetLanguage("ar", "🇸🇦", "عربی", "Arabic (Modern Standard)", "العربية", Script.ARABIC),
        TargetLanguage("tr", "🇹🇷", "ترکی استانبولی", "Turkish", "Türkçe", Script.LATIN),
        TargetLanguage("de", "🇩🇪", "آلمانی", "German", "Deutsch", Script.LATIN),
        TargetLanguage("fr", "🇫🇷", "فرانسوی", "French", "Français", Script.LATIN),
        TargetLanguage("es", "🇪🇸", "اسپانیایی", "Spanish", "Español", Script.LATIN),
        TargetLanguage("it", "🇮🇹", "ایتالیایی", "Italian", "Italiano", Script.LATIN),
        TargetLanguage("pt", "🇵🇹", "پرتغالی", "Portuguese", "Português", Script.LATIN),
        TargetLanguage("ru", "🇷🇺", "روسی", "Russian", "Русский", Script.CYRILLIC),
        TargetLanguage("uk", "🇺🇦", "اوکراینی", "Ukrainian", "Українська", Script.CYRILLIC),
        TargetLanguage("zh", "🇨🇳", "چینی (ساده‌شده)", "Chinese (Simplified)", "简体中文", Script.HAN),
        TargetLanguage("ja", "🇯🇵", "ژاپنی", "Japanese", "日本語", Script.JAPANESE),
        TargetLanguage("ko", "🇰🇷", "کره‌ای", "Korean", "한국어", Script.HANGUL),
        TargetLanguage("hi", "🇮🇳", "هندی", "Hindi", "हिन्दी", Script.DEVANAGARI),
        TargetLanguage("ur", "🇵🇰", "اردو", "Urdu", "اردو", Script.ARABIC),
        TargetLanguage("ps", "🇦🇫", "پشتو", "Pashto", "پښتو", Script.ARABIC),
        TargetLanguage("tg", "🇹🇯", "تاجیکی", "Tajik (Cyrillic)", "Тоҷикӣ", Script.CYRILLIC),
        TargetLanguage("az", "🇦🇿", "آذربایجانی", "Azerbaijani (Latin)", "Azərbaycanca", Script.LATIN),
        TargetLanguage("nl", "🇳🇱", "هلندی", "Dutch", "Nederlands", Script.LATIN),
        TargetLanguage("pl", "🇵🇱", "لهستانی", "Polish", "Polski", Script.LATIN),
        TargetLanguage("sv", "🇸🇪", "سوئدی", "Swedish", "Svenska", Script.LATIN),
        TargetLanguage("cs", "🇨🇿", "چکی", "Czech", "Čeština", Script.LATIN),
        TargetLanguage("ro", "🇷🇴", "رومانیایی", "Romanian", "Română", Script.LATIN),
        TargetLanguage("el", "🇬🇷", "یونانی", "Greek", "Ελληνικά", Script.GREEK),
        TargetLanguage("he", "🇮🇱", "عبری", "Hebrew", "עברית", Script.HEBREW),
        TargetLanguage("id", "🇮🇩", "اندونزیایی", "Indonesian", "Bahasa Indonesia", Script.LATIN),
        TargetLanguage("ms", "🇲🇾", "مالایی", "Malay", "Bahasa Melayu", Script.LATIN),
        TargetLanguage("vi", "🇻🇳", "ویتنامی", "Vietnamese", "Tiếng Việt", Script.LATIN),
        TargetLanguage("th", "🇹🇭", "تایلندی", "Thai", "ไทย", Script.THAI),
        TargetLanguage("bn", "🇧🇩", "بنگالی", "Bengali", "বাংলা", Script.BENGALI),
    )

    val persian: TargetLanguage get() = all.first()

    /** An unknown or missing code — a setting from an older build — falls back to Persian. */
    fun byCode(code: String?): TargetLanguage = all.firstOrNull { it.code == code } ?: persian

    /** Search by Persian, English or native name, or by code. Blank query returns everything. */
    fun search(query: String): List<TargetLanguage> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter {
            it.nameFa.contains(q, ignoreCase = true) ||
                it.nameEn.contains(q, ignoreCase = true) ||
                it.nativeName.contains(q, ignoreCase = true) ||
                it.code.equals(q, ignoreCase = true)
        }
    }
}
