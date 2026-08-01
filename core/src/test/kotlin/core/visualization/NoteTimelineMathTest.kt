package core.visualization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteTimelineMathTest {

    @Test
    fun `upcoming notes fall toward the keyboard at a stable rate`() {
        val (_, rectEarly) = NoteTimelineMath.projectNote(
            pitch = 60,
            startTimeUs = 2_000_000L,
            durationUs = 1_000_000L,
            currentTimeUs = 0L,
            keyboardTopPx = 600f,
            pixelsPerSecond = 100f,
            totalWidthPx = 880f
        )

        val (_, rectLater) = NoteTimelineMath.projectNote(
            pitch = 60,
            startTimeUs = 2_000_000L,
            durationUs = 1_000_000L,
            currentTimeUs = 1_000_000L,
            keyboardTopPx = 600f,
            pixelsPerSecond = 100f,
            totalWidthPx = 880f
        )

        assertTrue(rectLater.bottom > rectEarly.bottom)
        assertEquals(100f, rectEarly.bottom - rectEarly.top, 0.01f)
        assertEquals(100f, rectLater.bottom - rectLater.top, 0.01f)
    }

    @Test
    fun `held notes retain their duration as they advance`() {
        val (_, rectAtHit) = NoteTimelineMath.projectNote(
            pitch = 64,
            startTimeUs = 1_500_000L,
            durationUs = 750_000L,
            currentTimeUs = 1_500_000L,
            keyboardTopPx = 500f,
            pixelsPerSecond = 120f,
            totalWidthPx = 880f
        )

        val (_, rectWhileHeld) = NoteTimelineMath.projectNote(
            pitch = 64,
            startTimeUs = 1_500_000L,
            durationUs = 750_000L,
            currentTimeUs = 1_900_000L,
            keyboardTopPx = 500f,
            pixelsPerSecond = 120f,
            totalWidthPx = 880f
        )

        assertTrue(rectWhileHeld.bottom > rectAtHit.bottom)
        assertEquals(90f, rectAtHit.bottom - rectAtHit.top, 0.01f)
        assertEquals(90f, rectWhileHeld.bottom - rectWhileHeld.top, 0.01f)
    }
}
