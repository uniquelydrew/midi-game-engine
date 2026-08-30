package core.chart

import core.judgment.Judgment
import core.judgment.MissReason
import core.judgment.TimingMissDetail

data class ExpectedInput(
    val id: Int = -1,
    val pitch: Int,
    val targetTimeUs: Long,
    val durationUs: Long = 0L,
    val velocity: Int = 96,
    var matched: Boolean = false,
    var judgment: Judgment? = null,
    var missReason: MissReason? = null,
    var missDetail: TimingMissDetail? = null
) {
    val endTimeUs: Long
        get() = targetTimeUs + durationUs
}

data class PlayableChart(
    val events: List<ExpectedInput>
)
