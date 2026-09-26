package core.drums

import core.chart.ExpectedInput
import core.runtime.DrumSequenceSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrumSequenceJudgmentEngineTest {
    private val sequence = DrumSequence(
        events = listOf(
            DrumSequenceEvent(1, 38, DrumTarget.SNARE, 1_000_000L),
            DrumSequenceEvent(2, 36, DrumTarget.KICK, 2_000_000L)
        ),
        unclassifiedSourcePitches = emptySet()
    )

    @Test
    fun `general midi mapping converts known notes and reports unknown pitches`() {
        val chart = listOf(
            ExpectedInput(1, 38, 0L),
            ExpectedInput(2, 99, 500_000L)
        )
        val converted = DrumSequence.fromChart(chart, DrumSequenceProfile.generalMidi())
        assertEquals(listOf(DrumTarget.SNARE), converted.events.map { it.target })
        assertEquals(setOf(99), converted.unclassifiedSourcePitches)
    }

    @Test
    fun `timing bands score and wrong strikes do not consume expected event`() {
        val engine = DrumSequenceJudgmentEngine()
        engine.load(sequence.events)

        assertEquals(
            DrumSequenceOutcome.WRONG_TARGET,
            engine.onStrike(DrumStrike(DrumTarget.KICK, 36, 100, 9, 1_000_000L)).outcome
        )
        val perfect = engine.onStrike(DrumStrike(DrumTarget.SNARE, 38, 100, 9, 1_040_000L))
        assertEquals(DrumSequenceOutcome.PERFECT, perfect.outcome)
        assertEquals(100, perfect.points)
        val good = engine.onStrike(DrumStrike(DrumTarget.KICK, 36, 100, 9, 2_100_000L))
        assertEquals(DrumSequenceOutcome.GOOD, good.outcome)
        assertEquals(50, good.points)
        assertEquals(150, engine.scoreSummary().scorePoints)
    }

    @Test
    fun `expired targets miss and unrelated late input is extra`() {
        val engine = DrumSequenceJudgmentEngine()
        engine.load(sequence.events)
        assertEquals(1, engine.advanceTo(1_120_001L).size)
        assertEquals(
            DrumSequenceOutcome.EXTRA_STRIKE,
            engine.onStrike(DrumStrike(DrumTarget.SNARE, 38, 100, 9, 1_300_000L)).outcome
        )
        assertEquals(1, engine.scoreSummary().missCount)
    }

    @Test
    fun `session score persists when a loop starts another pass`() {
        val session = DrumSequenceSession(sequence)
        session.onStrike(DrumStrike(DrumTarget.SNARE, 38, 100, 9, 1_000_000L))
        session.resetPass()
        session.onStrike(DrumStrike(DrumTarget.SNARE, 38, 100, 9, 1_000_000L))
        assertEquals(200, session.scoreSummary().scorePoints)
        assertTrue(session.scoreSummary().perfectCount == 2)
    }
}
