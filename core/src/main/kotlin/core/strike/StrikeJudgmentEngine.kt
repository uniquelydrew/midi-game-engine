package core.strike

import core.drums.DrumStrike

class StrikeJudgmentEngine {
    private var activeTarget: StrikeTarget? = null
    private val resolutions = mutableListOf<StrikeResolution>()
    private var wrongStrikes = 0

    fun loadTarget(target: StrikeTarget) {
        check(activeTarget == null) { "Cannot load a new strike target while another target is active" }
        activeTarget = target
    }

    fun currentTarget(): StrikeTarget? = activeTarget

    fun onStrike(strike: DrumStrike): StrikeFeedback? {
        val target = activeTarget ?: return null

        if (strike.timestampUs < target.appearsAtUs) {
            return null
        }

        if (strike.timestampUs > target.expiresAtUs) {
            advanceTo(strike.timestampUs)
            return null
        }

        if (strike.target != target.target) {
            wrongStrikes += 1
            return StrikeFeedback(
                outcome = StrikeOutcome.WRONG_TARGET,
                target = target,
                strike = strike
            )
        }

        val reactionTimeUs = strike.timestampUs - target.appearsAtUs
        val resolution = StrikeResolution(
            target = target,
            outcome = StrikeOutcome.HIT,
            strike = strike,
            reactionTimeUs = reactionTimeUs
        )
        resolutions += resolution
        activeTarget = null

        return StrikeFeedback(
            outcome = StrikeOutcome.HIT,
            target = target,
            strike = strike,
            reactionTimeUs = reactionTimeUs
        )
    }

    fun advanceTo(timeUs: Long): StrikeResolution? {
        val target = activeTarget ?: return null
        if (timeUs <= target.expiresAtUs) return null

        val resolution = StrikeResolution(
            target = target,
            outcome = StrikeOutcome.MISS
        )
        resolutions += resolution
        activeTarget = null
        return resolution
    }

    fun results(): List<StrikeResolution> = resolutions.toList()

    fun scoreSummary(): StrikeScoreSummary {
        val hits = resolutions.filter { it.outcome == StrikeOutcome.HIT }
        val reactionTimes = hits.mapNotNull { it.reactionTimeUs }

        return StrikeScoreSummary(
            resolvedTargets = resolutions.size,
            hitCount = hits.size,
            missCount = resolutions.count { it.outcome == StrikeOutcome.MISS },
            wrongStrikeCount = wrongStrikes,
            averageReactionTimeUs = reactionTimes.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            bestReactionTimeUs = reactionTimes.minOrNull()
        )
    }

    fun reset() {
        activeTarget = null
        resolutions.clear()
        wrongStrikes = 0
    }
}
