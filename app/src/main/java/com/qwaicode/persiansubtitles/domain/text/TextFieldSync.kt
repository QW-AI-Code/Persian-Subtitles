package com.qwaicode.persiansubtitles.domain.text

/**
 * Keeps a text field from scrambling what the user types.
 *
 * The bug this exists for, reported from a real phone: type `q`, then `w`, and the
 * field shows `wq`. Every field in this app is bound to a value that is stored
 * asynchronously (DataStore for the API key, the signature, the patterns). The
 * round trip takes a few milliseconds, so while the user is typing the value
 * arriving from outside is *older* than what is on screen:
 *
 * ```
 * user types "q"      field shows "q"   →  store("q")
 * user types "w"      field shows "qw"  →  store("qw")
 * store emits "q"                          ← stale, one keystroke behind
 * ```
 *
 * If the field adopts that stale value it loses the last character and moves the
 * caret to the front, and the next keystroke lands in front of the text instead of
 * after it. The rule below tells the field which incoming values to ignore: every
 * value it has emitted itself is remembered, and an incoming value that is one of
 * those echoes is dropped, no matter how late it arrives. Only a value that the
 * field never produced — "restore defaults", a project being loaded, a translation
 * arriving from Gemini — is a genuine outside change and replaces the content.
 *
 * Pure Kotlin on purpose: this is the part that broke, so it is unit tested.
 */
object TextFieldSync {

    /** More than enough for the fastest typist; keeps the echo list bounded. */
    const val MAX_PENDING = 32

    /**
     * @param external the value handed to the field from outside
     * @param current what the field currently shows
     * @param pending everything the field has emitted and not seen echoed back yet
     */
    data class Decision(
        /** True when [external] is a real outside change and must be shown. */
        val adopt: Boolean,
        /** The echo list to keep for the next round. */
        val pending: List<String>,
    )

    fun onExternalValue(external: String, current: String, pending: List<String>): Decision {
        // Already on screen: nothing to do, and everything emitted up to here is
        // confirmed.
        if (external == current) {
            val index = pending.lastIndexOf(external)
            return Decision(adopt = false, pending = if (index >= 0) pending.drop(index + 1) else pending)
        }

        // One of our own, older values coming back through the store. Drop it and
        // forget the echoes it confirms; the newer ones are still on their way.
        val echo = pending.indexOf(external)
        if (echo >= 0) return Decision(adopt = false, pending = pending.drop(echo + 1))

        // A value the field never produced: a real change from elsewhere.
        return Decision(adopt = true, pending = emptyList())
    }

    /** Records a value the field just emitted. */
    fun remember(pending: List<String>, emitted: String): List<String> =
        (pending + emitted).takeLast(MAX_PENDING)
}
