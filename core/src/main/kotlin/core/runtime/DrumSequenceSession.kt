package core.runtime

import core.drums.DrumSequence
import core.drums.DrumSequenceFeedback
import core.drums.DrumSequenceJudgmentEngine
import core.drums.DrumSequenceScoreSummary
import core.drums.DrumSequenceOutcome
import core.drums.DrumStrike
import kotlin.math.abs

class DrumSequenceSession(
    private val sequence: DrumSequence,
    private val judgment: DrumSequenceJudgmentEngine = DrumSequenceJudgmentEngine()
) {
    private val sessionFeedback = mutableListOf<DrumSequenceFeedback>()

    init { judgment.load(sequence.events) }

    fun onStrike(strike: DrumStrike): DrumSequenceFeedback =
        judgment.onStrike(strike).also(sessionFeedback::add)

    fun advanceTo(timeUs: Long): List<DrumSequenceFeedback> =
        judgment.advanceTo(timeUs).also(sessionFeedback::addAll)

    fun scoreSummary(): DrumSequenceScoreSummary {
        val timing = sessionFeedback.mapNotNull { it.timingDeltaUs }.map(::abs)
        return DrumSequenceScoreSummary(
            scorePoints = sessionFeedback.sumOf { it.points },
            perfectCount = sessionFeedback.count { it.outcome == DrumSequenceOutcome.PERFECT },
            goodCount = sessionFeedback.count { it.outcome == DrumSequenceOutcome.GOOD },
            missCount = sessionFeedback.count { it.outcome == DrumSequenceOutcome.MISS },
            wrongStrikeCount = sessionFeedback.count { it.outcome == DrumSequenceOutcome.WRONG_TARGET },
            extraStrikeCount = sessionFeedback.count { it.outcome == DrumSequenceOutcome.EXTRA_STRIKE },
            averageAbsoluteTimingUs = timing.takeIf { it.isNotEmpty() }?.average()?.toLong()
        )
    }

    fun resetPass() = judgment.load(sequence.events)
}
