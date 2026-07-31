package core.runtime

import core.chart.PlayableChart
import core.difficulty.DifficultyProfile
import core.judgment.JudgmentEngine
import core.judgment.MatchResult
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

    fun onInput(event: MidiEvent) {
        when (event) {
            is NoteOn -> {
                val result = judgmentEngine.onNote(event.pitch, event.timestampUs)
                handleResult(result, event)
            }
            else -> {}
        }
    }

    private fun handleResult(result: MatchResult, event: NoteOn) {
        when (result) {
            MatchResult.Perfect,
            MatchResult.Great,
            MatchResult.Good -> {
                combo++
                if (combo > maxCombo) maxCombo = combo
            }
            MatchResult.Miss -> {
                combo = 0
            }
        }

        println(
            "Input pitch=${event.pitch} time=${event.timestampUs} -> $result | combo=$combo max=$maxCombo"
        )
    }
}
