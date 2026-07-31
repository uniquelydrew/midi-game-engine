package core.runtime

import core.chart.PlayableChart
import core.judgment.JudgmentEngine
import core.judgment.Judgment

class GameSession(
    private val judgmentEngine: JudgmentEngine
) {

    fun load(chart: PlayableChart) {
        judgmentEngine.load(chart.events)
    }

    fun onInput(pitch: Int, timeUs: Long): Judgment {
        return judgmentEngine.onNote(pitch, timeUs)
    }

    fun results() = judgmentEngine.getResults()
}
