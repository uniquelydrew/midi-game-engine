package core.visualization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Keyboard88LayoutTest {

    @Test
    fun `first key maps to the left edge`() {
        val key = Keyboard88Layout.keyGeometry(21, 880f)

        assertFalse(key.black)
        assertEquals(0f, key.left, 0.01f)
    }

    @Test
    fun `last key maps to the right edge`() {
        val key = Keyboard88Layout.keyGeometry(108, 880f)

        assertFalse(key.black)
        assertEquals(880f, key.left + key.width, 0.01f)
    }

    @Test
    fun `black and white note classes are recognized correctly`() {
        assertTrue(Keyboard88Layout.isBlackPitch(22))
        assertFalse(Keyboard88Layout.isBlackPitch(21))
    }
}
