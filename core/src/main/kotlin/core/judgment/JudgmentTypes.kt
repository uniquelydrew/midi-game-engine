package core.judgment

data class TimingWindow(
    val perfectUs: Long,
    val goodUs: Long
)

enum class Judgment {
    Perfect,
    Good,
    Miss
}

enum class MissReason {
    NoInput,
    WrongKey,
    TimingRelease
}

enum class TimingMissDetail {
    EarlyStartOnly,
    LateStartOnly,
    EarlyReleaseOnly,
    LateReleaseOnly,
    EarlyStartEarlyRelease,
    EarlyStartLateRelease,
    LateStartEarlyRelease,
    LateStartLateRelease
}

data class JudgmentResult(
    val noteId: Int,
    val pitch: Int,
    val expectedTimeUs: Long,
    val expectedEndTimeUs: Long,
    val actualStartTimeUs: Long? = null,
    val actualEndTimeUs: Long? = null,
    val startDeltaUs: Long? = null,
    val endDeltaUs: Long? = null,
    val judgment: Judgment,
    val missReason: MissReason? = null,
    val missDetail: TimingMissDetail? = null
)

data class ScoreSummary(
    val totalNotes: Int,
    val judgedNotes: Int,
    val perfectCount: Int,
    val goodCount: Int,
    val missCount: Int,
    val scorePercent: Int,
    val noInputCount: Int,
    val wrongKeyCount: Int,
    val timingReleaseCount: Int
)

data class InputFeedback(
    val noteId: Int?,
    val pitch: Int,
    val judgment: Judgment,
    val missReason: MissReason? = null,
    val missDetail: TimingMissDetail? = null,
    val message: String
)
