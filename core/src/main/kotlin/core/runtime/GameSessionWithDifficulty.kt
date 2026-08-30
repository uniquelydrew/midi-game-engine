package core.runtime

import core.chart.PlayableChart
import core.difficulty.DifficultyProfile
import core.judgment.InputFeedback
import core.judgment.Judgment
import core.judgment.JudgmentEngine
import core.judgment.JudgmentResult
import core.judgment.ScoreSummary
import core.midi.MidiEvent
import core.midi.NoteOn

class GameSessionWithDifficulty(
    private val chart: PlayableChart,
    difficulty: DifficultyProfile
) {

    private val judgmentEngine = JudgmentEngine(difficulty.timing)
    private var combo: Int = 0
    private var maxCombo: Int = 0

    init {
        judgmentEngine.load(chart.events)
    }

    fun onInput(event: MidiEvent): InputFeedback? {
        return when (event) {
            is NoteOn -> {
                val beforeCount = judgmentEngine.results().size
                val result = judgmentEngine.onInput(event)
                val finalized = judgmentEngine.results().drop(beforeCount)
                finalized.forEach { handleFinalResult(it) }
                println(
                    "Input pitch=${event.pitch} time=${event.timestampUs} -> ${result?.message ?: "ignored"} | combo=$combo max=$maxCombo"
                )
                result
            }
            else -> null
        }
    }

    fun advanceTo(timeUs: Long): List<JudgmentResult> {
        val finalized = judgmentEngine.advanceTo(timeUs)
        finalized.forEach { handleFinalResult(it) }
        return finalized
    }

    fun results() = judgmentEngine.results()

    fun scoreSummary(): ScoreSummary = judgmentEngine.scoreSummary()

    private fun handleFinalResult(result: JudgmentResult) {
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
