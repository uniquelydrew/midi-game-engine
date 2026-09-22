package core.strike

import core.drums.DrumStrike
import core.drums.DrumTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StrikeJudgmentEngineTest {
    @Test
    fun `correct strike resolves target and records reaction time`() {
        val engine = StrikeJudgmentEngine()
        engine.loadTarget(
            StrikeTarget(
                id = 1L,
                target = DrumTarget.SNARE,
                appearsAtUs = 1_000_000L,
                expiresAtUs = 2_000_000L
            )
        )

        val feedback = engine.onStrike(
            DrumStrike(
                target = DrumTarget.SNARE,
                midiNote = 38,
                velocity = 112,
                channel = 9,
                timestampUs = 1_180_000L
            )
        )

        assertEquals(StrikeOutcome.HIT, feedback?.outcome)
        assertEquals(180_000L, feedback?.reactionTimeUs)
        assertNull(engine.currentTarget())
        assertEquals(1, engine.scoreSummary().hitCount)
        assertEquals(180_000L, engine.scoreSummary().bestReactionTimeUs)
    }

    @Test
    fun `wrong target is penalized without consuming active target`() {
        val engine = StrikeJudgmentEngine()
        val target = StrikeTarget(
            id = 1L,
            target = DrumTarget.SNARE,
            appearsAtUs = 0L,
            expiresAtUs = 1_000_000L
        )
        engine.loadTarget(target)

        val feedback = engine.onStrike(
            DrumStrike(
                target = DrumTarget.KICK,
                midiNote = 36,
                velocity = 100,
                channel = 9,
                timestampUs = 100_000L
            )
        )

        assertEquals(StrikeOutcome.WRONG_TARGET, feedback?.outcome)
        assertEquals(target, engine.currentTarget())
        assertEquals(1, engine.scoreSummary().wrongStrikeCount)
        assertEquals(0, engine.scoreSummary().resolvedTargets)
    }

    @Test
    fun `expired target resolves as miss`() {
        val engine = StrikeJudgmentEngine()
        engine.loadTarget(
            StrikeTarget(
                id = 1L,
                target = DrumTarget.RIDE,
                appearsAtUs = 0L,
                expiresAtUs = 500_000L
            )
        )

        val resolution = engine.advanceTo(500_001L)

        assertEquals(StrikeOutcome.MISS, resolution?.outcome)
        assertEquals(1, engine.scoreSummary().missCount)
        assertNull(engine.currentTarget())
    }
}
