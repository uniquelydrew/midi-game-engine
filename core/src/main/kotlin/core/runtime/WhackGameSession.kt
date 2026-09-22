package core.runtime

import core.drums.DrumInputMapper
import core.drums.DrumKitProfile
import core.drums.DrumTarget
import core.midi.MidiEvent
import core.strike.StrikeFeedback
import core.strike.StrikeJudgmentEngine
import core.strike.StrikeOutcome
import core.strike.StrikeResolution
import core.strike.StrikeScoreSummary
import core.strike.StrikeTarget
import kotlin.random.Random

data class WhackGameConfig(
    val targetDurationUs: Long = 1_500_000L,
    val interTargetDelayUs: Long = 250_000L
) {
    init {
        require(targetDurationUs > 0L) { "Target duration must be positive" }
        require(interTargetDelayUs >= 0L) { "Inter-target delay cannot be negative" }
    }
}

interface WhackTargetSelector {
    fun nextTarget(previous: DrumTarget?): DrumTarget
}

class RandomWhackTargetSelector(
    private val targets: List<DrumTarget>,
    private val random: Random = Random.Default
) : WhackTargetSelector {
    init {
        require(targets.isNotEmpty()) { "At least one drum target is required" }
    }

    override fun nextTarget(previous: DrumTarget?): DrumTarget {
        if (targets.size == 1) return targets.single()

        val candidates = if (previous == null) {
            targets
        } else {
            targets.filterNot { it == previous }.ifEmpty { targets }
        }
        return candidates[random.nextInt(candidates.size)]
    }
}

class WhackGameSession(
    profile: DrumKitProfile,
    selector: WhackTargetSelector? = null,
    private val config: WhackGameConfig = WhackGameConfig(),
    private val judgmentEngine: StrikeJudgmentEngine = StrikeJudgmentEngine()
) {
    private val mapper = DrumInputMapper(profile)
    private val followsProfileTargets = selector == null
    private var targetSelector: WhackTargetSelector =
        selector ?: RandomWhackTargetSelector(profile.availableTargets())
    private var nextTargetId = 1L
    private var previousTarget: DrumTarget? = null
    private var started = false

    init {
        require(profile.availableTargets().isNotEmpty()) {
            "Whack game session requires at least one mapped drum target"
        }
    }

    fun start(startTimeUs: Long = 0L) {
        check(!started) { "Whack game session has already started" }
        started = true
        scheduleTarget(startTimeUs)
    }

    fun updateProfile(profile: DrumKitProfile) {
        require(profile.availableTargets().isNotEmpty()) {
            "Whack game session requires at least one mapped drum target"
        }

        mapper.updateProfile(profile)
        if (followsProfileTargets) {
            targetSelector = RandomWhackTargetSelector(profile.availableTargets())
        }
    }

    fun currentProfile(): DrumKitProfile = mapper.currentProfile()

    fun currentTarget(): StrikeTarget? = judgmentEngine.currentTarget()

    fun onInput(event: MidiEvent): StrikeFeedback? {
        check(started) { "Whack game session must be started before accepting input" }

        advanceTo(event.timestampUs)

        val strike = mapper.map(event) ?: return null
        val feedback = judgmentEngine.onStrike(strike)

        if (feedback?.outcome == StrikeOutcome.HIT) {
            scheduleTarget(strike.timestampUs + config.interTargetDelayUs)
        }

        return feedback
    }

    fun advanceTo(timeUs: Long): List<StrikeResolution> {
        check(started) { "Whack game session must be started before advancing time" }

        val resolved = mutableListOf<StrikeResolution>()
        while (true) {
            val miss = judgmentEngine.advanceTo(timeUs) ?: break
            resolved += miss
            scheduleTarget(miss.target.expiresAtUs + config.interTargetDelayUs)
        }
        return resolved
    }

    fun results(): List<StrikeResolution> = judgmentEngine.results()

    fun scoreSummary(): StrikeScoreSummary = judgmentEngine.scoreSummary()

    private fun scheduleTarget(appearsAtUs: Long) {
        val target = targetSelector.nextTarget(previousTarget)
        judgmentEngine.loadTarget(
            StrikeTarget(
                id = nextTargetId++,
                target = target,
                appearsAtUs = appearsAtUs,
                expiresAtUs = appearsAtUs + config.targetDurationUs
            )
        )
        previousTarget = target
    }
}
