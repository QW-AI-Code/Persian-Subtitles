package com.qwaicode.persiansubtitles

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app is Persian-only, by design.
 *
 * Android normally picks the resource folder that matches the PHONE's language, so
 * on a device set to English the app came up in English. There is no English UI
 * any more, and on top of that every entry point forces the Persian locale onto
 * its own context, so the language and the right-to-left layout no longer depend
 * on the phone's settings at all.
 */
object AppLocale {

    val PERSIAN: Locale = Locale("fa", "IR")

    /** Wraps a context so that all resources, dates and layout direction are Persian. */
    fun wrap(base: Context): Context {
        Locale.setDefault(PERSIAN)
        val config = Configuration(base.resources.configuration)
        config.setLocale(PERSIAN)
        config.setLayoutDirection(PERSIAN)
        return base.createConfigurationContext(config)
    }
}

/**
 * Persian-Indic digits. Subtitle timestamps inside exported files stay ASCII
 * (players require it) — this is only for what the user reads on screen.
 */
private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

fun String.toPersianDigits(): String {
    if (none { it in '0'..'9' }) return this
    val out = StringBuilder(length)
    for (ch in this) {
        out.append(if (ch in '0'..'9') PERSIAN_DIGITS[ch - '0'] else ch)
    }
    return out.toString()
}

fun Int.fa(): String = toString().toPersianDigits()

fun Long.fa(): String = toString().toPersianDigits()

/** "۱٫۲۳۴" — grouped with the Persian thousands separator. */
fun Int.faGrouped(): String {
    val digits = toString()
    val grouped = digits.reversed().chunked(3).joinToString("\u066C").reversed()
    return grouped.toPersianDigits()
}
