package core.runtime

/** A non-empty transport-time range used for repeated practice. */
data class PracticeLoop(
    val startUs: Long,
    val endUs: Long
) {
    init {
        require(startUs >= 0L) { "Loop start must not be negative" }
        require(endUs > startUs) { "Loop end must be after start" }
    }

    fun contains(timeUs: Long): Boolean = timeUs in startUs until endUs

    fun clamp(timeUs: Long): Long = timeUs.coerceIn(startUs, endUs)
}

sealed interface LoopStopRule {
    data object Forever : LoopStopRule
    data class PassCount(val passes: Int) : LoopStopRule {
        init { require(passes > 0) { "Pass count must be positive" } }
    }
    data class Duration(val durationMs: Long) : LoopStopRule {
        init { require(durationMs > 0L) { "Practice duration must be positive" } }
    }
}

data class PracticeLoopSnapshot(
    val activeElapsedMs: Long,
    val completedPasses: Int,
    val isRunning: Boolean,
    val isComplete: Boolean
)

/**
 * Tracks a loop practice session independently of transport rate. Durations are
 * wall-clock practice time and only complete at a loop boundary.
 */
class PracticeLoopSession(
    private val stopRule: LoopStopRule
) {
    private var activeStartedAtNs: Long? = null
    private var accumulatedActiveNs: Long = 0L
    private var completedPasses: Int = 0
    private var complete = false

    fun start(nowNs: Long) {
        if (!complete && activeStartedAtNs == null) activeStartedAtNs = nowNs
    }

    fun pause(nowNs: Long) {
        activeStartedAtNs?.let { startedAt ->
            accumulatedActiveNs += (nowNs - startedAt).coerceAtLeast(0L)
            activeStartedAtNs = null
        }
    }

    fun reset() {
        activeStartedAtNs = null
        accumulatedActiveNs = 0L
        completedPasses = 0
        complete = false
    }

    /** Records a completed A-to-B pass and reports whether playback must stop. */
    fun completePass(nowNs: Long): Boolean {
        if (complete) return true
        completedPasses += 1
        if (shouldFinishAtBoundary(nowNs)) {
            pause(nowNs)
            complete = true
        }
        return complete
    }

    fun snapshot(nowNs: Long): PracticeLoopSnapshot = PracticeLoopSnapshot(
        activeElapsedMs = activeElapsedNs(nowNs) / 1_000_000L,
        completedPasses = completedPasses,
        isRunning = activeStartedAtNs != null,
        isComplete = complete
    )

    private fun shouldFinishAtBoundary(nowNs: Long): Boolean = when (stopRule) {
        LoopStopRule.Forever -> false
        is LoopStopRule.PassCount -> completedPasses >= stopRule.passes
        is LoopStopRule.Duration -> activeElapsedNs(nowNs) >= stopRule.durationMs * 1_000_000L
    }

    private fun activeElapsedNs(nowNs: Long): Long = accumulatedActiveNs +
        (activeStartedAtNs?.let { (nowNs - it).coerceAtLeast(0L) } ?: 0L)
}
