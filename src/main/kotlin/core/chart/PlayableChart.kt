package core.chart

data class ExpectedInput(
    val pitch: Int,
    val targetTimeUs: Long,
    var matched: Boolean = false
)

data class PlayableChart(
    val events: List<ExpectedInput>
)
