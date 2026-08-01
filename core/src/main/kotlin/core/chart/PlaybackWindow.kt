package core.chart

data class PlaybackWindow(
    val startUs: Long,
    val endUs: Long
) {
    init {
        require(startUs >= 0L)
        require(endUs >= startUs)
    }

    val durationUs: Long
        get() = endUs - startUs

    fun clamp(positionUs: Long): Long = positionUs.coerceIn(startUs, endUs)

    companion object {
        fun fromChart(chart: PlayableChart, paddingUs: Long = 50_000L): PlaybackWindow {
            if (chart.events.isEmpty()) return PlaybackWindow(0L, 0L)
            val first = chart.events.minOfOrNull { it.targetTimeUs } ?: 0L
            val last = chart.events.maxOfOrNull { it.targetTimeUs + it.durationUs } ?: 0L
            return PlaybackWindow(
                startUs = (first - paddingUs).coerceAtLeast(0L),
                endUs = (last + paddingUs).coerceAtLeast(first)
            )
        }

        fun fullChart(chart: PlayableChart, originalEndUs: Long? = null): PlaybackWindow {
            return PlaybackWindow(0L, originalEndUs ?: chart.events.maxOfOrNull { it.targetTimeUs + it.durationUs } ?: 0L)
        }
    }
}

data class PlaybackSettings(
    val speed: Double = 1.0,
    val autoTrimEnabled: Boolean = true,
    val trimPaddingMs: Int = 50
) {
    val normalizedSpeed: Double
        get() = ((speed * 20.0).toInt() / 20.0).coerceIn(0.25, 2.0)

    val normalizedTrimPaddingMs: Int
        get() = trimPaddingMs.coerceIn(0, 2_000)

    val trimPaddingUs: Long
        get() = normalizedTrimPaddingMs * 1_000L
}
