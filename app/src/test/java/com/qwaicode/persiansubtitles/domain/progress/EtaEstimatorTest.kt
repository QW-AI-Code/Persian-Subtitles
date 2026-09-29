package com.qwaicode.persiansubtitles.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaEstimatorTest {

    @Test
    fun `no estimate before there is enough data`() {
        val eta = EtaEstimator()
        assertNull(eta.update(nowMs = 0, done = 0, total = 1000))
        assertNull(eta.update(nowMs = 3_000, done = 20, total = 1000))
    }

    @Test
    fun `steady progress gives speed and time left`() {
        val eta = EtaEstimator()
        eta.update(0, 0, 1000)
        eta.update(30_000, 50, 1000)
        val estimate = eta.update(60_000, 100, 1000)

        assertNotNull(estimate)
        assertEquals(100, estimate!!.linesPerMinute)
        // 900 lines left at 100 per minute.
        assertEquals(9 * 60_000L, estimate.remainingMs)
    }

    @Test
    fun `a slow start is forgotten once it leaves the window`() {
        val eta = EtaEstimator(windowMs = 60_000)
        eta.update(0, 0, 2000)
        eta.update(60_000, 10, 2000) // ramp-up: 10 lines in the first minute
        eta.update(90_000, 110, 2000)
        eta.update(120_000, 210, 2000)
        val estimate = eta.update(150_000, 310, 2000)!!
        assertTrue("speed ${estimate.linesPerMinute}", estimate.linesPerMinute >= 190)
    }

    @Test
    fun `progress going backwards starts over`() {
        val eta = EtaEstimator()
        eta.update(0, 0, 100)
        eta.update(30_000, 50, 100)
        assertNull(eta.update(31_000, 0, 100))
    }

    @Test
    fun `a finished run has no estimate`() {
        val eta = EtaEstimator()
        eta.update(0, 0, 100)
        assertNull(eta.update(30_000, 100, 100))
    }
}
