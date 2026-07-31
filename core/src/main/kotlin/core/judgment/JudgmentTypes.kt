package core.judgment

data class TimingWindow(
    val perfectUs: Long,
    val greatUs: Long,
    val goodUs: Long
)

enum class Judgment {
    Perfect,
    Great,
    Good,
    Miss
}

data class JudgmentResult(
    val expectedTimeUs: Long,
    val actualTimeUs: Long,
    val rawDeltaUs: Long,
    val judgment: Judgment
)
