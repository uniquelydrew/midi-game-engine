package core.drums

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrumKitLayoutTest {
    @Test fun `standard layout includes each supported target exactly once`() {
        val layout = DrumKitLayouts.standard()
        assertEquals(DrumTarget.values().toSet(), layout.map { it.target }.toSet())
        assertEquals(layout.size, layout.map { it.target }.distinct().size)
    }

    @Test fun `normalizing preserves removed piece and fills missing targets`() {
        val layout = DrumKitLayouts.normalized(listOf(DrumKitLayoutPiece(DrumTarget.SNARE, false, .1f, .2f)))
        assertFalse(layout.first { it.target == DrumTarget.SNARE }.visible)
        assertTrue(layout.any { it.target == DrumTarget.KICK })
    }
}
