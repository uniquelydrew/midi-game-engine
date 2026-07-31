package core.judgment

import core.chart.ExpectedInput
import kotlin.math.abs

class JudgmentEngine(
    private val timing: TimingWindow
) {

    private val events = mutableListOf<ExpectedInput>()
    private val results = mutableListOf<JudgmentResult>()

    fun load(chartEvents: List<ExpectedInput>) {
        events.clear()
        events.addAll(chartEvents)
        results.clear()
    }

    fun onNote(pitch: Int, timeUs: Long): Judgment {
        val candidates = events.filter { it.pitch == pitch && !it.matched }

        val best = candidates.minByOrNull {
            abs(it.targetTimeUs - timeUs)
        } ?: return recordMiss(timeUs)

        val delta = timeUs - best.targetTimeUs
        val absDelta = abs(delta)

        val judgment = when {
            absDelta <= timing.perfectUs -> Judgment.Perfect
            absDelta <= timing.greatUs -> Judgment.Great
            absDelta <= timing.goodUs -> Judgment.Good
            else -> Judgment.Miss
        }

        if (judgment != Judgment.Miss) {
            best.matched = true
        }

        results += JudgmentResult(
            expectedTimeUs = best.targetTimeUs,
            actualTimeUs = timeUs,
            rawDeltaUs = delta,
            judgment = judgment
        )

        return judgment
    }

    private fun recordMiss(timeUs: Long): Judgment {
        results += JudgmentResult(
            expectedTimeUs = -1,
            actualTimeUs = timeUs,
            rawDeltaUs = Long.MAX_VALUE,
            judgment = Judgment.Miss
        )
        return Judgment.Miss
    }

    fun getResults(): List<JudgmentResult> = results.toList()
}
