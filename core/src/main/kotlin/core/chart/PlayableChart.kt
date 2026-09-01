package core.chart

data class ExpectedInput(
    val id: Int = -1,
    val pitch: Int,
    val targetTimeUs: Long,
    val durationUs: Long = 0L,
    val velocity: Int = 96,
    val channel: Int? = null
) {
    val endTimeUs: Long
        get() = targetTimeUs + durationUs
}

data class PlayableChart(
    val events: List<ExpectedInput>
)
