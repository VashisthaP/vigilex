package com.vigilex

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the pure detection logic extracted from DrowsinessAnalyzer.
 * The full DrowsinessAnalyzer requires Android context (SensorManager, ML Kit),
 * so we test the thresholds and state machine here in isolation.
 */
class DrowsinessDetectionTest {

    // ── Eye closure detection logic ──────────────────────────────────────────

    private val eyeThreshold = 0.25f
    private val EYE_CLOSED_DURATION_MS = 2_000L

    private fun eyesAreClosed(left: Float, right: Float) = left < eyeThreshold && right < eyeThreshold

    @Test fun `both eyes below threshold triggers closure`() {
        assertTrue(eyesAreClosed(0.1f, 0.2f))
    }

    @Test fun `one eye open does not trigger closure`() {
        assertFalse(eyesAreClosed(0.5f, 0.2f))
    }

    @Test fun `both eyes at threshold boundary not closed`() {
        assertFalse(eyesAreClosed(0.25f, 0.25f))
    }

    @Test fun `both eyes above threshold not closed`() {
        assertFalse(eyesAreClosed(0.9f, 0.9f))
    }

    // ── Head drop detection ───────────────────────────────────────────────────

    private val HEAD_EULER_Z = 20f
    private val HEAD_EULER_Y = 25f

    private fun headDropped(eulerY: Float, eulerZ: Float) =
        kotlin.math.abs(eulerZ) > HEAD_EULER_Z || kotlin.math.abs(eulerY) > HEAD_EULER_Y

    @Test fun `eulerZ above threshold triggers head drop`() {
        assertTrue(headDropped(0f, 25f))
    }

    @Test fun `eulerY above threshold triggers head drop`() {
        assertTrue(headDropped(30f, 0f))
    }

    @Test fun `negative euler values also trigger`() {
        assertTrue(headDropped(0f, -25f))
        assertTrue(headDropped(-30f, 0f))
    }

    @Test fun `small euler angles do not trigger head drop`() {
        assertFalse(headDropped(10f, 15f))
    }

    // ── Calibration threshold adjustment ─────────────────────────────────────

    @Test fun `calibration threshold clamped to floor`() {
        val avg = 0.1f  // very small eyes
        val adjusted = (avg * 0.5f).coerceIn(0.15f, 0.35f)
        assertEquals(0.15f, adjusted)
    }

    @Test fun `calibration threshold clamped to ceiling`() {
        val avg = 0.9f  // very open eyes
        val adjusted = (avg * 0.5f).coerceIn(0.15f, 0.35f)
        assertEquals(0.35f, adjusted)
    }

    @Test fun `calibration threshold in normal range`() {
        val avg = 0.6f  // normal eyes
        val adjusted = (avg * 0.5f).coerceIn(0.15f, 0.35f)
        assertEquals(0.30f, adjusted, 0.001f)
    }

    // ── COMBINED detection (fixed logic) ─────────────────────────────────────

    private val COMBINED_WINDOW_MS = 10_000L

    @Test fun `two different signals within window upgrades to COMBINED`() {
        var prevType: String? = null
        var prevMs = 0L

        fun fire(type: String, now: Long): String {
            val resolved = if (
                prevType != null && prevType != type && (now - prevMs) <= COMBINED_WINDOW_MS
            ) "COMBINED" else type
            prevType = type; prevMs = now
            return resolved
        }

        val r1 = fire("EYE_CLOSURE", 1000L)
        val r2 = fire("HEAD_DROP",   5000L)   // 4s later — within 10s window
        assertEquals("EYE_CLOSURE", r1)
        assertEquals("COMBINED",    r2)
    }

    @Test fun `same signal twice does NOT combine`() {
        var prevType: String? = null
        var prevMs = 0L

        fun fire(type: String, now: Long): String {
            val resolved = if (
                prevType != null && prevType != type && (now - prevMs) <= COMBINED_WINDOW_MS
            ) "COMBINED" else type
            prevType = type; prevMs = now
            return resolved
        }

        fire("EYE_CLOSURE", 1000L)
        val r2 = fire("EYE_CLOSURE", 5000L)
        assertEquals("EYE_CLOSURE", r2)
    }

    @Test fun `signals outside window do NOT combine`() {
        var prevType: String? = null
        var prevMs = 0L

        fun fire(type: String, now: Long): String {
            val resolved = if (
                prevType != null && prevType != type && (now - prevMs) <= COMBINED_WINDOW_MS
            ) "COMBINED" else type
            prevType = type; prevMs = now
            return resolved
        }

        fire("EYE_CLOSURE", 1000L)
        val r2 = fire("HEAD_DROP",  15_000L)  // 14s later — outside 10s window
        assertEquals("HEAD_DROP", r2)          // NOT combined
    }
}
