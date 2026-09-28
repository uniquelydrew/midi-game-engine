package core.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhackDifficultyTest {
    @Test
    fun `difficulty presets monotonically shorten target and gap durations`() {
        val ordered = listOf(
            WhackDifficulty.RELAXED,
            WhackDifficulty.STANDARD,
            WhackDifficulty.FAST,
            WhackDifficulty.EXPERT
        )

        ordered.zipWithNext().forEach { (easier, harder) ->
            assertTrue(harder.targetDurationUs < easier.targetDurationUs)
            assertTrue(harder.interTargetDelayUs < easier.interTargetDelayUs)
        }
    }

    @Test
    fun `preset config preserves declared timings`() {
        val difficulty = WhackDifficulty.EXPERT
        val config = difficulty.config()

        assertEquals(difficulty.targetDurationUs, config.targetDurationUs)
        assertEquals(difficulty.interTargetDelayUs, config.interTargetDelayUs)
    }
}
