package core.runtime

import core.drums.DrumKitProfile
import core.drums.DrumTarget
import core.drums.DrumTrigger
import core.midi.NoteOn
import core.strike.StrikeOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WhackGameSessionTest {
    private class SequenceSelector(
        private val sequence: List<DrumTarget>
    ) : WhackTargetSelector {
        private var index = 0

        override fun nextTarget(previous: DrumTarget?): DrumTarget {
            val target = sequence[index % sequence.size]
            index += 1
            return target
        }
    }

    private val profile = DrumKitProfile(
        name = "Test kit",
        triggers = listOf(
            DrumTrigger(DrumTarget.SNARE, midiNote = 38),
            DrumTrigger(DrumTarget.KICK, midiNote = 36)
        )
    )

    @Test
    fun `hit advances to the next target after configured delay`() {
        val session = WhackGameSession(
            profile = profile,
            selector = SequenceSelector(listOf(DrumTarget.SNARE, DrumTarget.KICK)),
            config = WhackGameConfig(
                targetDurationUs = 1_000_000L,
                interTargetDelayUs = 100_000L
            )
        )
        session.start(0L)

        val wrong = session.onInput(NoteOn(100_000L, pitch = 36, velocity = 100, channel = 9))
        assertEquals(StrikeOutcome.WRONG_TARGET, wrong?.outcome)
        assertEquals(DrumTarget.SNARE, session.currentTarget()?.target)

        val hit = session.onInput(NoteOn(200_000L, pitch = 38, velocity = 115, channel = 9))
        assertEquals(StrikeOutcome.HIT, hit?.outcome)

        val next = assertNotNull(session.currentTarget())
        assertEquals(DrumTarget.KICK, next.target)
        assertEquals(300_000L, next.appearsAtUs)

        assertNull(session.onInput(NoteOn(250_000L, pitch = 36, velocity = 100, channel = 9)))

        val secondHit = session.onInput(NoteOn(350_000L, pitch = 36, velocity = 100, channel = 9))
        assertEquals(StrikeOutcome.HIT, secondHit?.outcome)

        val summary = session.scoreSummary()
        assertEquals(2, summary.hitCount)
        assertEquals(1, summary.wrongStrikeCount)
    }

    @Test
    fun `advancing past expiration records miss and schedules another target`() {
        val session = WhackGameSession(
            profile = profile,
            selector = SequenceSelector(listOf(DrumTarget.SNARE, DrumTarget.KICK)),
            config = WhackGameConfig(
                targetDurationUs = 1_000_000L,
                interTargetDelayUs = 100_000L
            )
        )
        session.start(0L)

        val resolutions = session.advanceTo(1_000_001L)

        assertEquals(1, resolutions.size)
        assertEquals(StrikeOutcome.MISS, resolutions.single().outcome)
        assertEquals(1, session.scoreSummary().missCount)
        assertEquals(DrumTarget.KICK, session.currentTarget()?.target)
        assertEquals(1_100_000L, session.currentTarget()?.appearsAtUs)
    }

    @Test
    fun `default target selection only uses drums mapped by the active profile`() {
        val snareOnlyProfile = DrumKitProfile(
            name = "Snare only",
            triggers = listOf(
                DrumTrigger(DrumTarget.SNARE, midiNote = 38)
            )
        )
        val session = WhackGameSession(
            profile = snareOnlyProfile,
            config = WhackGameConfig(
                targetDurationUs = 1_000_000L,
                interTargetDelayUs = 100_000L
            )
        )
        session.start(0L)

        assertEquals(DrumTarget.SNARE, session.currentTarget()?.target)

        val hit = session.onInput(NoteOn(100_000L, pitch = 38, velocity = 100, channel = 9))
        assertEquals(StrikeOutcome.HIT, hit?.outcome)
        assertEquals(DrumTarget.SNARE, session.currentTarget()?.target)
    }
}
