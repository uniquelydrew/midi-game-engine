package core.midi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MidiByteStreamParserTest {
    @Test
    fun `parses multiple channel messages from one callback`() {
        val parser = MidiByteStreamParser()

        val messages = parser.feed(
            byteArrayOf(
                0x90.toByte(), 60, 100,
                0x80.toByte(), 60, 0
            )
        )

        assertEquals(2, messages.size)
        assertEquals(0x90, messages[0].command)
        assertEquals(60, messages[0].data1)
        assertEquals(100, messages[0].data2)
        assertEquals(0x80, messages[1].command)
    }

    @Test
    fun `supports running status for repeated drum strikes`() {
        val parser = MidiByteStreamParser()

        val messages = parser.feed(
            byteArrayOf(
                0x99.toByte(),
                36, 110,
                38, 120,
                42, 96
            )
        )

        assertEquals(listOf(36, 38, 42), messages.map { it.data1 })
        assertEquals(listOf(110, 120, 96), messages.map { it.data2 })
        assertTrue(messages.all { it.command == 0x90 && it.channel == 9 })
    }

    @Test
    fun `retains partial channel message across callbacks`() {
        val parser = MidiByteStreamParser()

        assertTrue(
            parser.feed(byteArrayOf(0x90.toByte(), 60)).isEmpty()
        )
        val messages = parser.feed(byteArrayOf(100))

        assertEquals(1, messages.size)
        assertEquals(60, messages.single().data1)
        assertEquals(100, messages.single().data2)
    }

    @Test
    fun `real-time bytes do not disturb running status`() {
        val parser = MidiByteStreamParser()

        val messages = parser.feed(
            byteArrayOf(
                0x90.toByte(), 60, 100,
                0xF8.toByte(),
                61, 110
            )
        )

        assertEquals(listOf(60, 61), messages.map { it.data1 })
    }

    @Test
    fun `system common status clears running status`() {
        val parser = MidiByteStreamParser()

        val first = parser.feed(byteArrayOf(0x90.toByte(), 60, 100))
        val afterSystem = parser.feed(
            byteArrayOf(
                0xF1.toByte(), 0x7F,
                61, 110
            )
        )

        assertEquals(1, first.size)
        assertTrue(afterSystem.isEmpty())
    }
}
