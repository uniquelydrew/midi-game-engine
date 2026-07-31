package core.runtime

import core.chart.PlayableChart
import core.judgment.JudgmentEngine
import core.judgment.MatchResult
import core.midi.MidiEvent
import core.midi.NoteOn

class GameSessionStateful(
    private val chart: PlayableChart,
    private val judgmentEngine: JudgmentEngine
) {

    private val results = mutableListOf<MatchResult>()
    private var combo: Int = 0
    private var maxCombo: Int = 0

    init {
        judgmentEngine.load(chart.events)
    }

    fun onInput(event: MidiEvent) {
        when (event) {
            is NoteOn -> {
                val result = judgmentEngine.onNote(event.pitch, event.timestampUs)
                results.add(result)

                if (result != MatchResult.Miss) {
                    combo++
                    if (combo > maxCombo) maxCombo = combo
                } else {
                    combo = 0
                }

                println("Input: pitch=${event.pitch} time=${event.timestampUs} -> $result | combo=$combo")
            }
            else -> {}
        }
    }

    fun getResults(): List<MatchResult> = results

    fun getMaxCombo(): Int = maxCombo
}
