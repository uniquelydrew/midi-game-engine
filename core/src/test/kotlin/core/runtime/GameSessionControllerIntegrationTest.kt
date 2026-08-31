package core.runtime

import core.chart.ExpectedInput
import core.chart.PlayableChart
import core.judgment.Judgment
import core.judgment.JudgmentEngine
import core.judgment.TimingWindow
import core.midi.NoteOff
import core.midi.NoteOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Integration coverage for the session controller boundary used by the UI controller. */
class GameSessionControllerIntegrationTest {
    private val timing = TimingWindow(perfectUs = 50_000L, goodUs = 100_000L)

    @Test
    fun `runtime judgment state marks notes without mutating the reusable chart`() {
        val chart = PlayableChart(listOf(ExpectedInput(id = 42, pitch = 60, targetTimeUs = 1_000_000L)))
        val session = GameSessionStateful(chart, JudgmentEngine(timing))

        session.onInput(NoteOn(1_000_000L, 60, 100, 0))

        assertTrue(session.runtimeNoteStates().getValue(42).matched)
        assertEquals(Judgment.Perfect, session.runtimeNoteStates().getValue(42).judgment)
        assertEquals(ExpectedInput(id = 42, pitch = 60, targetTimeUs = 1_000_000L), chart.events.single())

        val replay = GameSessionStateful(chart, JudgmentEngine(timing))
        assertFalse(replay.runtimeNoteStates().getValue(42).matched)
    }

    @Test
    fun `held note acquisition and release are judged as one controller transaction`() {
        val session = GameSessionStateful(
            PlayableChart(listOf(ExpectedInput(id = 7, pitch = 60, targetTimeUs = 1_000_000L, durationUs = 500_000L))),
            JudgmentEngine(timing)
        )

        session.onInput(NoteOn(1_000_000L, 60, 100, 0))
        assertFalse(session.runtimeNoteStates().getValue(7).matched)
        session.onInput(NoteOff(1_580_000L, 60, 0))

        assertEquals(Judgment.Good, session.runtimeNoteStates().getValue(7).judgment)
        assertEquals(1, session.getResults().size)
    }
}
