package core.runtime

import core.chart.PlayableChart
import core.judgment.JudgmentEngine
import core.judgment.InputFeedback
import core.judgment.JudgmentResult
import core.judgment.ScoreSummary

class GameSession(
    private val judgmentEngine: JudgmentEngine
) {

    fun load(chart: PlayableChart) {
        judgmentEngine.load(chart.events)
    }

    fun onInput(event: core.midi.MidiEvent): InputFeedback? {
        return judgmentEngine.onInput(event)
    }

    fun advanceTo(timeUs: Long): List<JudgmentResult> = judgmentEngine.advanceTo(timeUs)

    fun results() = judgmentEngine.results()

    fun runtimeNoteStates() = judgmentEngine.runtimeNoteStates()

    fun scoreSummary(): ScoreSummary = judgmentEngine.scoreSummary()
}
