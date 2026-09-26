package core.drums

import core.midi.NoteOff
import core.midi.NoteOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrumInputMapperTest {
    private val profile = DrumKitProfile(
        name = "Test kit",
        triggers = listOf(
            DrumTrigger(DrumTarget.SNARE, midiNote = 38, channel = 9),
            DrumTrigger(DrumTarget.KICK, midiNote = 36)
        )
    )

    @Test
    fun `maps configured note-on events to logical drum targets`() {
        val mapper = DrumInputMapper(profile)

        val snare = mapper.map(NoteOn(100_000L, pitch = 38, velocity = 110, channel = 9))
        val kick = mapper.map(NoteOn(200_000L, pitch = 36, velocity = 95, channel = 2))

        assertEquals(DrumTarget.SNARE, snare?.target)
        assertEquals(38, snare?.midiNote)
        assertEquals(110, snare?.velocity)
        assertEquals(DrumTarget.KICK, kick?.target)
    }

    @Test
    fun `ignores unmatched channels notes and note-off events`() {
        val mapper = DrumInputMapper(profile)

        assertNull(mapper.map(NoteOn(100_000L, pitch = 38, velocity = 110, channel = 8)))
        assertNull(mapper.map(NoteOn(100_000L, pitch = 40, velocity = 110, channel = 9)))
        assertNull(mapper.map(NoteOff(100_000L, pitch = 38, channel = 9)))
    }
}
