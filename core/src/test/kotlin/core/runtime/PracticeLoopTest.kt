package core.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeLoopTest {
    @Test
    fun `range is end exclusive and clamps selection`() {
        val loop = PracticeLoop(1_000L, 2_000L)
        assertTrue(loop.contains(1_000L))
        assertFalse(loop.contains(2_000L))
        assertEquals(1_000L, loop.clamp(1L))
        assertEquals(2_000L, loop.clamp(3_000L))
    }

    @Test
    fun `pass count stops at the requested boundary`() {
        val session = PracticeLoopSession(LoopStopRule.PassCount(2))
        session.start(0L)
        assertFalse(session.completePass(1_000_000L))
        assertTrue(session.completePass(2_000_000L))
        assertEquals(2, session.snapshot(2_000_000L).completedPasses)
    }

    @Test
    fun `duration excludes paused time and completes on a boundary`() {
        val session = PracticeLoopSession(LoopStopRule.Duration(1_000L))
        session.start(0L)
        session.pause(600_000_000L)
        session.start(2_000_000_000L)
        assertFalse(session.completePass(2_300_000_000L))
        assertTrue(session.completePass(2_500_000_000L))
        assertEquals(1_100L, session.snapshot(2_500_000_000L).activeElapsedMs)
    }
}
