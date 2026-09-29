package com.qwaicode.persiansubtitles.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Reading and writing subtitle files through the Storage Access Framework. */
object FileUtils {

    private const val MAX_BYTES = 12 * 1024 * 1024 // a subtitle is never bigger than this

    fun readText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                // Refuse instead of cutting the file off: a truncated read used to
                // end in the middle of a character and decode as garbage.
                if (total > MAX_BYTES) throw IllegalArgumentException("file too large")
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: throw IllegalStateException("cannot open $uri")

        return decode(bytes)
    }

    fun writeText(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write("\uFEFF".toByteArray(Charsets.UTF_8)) // BOM: many players need it for Persian
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: throw IllegalStateException("cannot write $uri")
    }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"
    }

    /**
     * Subtitle files come in many encodings. UTF-8 (with or without BOM) is tried
     * strictly first, then UTF-16, then Windows-1256 which is what old Persian and
     * Arabic subtitles use.
     */
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        strictDecode(bytes, Charsets.UTF_8)?.let { return it }
        strictDecode(bytes, charsetOrNull("windows-1256"))?.let { return it }
        return String(bytes, Charsets.UTF_8) // last resort, replaces bad bytes
    }

    private fun charsetOrNull(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    private fun strictDecode(bytes: ByteArray, charset: Charset?): String? {
        if (charset == null) return null
        return runCatching {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
    }
}
