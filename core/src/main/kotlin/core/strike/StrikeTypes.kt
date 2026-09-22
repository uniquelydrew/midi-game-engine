package core.strike

import core.drums.DrumStrike
import core.drums.DrumTarget

data class StrikeTarget(
    val id: Long,
    val target: DrumTarget,
    val appearsAtUs: Long,
    val expiresAtUs: Long
) {
    init {
        require(expiresAtUs > appearsAtUs) { "Strike target expiration must be after appearance" }
    }
}

enum class StrikeOutcome {
    HIT,
    WRONG_TARGET,
    MISS
}

data class StrikeFeedback(
    val outcome: StrikeOutcome,
    val target: StrikeTarget,
    val strike: DrumStrike? = null,
    val reactionTimeUs: Long? = null
)

data class StrikeResolution(
    val target: StrikeTarget,
    val outcome: StrikeOutcome,
    val strike: DrumStrike? = null,
    val reactionTimeUs: Long? = null
) {
    init {
        require(outcome != StrikeOutcome.WRONG_TARGET) {
            "Wrong-target input does not resolve the active target"
        }
    }
}

data class StrikeScoreSummary(
    val resolvedTargets: Int,
    val hitCount: Int,
    val missCount: Int,
    val wrongStrikeCount: Int,
    val averageReactionTimeUs: Long?,
    val bestReactionTimeUs: Long?
)
