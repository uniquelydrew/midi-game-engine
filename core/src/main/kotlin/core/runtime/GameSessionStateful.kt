package core.runtime

import core.chart.PlayableChart
import core.judgment.InputFeedback
import core.judgment.Judgment
import core.judgment.JudgmentEngine
import core.judgment.JudgmentResult
import core.judgment.JudgmentEngine.RuntimeNoteState
import core.judgment.ScoreSummary
import core.midi.MidiEvent

class GameSessionStateful(
    private val chart: PlayableChart,
    private val judgmentEngine: JudgmentEngine
) {

    private var combo: Int = 0
    private var maxCombo: Int = 0
    private var lastFeedback: InputFeedback? = null

    init {
        judgmentEngine.load(chart.events)
    }

    fun onInput(event: MidiEvent): InputFeedback? {
        advanceTo(event.timestampUs)
        val beforeCount = judgmentEngine.results().size
        val feedback = judgmentEngine.onInput(event)
        lastFeedback = feedback
        val finalized = judgmentEngine.results().drop(beforeCount)
        if (finalized.isNotEmpty()) {
            finalized.forEach { result ->
                when (result.judgment) {
                    Judgment.Perfect,
                    Judgment.Good -> {
                        combo++
                        if (combo > maxCombo) maxCombo = combo
                    }
                    Judgment.Miss -> combo = 0
                }
            }
        }
        return feedback
    }

    fun advanceTo(timeUs: Long): List<JudgmentResult> {
        val finalized = judgmentEngine.advanceTo(timeUs)
        if (finalized.isNotEmpty()) {
            finalized.forEach { result ->
                when (result.judgment) {
                    Judgment.Perfect,
                    Judgment.Good -> {
                        combo++
                        if (combo > maxCombo) maxCombo = combo
                    }
                    Judgment.Miss -> combo = 0
                }
            }
        }
        return finalized
    }

    fun getResults(): List<JudgmentResult> = judgmentEngine.results()

    fun runtimeNoteStates(): Map<Int, RuntimeNoteState> = judgmentEngine.runtimeNoteStates()

    fun getCombo(): Int = combo

    fun getMaxCombo(): Int = maxCombo

    fun scoreSummary(): ScoreSummary = judgmentEngine.scoreSummary()

    fun lastFeedback(): InputFeedback? = lastFeedback
}
