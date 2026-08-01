package core.chart

data class ExpectedInput(
    val pitch: Int,
    val targetTimeUs: Long,
    val durationUs: Long = 0L,
    val velocity: Int = 96,
    var matched: Boolean = false
)

data class PlayableChart(
    val events: List<ExpectedInput>
)
