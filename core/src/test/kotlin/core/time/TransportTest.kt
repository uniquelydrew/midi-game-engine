package core.time

import kotlin.test.Test
import kotlin.test.assertEquals

class TransportTest {
    private class FakeClock(var timeNs: Long = 0L) : Clock {
        override fun now(): Long = timeNs
    }

    @Test
    fun `rate changes preserve position and scale subsequent time`() {
        val clock = FakeClock()
        val transport = Transport(clock)
        transport.start()
        clock.timeNs = 1_000_000_000L
        assertEquals(1_000_000_000L, transport.positionNs())

        transport.setRate(2.0)
        clock.timeNs = 1_500_000_000L
        assertEquals(2_000_000_000L, transport.positionNs())
    }

    @Test
    fun `seek works while paused and resumes from seeked position`() {
        val clock = FakeClock()
        val transport = Transport(clock)
        transport.seekTo(3_000_000L)
        assertEquals(3_000_000_000L, transport.positionNs())

        transport.resume()
        clock.timeNs = 500_000_000L
        assertEquals(3_500_000_000L, transport.positionNs())
    }
}
