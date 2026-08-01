package core.midi

import core.chart.ChartGenerator
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StandardMidiFileLoaderTest {

    @Test
    fun `valid single-track MIDI imports successfully`() {
        val song = StandardMidiFileLoader.load(singleTrackMidiBytes())

        assertEquals(480, song.ticksPerQuarterNote)
        assertEquals(500_000L, song.tempoUsPerQuarterNote)
        assertEquals(1, song.tracks.size)
        assertEquals(1, song.tracks.single().notes.size)
    }

    @Test
    fun `multi-track MIDI exposes selectable tracks`() {
        val song = StandardMidiFileLoader.load(multiTrackMidiBytes())

        assertEquals(2, song.tracks.size)
        assertEquals("track-0", song.tracks[0].id)
        assertEquals("track-1", song.tracks[1].id)
    }

    @Test
    fun `malformed MIDI is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            StandardMidiFileLoader.load(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        }
    }

    @Test
    fun `note timing converts consistently into chart time`() {
        val song = StandardMidiFileLoader.load(singleTrackMidiBytes())
        val chart = ChartGenerator.fromSong(song)

        val note = chart.events.single()
        assertEquals(60, note.pitch)
        assertEquals(500_000L, note.targetTimeUs)
        assertEquals(500_000L, note.durationUs)
    }

    @Test
    fun `tempo changes are retained in the complete MIDI model`() {
        val song = StandardMidiFileLoader.load(singleTrackMidiBytes())

        assertEquals(1, song.tempoChanges.size)
        assertEquals(0L, song.tempoChanges.single().tick)
        assertEquals(500_000L, song.tempoChanges.single().microsecondsPerQuarterNote)
    }

    private fun singleTrackMidiBytes(): ByteArray {
        val track = buildTrack(
            notePitch = 60,
            noteStartDelayTicks = 480,
            noteDurationTicks = 480
        )
        return buildMidiFile(track, trackCount = 1)
    }

    private fun multiTrackMidiBytes(): ByteArray {
        val emptyTrack = byteArrayOf(
            0x00,
            0xFF.toByte(),
            0x2F.toByte(),
            0x00
        )
        val header = buildHeader(trackCount = 2, format = 1)
        return header + buildChunk(emptyTrack) + buildChunk(emptyTrack)
    }

    private fun buildMidiFile(track: ByteArray, trackCount: Int): ByteArray {
        return buildHeader(trackCount, format = 0) + buildChunk(track)
    }

    private fun buildHeader(trackCount: Int, format: Int): ByteArray {
        return buildString {
            append("MThd")
        }.toByteArray(Charsets.US_ASCII) +
            intToBytes(6) +
            shortToBytes(format) +
            shortToBytes(trackCount) +
            shortToBytes(480)
    }

    private fun buildChunk(data: ByteArray): ByteArray {
        return buildString { append("MTrk") }.toByteArray(Charsets.US_ASCII) +
            intToBytes(data.size) +
            data
    }

    private fun buildTrack(notePitch: Int, noteStartDelayTicks: Int, noteDurationTicks: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(varLen(0))
        out.write(byteArrayOf(0xFF.toByte(), 0x51.toByte(), 0x03, 0x07, 0xA1.toByte(), 0x20))
        out.write(varLen(noteStartDelayTicks))
        out.write(byteArrayOf(0x90.toByte(), notePitch.toByte(), 0x64))
        out.write(varLen(noteDurationTicks))
        out.write(byteArrayOf(0x80.toByte(), notePitch.toByte(), 0x40))
        out.write(varLen(0))
        out.write(byteArrayOf(0xFF.toByte(), 0x2F.toByte(), 0x00))
        return out.toByteArray()
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun shortToBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun varLen(value: Int): ByteArray {
        var buffer = value and 0x7F
        var remaining = value ushr 7
        while (remaining > 0) {
            buffer = (buffer shl 8) or ((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }

        val out = mutableListOf<Byte>()
        var current = buffer
        while (true) {
            out += (current and 0xFF).toByte()
            if (current and 0x80 != 0) {
                current = current ushr 8
            } else {
                break
            }
        }
        return out.toByteArray()
    }
}
