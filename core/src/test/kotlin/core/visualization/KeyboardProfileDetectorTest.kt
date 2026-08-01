package core.visualization

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardProfileDetectorTest {

    @Test
    fun `metadata selects explicit key count`() {
        assertEquals(
            KeyboardProfile.KEYS_61,
            KeyboardProfileDetector.detect("Casio CT-X 61-key", emptyList())
        )
    }

    @Test
    fun `observed range selects smallest safe standard profile`() {
        assertEquals(
            KeyboardProfile.KEYS_49,
            KeyboardProfileDetector.detect(null, listOf(40, 60, 80))
        )
    }

    @Test
    fun `unknown device falls back to 88 keys`() {
        assertEquals(
            KeyboardProfile.KEYS_88,
            KeyboardProfileDetector.detect("Unknown MIDI device", emptyList())
        )
    }
}
