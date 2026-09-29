package com.qwaicode.persiansubtitles.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFieldSyncTest {

    @Test
    fun `a delayed own echo is ignored and newer typing stays pending`() {
        var pending = emptyList<String>()
        pending = TextFieldSync.remember(pending, "q")
        pending = TextFieldSync.remember(pending, "qw")

        val decision = TextFieldSync.onExternalValue("q", current = "qw", pending = pending)

        assertFalse(decision.adopt)
        assertEquals(listOf("qw"), decision.pending)
    }

    @Test
    fun `echo of the current value confirms all older echoes`() {
        val decision = TextFieldSync.onExternalValue(
            external = "qw",
            current = "qw",
            pending = listOf("q", "qw"),
        )

        assertFalse(decision.adopt)
        assertEquals(emptyList<String>(), decision.pending)
    }

    @Test
    fun `a value never emitted by the field is adopted`() {
        val decision = TextFieldSync.onExternalValue(
            external = "restored",
            current = "typed",
            pending = listOf("typed"),
        )

        assertTrue(decision.adopt)
        assertEquals(emptyList<String>(), decision.pending)
    }

    @Test
    fun `pending echoes are bounded`() {
        val pending = (0..TextFieldSync.MAX_PENDING + 10).fold(emptyList<String>()) { list, i ->
            TextFieldSync.remember(list, i.toString())
        }

        assertEquals(TextFieldSync.MAX_PENDING, pending.size)
        assertEquals("11", pending.first())
    }
}
