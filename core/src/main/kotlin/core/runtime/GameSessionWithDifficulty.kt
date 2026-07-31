package core.runtime

import core.chart.PlayableChart
import core.difficulty.DifficultyProfile
import core.judgment.JudgmentEngine
import core.judgment.Judgment
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

    fun onInput(event: MidiEvent): Judgment? {
        when (event) {
            is NoteOn -> {
                val result = judgmentEngine.onNote(event.pitch, event.timestampUs)
                handleResult(result, event)
                return result
            }
            else -> return null
        }
    }

    private fun handleResult(result: Judgment, event: NoteOn) {
        when (result) {
            Judgment.Perfect,
            Judgment.Great,
            Judgment.Good -> {
                combo++
                if (combo > maxCombo) maxCombo = combo
            }
            Judgment.Miss -> {
                combo = 0
            }
        }

        println(
            "Input pitch=${event.pitch} time=${event.timestampUs} -> $result | combo=$combo max=$maxCombo"
        )
    }
}
