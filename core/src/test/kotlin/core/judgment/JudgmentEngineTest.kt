package core.judgment

import core.chart.ExpectedInput
import core.midi.NoteOff
import core.midi.NoteOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JudgmentEngineTest {

    private val timing = TimingWindow(
        perfectUs = 50_000L,
        goodUs = 100_000L
    )

    @Test
    fun `note-on timing boundaries judge perfect good and late start miss`() {
        val engine = JudgmentEngine(timing)

        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L)))
        assertEquals(
            Judgment.Perfect,
            engine.onInput(NoteOn(timestampUs = 1_000_000L, pitch = 60, velocity = 100, channel = 0))?.judgment
        )

        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L)))
        assertEquals(
            Judgment.Good,
            engine.onInput(NoteOn(timestampUs = 1_080_000L, pitch = 60, velocity = 100, channel = 0))?.judgment
        )

        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L)))
        val miss = engine.onInput(NoteOn(timestampUs = 1_200_000L, pitch = 60, velocity = 100, channel = 0))
        assertEquals(Judgment.Miss, miss?.judgment)
        assertEquals(MissReason.TimingRelease, miss?.missReason)
        assertEquals(TimingMissDetail.LateStartOnly, miss?.missDetail)
    }

    @Test
    fun `hold notes judge perfect release good release and salvageable start release combinations`() {
        val engine = JudgmentEngine(timing)

        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L, durationUs = 500_000L)))
        engine.onInput(NoteOn(timestampUs = 1_000_000L, pitch = 60, velocity = 100, channel = 0))
        val perfect = engine.onInput(NoteOff(timestampUs = 1_500_000L, pitch = 60, channel = 0))
        assertEquals(Judgment.Perfect, perfect?.judgment)

        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L, durationUs = 500_000L)))
        engine.onInput(NoteOn(timestampUs = 1_000_000L, pitch = 60, velocity = 100, channel = 0))
        val goodRelease = engine.onInput(NoteOff(timestampUs = 1_620_000L, pitch = 60, channel = 0))
        assertEquals(Judgment.Good, goodRelease?.judgment)

        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L, durationUs = 500_000L)))
        engine.onInput(NoteOn(timestampUs = 1_080_000L, pitch = 60, velocity = 100, channel = 0))
        val goodStart = engine.onInput(NoteOff(timestampUs = 1_500_000L, pitch = 60, channel = 0))
        assertEquals(Judgment.Good, goodStart?.judgment)
    }

    @Test
    fun `held note without release times out as a timing release miss`() {
        val engine = JudgmentEngine(timing)

        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L, durationUs = 500_000L)))
        engine.onInput(NoteOn(timestampUs = 1_000_000L, pitch = 60, velocity = 100, channel = 0))

        val finalized = engine.advanceTo(1_600_001L)

        assertEquals(1, finalized.size)
        assertEquals(Judgment.Miss, finalized.single().judgment)
        assertEquals(MissReason.TimingRelease, finalized.single().missReason)
        assertEquals(TimingMissDetail.LateReleaseOnly, finalized.single().missDetail)
    }

    @Test
    fun `no input becomes a no input miss`() {
        val engine = JudgmentEngine(timing)
        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L, durationUs = 500_000L)))

        val finalized = engine.advanceTo(1_600_001L)

        assertEquals(1, finalized.size)
        assertEquals(Judgment.Miss, finalized.single().judgment)
        assertEquals(MissReason.NoInput, finalized.single().missReason)
    }

    @Test
    fun `wrong key on note-on is classified distinctly`() {
        val engine = JudgmentEngine(timing)
        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L)))

        val feedback = engine.onInput(NoteOn(timestampUs = 1_000_000L, pitch = 61, velocity = 100, channel = 0))

        assertEquals(Judgment.Miss, feedback?.judgment)
        assertEquals(MissReason.WrongKey, feedback?.missReason)
        assertEquals(1, engine.results().size)
        assertEquals(MissReason.WrongKey, engine.results().single().missReason)
    }

    @Test
    fun `early start and late release are classified as timing release miss`() {
        val engine = JudgmentEngine(timing)
        engine.load(listOf(ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L, durationUs = 500_000L)))

        engine.onInput(NoteOn(timestampUs = 850_000L, pitch = 60, velocity = 100, channel = 0))
        val feedback = engine.onInput(NoteOff(timestampUs = 1_700_000L, pitch = 60, channel = 0))

        assertEquals(Judgment.Miss, feedback?.judgment)
        assertEquals(MissReason.TimingRelease, feedback?.missReason)
        assertEquals(TimingMissDetail.EarlyStartLateRelease, feedback?.missDetail)
        assertEquals(TimingMissDetail.EarlyStartLateRelease, engine.results().single().missDetail)
    }

    @Test
    fun `same pitch repeats and chords resolve to the correct expected note`() {
        val engine = JudgmentEngine(timing)
        engine.load(
            listOf(
                ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L),
                ExpectedInput(pitch = 60, targetTimeUs = 2_000_000L),
                ExpectedInput(pitch = 64, targetTimeUs = 1_000_000L)
            )
        )

        engine.onInput(NoteOn(timestampUs = 1_000_000L, pitch = 60, velocity = 100, channel = 0))
        engine.onInput(NoteOn(timestampUs = 1_000_000L, pitch = 64, velocity = 100, channel = 0))
        engine.onInput(NoteOn(timestampUs = 2_000_000L, pitch = 60, velocity = 100, channel = 0))

        val results = engine.results()
        assertEquals(listOf(0, 2, 1), results.map { it.noteId })
        assertEquals(listOf(60, 64, 60), results.map { it.pitch })
        assertEquals(listOf(Judgment.Perfect, Judgment.Perfect, Judgment.Perfect), results.map { it.judgment })
        assertNull(engine.advanceTo(3_000_000L).firstOrNull())
    }
}
