package core.runtime

import core.chart.ExpectedInput
import core.chart.PlayableChart
import core.judgment.Judgment
import core.judgment.JudgmentEngine
import core.judgment.TimingWindow
import core.midi.NoteOn
import kotlin.test.Test
import kotlin.test.assertEquals

class GameSessionStatefulTest {

    private val timing = TimingWindow(
        perfectUs = 50_000L,
        goodUs = 100_000L
    )

    @Test
    fun `score accumulation and reset behave like a game session`() {
        val engine = JudgmentEngine(timing)
        val chart = PlayableChart(
            listOf(
                ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L),
                ExpectedInput(pitch = 62, targetTimeUs = 2_000_000L),
                ExpectedInput(pitch = 64, targetTimeUs = 3_000_000L)
            )
        )

        val session = GameSessionStateful(chart, engine)
        session.onInput(NoteOn(timestampUs = 1_000_000L, pitch = 60, velocity = 100, channel = 0))
        session.onInput(NoteOn(timestampUs = 2_080_000L, pitch = 62, velocity = 100, channel = 0))
        session.advanceTo(3_200_001L)

        val summary = session.scoreSummary()
        assertEquals(50, summary.scorePercent)
        assertEquals(1, summary.missCount)
        assertEquals(1, summary.noInputCount)
        assertEquals(0, session.getCombo())
        assertEquals(2, session.getMaxCombo())
        assertEquals(3, session.getResults().size)
        assertEquals(Judgment.Miss, session.getResults().last().judgment)

        val resetChart = PlayableChart(
            listOf(ExpectedInput(pitch = 72, targetTimeUs = 4_000_000L))
        )
        val resetSession = GameSessionStateful(resetChart, engine)

        assertEquals(0, resetSession.getCombo())
        assertEquals(0, resetSession.getMaxCombo())
        assertEquals(0, resetSession.getResults().size)
        assertEquals(0, resetSession.scoreSummary().judgedNotes)
        assertEquals(0, resetSession.scoreSummary().scorePercent)
    }
}
