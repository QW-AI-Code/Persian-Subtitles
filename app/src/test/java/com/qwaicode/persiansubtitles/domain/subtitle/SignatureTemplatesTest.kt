package com.qwaicode.persiansubtitles.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ready-made credit frames and, above all, getting the name back out of one. */
class SignatureTemplatesTest {

    @Test
    fun `every template is usable and carries the placeholder`() {
        assertTrue(SignatureTemplates.all.size >= 10)
        val ids = SignatureTemplates.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)

        SignatureTemplates.all.forEach { template ->
            assertTrue(template.title.isNotBlank())
            assertTrue(
                "${template.id} has no placeholder",
                template.pattern.contains(SignatureTemplates.NAME),
            )
        }
    }

    @Test
    fun `every template puts the latin name before the persian text`() {
        // The whole point: read the stored line from left to right and you already
        // have the intended picture — name on the left, Persian on the right. A player
        // that implements the BiDi algorithm and one that ignores it then agree.
        SignatureTemplates.all.forEach { template ->
            val rendered = SignatureTemplates.render(template.pattern, "QW-AI-Code")
            val firstLine = rendered.lineSequence().first()
            val name = firstLine.indexOf("QW-AI-Code")
            val persian = firstLine.indexOfFirst { it in '\u0600'..'\u06FF' }

            assertTrue("${template.id}: the name must be on the first line", name >= 0)
            if (persian >= 0) {
                assertTrue(
                    "${template.id}: the name must come before the persian text",
                    name < persian,
                )
            }
        }
    }

    @Test
    fun `no template contains a direction control character`() {
        // Those are what a player drew as empty boxes around the name.
        val controls = setOf(
            '\u200E', '\u200F', '\u202A', '\u202B', '\u202C',
            '\u2066', '\u2067', '\u2068', '\u2069',
        )
        SignatureTemplates.all.forEach { template ->
            template.pattern.forEach { ch ->
                assertFalse("${template.id} contains a control character", ch in controls)
            }
        }
    }

    @Test
    fun `rendering puts the name into the frame`() {
        val template = SignatureTemplates.byId("stars")!!

        assertEquals(
            "★ QW-AI-Code ★ زیرنویس اختصاصی",
            SignatureTemplates.render(template.pattern, "QW-AI-Code"),
        )
    }

    @Test
    fun `a blank name falls back to the project default`() {
        val rendered = SignatureTemplates.render("ترجمه: ${SignatureTemplates.NAME}", "   ")

        assertTrue(rendered.contains(SignatureInserter.DEFAULT_TEXT))
        assertFalse(rendered.contains(SignatureTemplates.NAME))
    }

    @Test
    fun `the name survives switching from one frame to another`() {
        assertEquals("QW-AI-Code", SignatureTemplates.extractName("QW-AI-Code"))
        assertEquals("QW-AI-Code", SignatureTemplates.extractName("★ QW-AI-Code ★ زیرنویس اختصاصی"))
        assertEquals("QW-AI-Code", SignatureTemplates.extractName("QW-AI-Code — ترجمه"))
        assertEquals("QW-AI-Code", SignatureTemplates.extractName("<i>QW-AI-Code — ترجمه</i>"))
        assertEquals("علی", SignatureTemplates.extractName("علی «زیرنویس اختصاصی»"))
        // A signature written the old way round is still understood.
        assertEquals("علی", SignatureTemplates.extractName("ترجمه: علی"))
    }

    @Test
    fun `a two line frame keeps the line that holds the name`() {
        val signature = SignatureTemplates.render(
            SignatureTemplates.byId("channel")!!.pattern,
            "QW-AI-Code",
        )

        assertEquals("QW-AI-Code", SignatureTemplates.extractName(signature))
    }

    @Test
    fun `an unusable signature falls back instead of returning nothing`() {
        assertEquals(SignatureInserter.DEFAULT_TEXT, SignatureTemplates.extractName(""))
        assertEquals(SignatureInserter.DEFAULT_TEXT, SignatureTemplates.extractName("★ • ─── ★"))
        assertEquals(SignatureInserter.DEFAULT_TEXT, SignatureTemplates.extractName("ترجمه:"))
    }

    @Test
    fun `rendering twice in a row is stable`() {
        val first = SignatureTemplates.render(SignatureTemplates.byId("stars")!!.pattern, "علی")
        val name = SignatureTemplates.extractName(first)
        val second = SignatureTemplates.render(SignatureTemplates.byId("stars")!!.pattern, name)

        assertEquals(first, second)
    }
}
