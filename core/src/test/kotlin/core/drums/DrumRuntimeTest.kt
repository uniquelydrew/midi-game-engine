package core.drums

import core.chart.ExpectedInput
import core.chart.PlayableChart
import core.input.GeneralMidiDrumProfile
import core.input.MidiLearnSession
import core.midi.NoteOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrumRuntimeTest {
    private fun runtime(events: List<ExpectedInput>) = DrumPracticeRuntime(DrumChartProjector(GeneralMidiDrumProfile).project(PlayableChart(events)), GeneralMidiDrumProfile)

    @Test fun `projects mapped notes and reports unmapped`() {
        val projection = DrumChartProjector(GeneralMidiDrumProfile).project(PlayableChart(listOf(ExpectedInput(1, 36, 1000), ExpectedInput(2, 99, 2000))))
        assertEquals("kick", projection.events.single().targetId)
        assertEquals(1, projection.unmappedEventCount)
    }

    @Test fun `drum timing has four tiers and fifo rolls`() {
        val session = runtime(listOf(ExpectedInput(1, 38, 1_000_000), ExpectedInput(2, 38, 1_080_000)))
        session.onInput(NoteOn(1_040_000, 38, 96, 9))
        session.onInput(NoteOn(1_110_000, 38, 96, 9))
        assertEquals(listOf(DrumJudgment.Perfect, DrumJudgment.Perfect), session.results().map { it.judgment })
        assertEquals(listOf(1, 2), session.results().map { it.noteId })
    }

    @Test fun `wrong target consumes one active event`() {
        val session = runtime(listOf(ExpectedInput(1, 38, 1_000_000)))
        val feedback = session.onInput(NoteOn(1_000_000, 36, 100, 9))
        assertTrue(feedback!!.wrongTarget)
        assertEquals(DrumMissReason.WrongTarget, session.results().single().missReason)
    }

    @Test fun `game scoring applies multiplier and star rating`() {
        val rules = DrumGameRulesEngine()
        repeat(10) { rules.onJudgment(DrumJudgmentResult(it, "kick", 36, 0, 0, DrumJudgment.Perfect)) }
        assertEquals(2, rules.snapshot().multiplier)
        assertEquals(1100L, rules.snapshot().score)
        assertEquals(StarRating.Five, rules.snapshot().starRating)
    }

    @Test fun `learn mode consumes exactly one note-on`() {
        val learn = MidiLearnSession()
        learn.awaitPad(2)
        assertEquals(38, learn.consume(NoteOn(0, 38, 100, 9))!!.midiPitch)
        assertEquals(null, learn.consume(NoteOn(1, 36, 100, 9)))
    }
}
