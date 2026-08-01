package core.visualization

import kotlin.test.Test
import kotlin.test.assertEquals

class PitchNamesTest {

    @Test
    fun `pitch names use standard MIDI octave numbering`() {
        assertEquals("A0", PitchNames.name(21))
        assertEquals("C4", PitchNames.name(60))
        assertEquals("C8", PitchNames.name(108))
    }
}
