package core.chart

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackWindowTest {

    @Test
    fun `trim window removes silence and preserves padding`() {
        val chart = PlayableChart(
            listOf(
                ExpectedInput(60, 1_000_000L, 500_000L),
                ExpectedInput(64, 3_000_000L, 250_000L)
            )
        )

        val window = PlaybackWindow.fromChart(chart)

        assertEquals(950_000L, window.startUs)
        assertEquals(3_300_000L, window.endUs)
    }

    @Test
    fun `empty chart has a safe zero window`() {
        val window = PlaybackWindow.fromChart(PlayableChart(emptyList()))

        assertEquals(0L, window.startUs)
        assertEquals(0L, window.endUs)
    }

    @Test
    fun `trim window accepts adjustable padding`() {
        val chart = PlayableChart(
            listOf(ExpectedInput(60, 1_000_000L, 500_000L))
        )

        val window = PlaybackWindow.fromChart(chart, paddingUs = 250_000L)

        assertEquals(750_000L, window.startUs)
        assertEquals(1_750_000L, window.endUs)
    }

    @Test
    fun `playback settings clamp trim padding`() {
        assertEquals(0, PlaybackSettings(trimPaddingMs = -10).normalizedTrimPaddingMs)
        assertEquals(2_000, PlaybackSettings(trimPaddingMs = 3_000).normalizedTrimPaddingMs)
    }
}
