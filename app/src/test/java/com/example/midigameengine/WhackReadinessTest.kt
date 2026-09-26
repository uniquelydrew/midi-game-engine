package com.example.midigameengine

import core.drums.DrumTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class WhackReadinessTest {
    @Test
    fun `readiness guides device and mapping setup in order`() {
        assertEquals(
            WhackReadiness.NO_DEVICE,
            WhackReadiness.derive(false, 0, false, false, null)
        )
        assertEquals(
            WhackReadiness.NEEDS_CONFIGURATION,
            WhackReadiness.derive(true, 0, false, false, null)
        )
    }

    @Test
    fun `one mapped pad is ready and game transitions pause and resume`() {
        assertEquals(
            WhackReadiness.READY,
            WhackReadiness.derive(true, 1, false, false, null)
        )
        assertEquals(
            WhackReadiness.PLAYING,
            WhackReadiness.derive(true, 1, true, true, null)
        )
        assertEquals(
            WhackReadiness.PAUSED,
            WhackReadiness.derive(true, 1, false, true, null)
        )
    }

    @Test
    fun `learn mode is explicit and takes precedence over ready state`() {
        assertEquals(
            WhackReadiness.LEARNING_MAPPING,
            WhackReadiness.derive(true, 1, false, true, DrumTarget.SNARE)
        )
    }
}
