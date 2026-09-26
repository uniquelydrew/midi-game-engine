package core.drums

import kotlin.math.abs

enum class DrumSequenceOutcome { PERFECT, GOOD, MISS, WRONG_TARGET, EXTRA_STRIKE }

data class DrumSequenceFeedback(
    val outcome: DrumSequenceOutcome,
    val event: DrumSequenceEvent? = null,
    val strike: DrumStrike? = null,
    val timingDeltaUs: Long? = null,
    val points: Int = 0
)

data class DrumSequenceScoreSummary(
    val scorePoints: Int,
    val perfectCount: Int,
    val goodCount: Int,
    val missCount: Int,
    val wrongStrikeCount: Int,
    val extraStrikeCount: Int,
    val averageAbsoluteTimingUs: Long?
)

/** Judgement for a timed drum chart. It deliberately does not share Whack's prompt lifecycle. */
class DrumSequenceJudgmentEngine(
    private val perfectUs: Long = 50_000L,
    private val goodUs: Long = 120_000L
) {
    private data class EventState(val event: DrumSequenceEvent, var resolved: Boolean = false)

    private val states = mutableListOf<EventState>()
    private val results = mutableListOf<DrumSequenceFeedback>()
    private var scorePoints = 0

    init {
        require(perfectUs in 0L..goodUs) { "Perfect window must be within good window" }
    }

    fun load(events: List<DrumSequenceEvent>) {
        states.clear()
        states += events.map { EventState(it) }
        results.clear()
        scorePoints = 0
    }

    fun advanceTo(timeUs: Long): List<DrumSequenceFeedback> {
        val misses = states.filter { !it.resolved && timeUs > it.event.targetTimeUs + goodUs }
            .map { state ->
                state.resolved = true
                DrumSequenceFeedback(DrumSequenceOutcome.MISS, event = state.event)
                    .also(results::add)
            }
        return misses
    }

    fun onStrike(strike: DrumStrike): DrumSequenceFeedback {
        advanceTo(strike.timestampUs)
        val targetMatch = states.filter { state ->
            !state.resolved && state.event.target == strike.target &&
                abs(strike.timestampUs - state.event.targetTimeUs) <= goodUs
        }.minByOrNull { abs(strike.timestampUs - it.event.targetTimeUs) }

        if (targetMatch != null) {
            targetMatch.resolved = true
            val delta = strike.timestampUs - targetMatch.event.targetTimeUs
            val outcome = if (abs(delta) <= perfectUs) DrumSequenceOutcome.PERFECT else DrumSequenceOutcome.GOOD
            val points = if (outcome == DrumSequenceOutcome.PERFECT) 100 else 50
            scorePoints += points
            return DrumSequenceFeedback(outcome, targetMatch.event, strike, delta, points)
                .also(results::add)
        }

        val nearbyExpected = states.any { state ->
            !state.resolved && abs(strike.timestampUs - state.event.targetTimeUs) <= goodUs
        }
        val outcome = if (nearbyExpected) DrumSequenceOutcome.WRONG_TARGET else DrumSequenceOutcome.EXTRA_STRIKE
        return DrumSequenceFeedback(outcome, strike = strike).also(results::add)
    }

    fun results(): List<DrumSequenceFeedback> = results.toList()

    fun scoreSummary(): DrumSequenceScoreSummary {
        val timing = results.mapNotNull { it.timingDeltaUs }.map(::abs)
        return DrumSequenceScoreSummary(
            scorePoints = scorePoints,
            perfectCount = results.count { it.outcome == DrumSequenceOutcome.PERFECT },
            goodCount = results.count { it.outcome == DrumSequenceOutcome.GOOD },
            missCount = results.count { it.outcome == DrumSequenceOutcome.MISS },
            wrongStrikeCount = results.count { it.outcome == DrumSequenceOutcome.WRONG_TARGET },
            extraStrikeCount = results.count { it.outcome == DrumSequenceOutcome.EXTRA_STRIKE },
            averageAbsoluteTimingUs = timing.takeIf { it.isNotEmpty() }?.average()?.toLong()
        )
    }
}
