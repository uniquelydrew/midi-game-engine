package core.midi

import core.model.SongModel
import core.model.SongNote
import core.model.SongTrack
import core.model.TempoChange
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.math.max

object StandardMidiFileLoader {

    fun load(bytes: ByteArray): SongModel {
        val input = DataInputStream(ByteArrayInputStream(bytes))

        require(readInt(input) == 0x4D546864) { "Missing MIDI header" }
        val headerLength = readInt(input)
        require(headerLength >= 6) { "Invalid MIDI header length: $headerLength" }

        val format = input.readUnsignedShort()
        val trackCount = input.readUnsignedShort()
        val division = input.readUnsignedShort()
        if (headerLength > 6) {
            input.skipBytes(headerLength - 6)
        }

        require(division and 0x8000 == 0) { "SMPTE time code is not supported" }
        require(format in 0..1) { "Only Standard MIDI format 0 or 1 is supported" }
        require(trackCount > 0) { "MIDI file contains no tracks" }
        require(format != 0 || trackCount == 1) {
            "Format 0 MIDI files must contain exactly one track"
        }
        val ticksPerQuarterNote = division

        var tempoUsPerQuarterNote = 500_000L
        val tracks = mutableListOf<SongTrack>()
        val tempoChanges = mutableListOf<TempoChange>()
        var durationTicks = 0L

        repeat(trackCount) { trackIndex ->
            require(readInt(input) == 0x4D54726B) { "Missing track chunk $trackIndex" }
            val trackLength = readInt(input)
            require(trackLength >= 0) { "Invalid track length: $trackLength" }
            val trackData = ByteArray(trackLength)
            input.readFully(trackData)

            val parsed = parseTrack(trackData)
            parsed.tempoChanges.forEach { change ->
            tempoChanges += change
            }
            durationTicks = max(durationTicks, parsed.endTick)
            parsed.tempoChanges.firstOrNull { it.tick == 0L }?.let {
                tempoUsPerQuarterNote = it.microsecondsPerQuarterNote
            }
            tracks += SongTrack(
                id = "track-$trackIndex",
                name = parsed.name,
                notes = parsed.notes
            )
        }

        return SongModel(
            ticksPerQuarterNote = ticksPerQuarterNote,
            tempoUsPerQuarterNote = tempoUsPerQuarterNote,
            tracks = tracks,
            tempoChanges = tempoChanges
                .distinctBy { it.tick }
                .sortedBy { it.tick },
            durationTicks = durationTicks
        )
    }

    private data class ParsedTrack(
        val notes: List<SongNote>,
        val tempoChanges: List<TempoChange>,
        val name: String?,
        val endTick: Long
    )

    private fun parseTrack(trackData: ByteArray): ParsedTrack {
        val reader = MidiTrackReader(trackData)
        var tick = 0L
        var runningStatus = -1
        val tempoChanges = mutableListOf<TempoChange>()
        var name: String? = null
        val activeNotes = mutableMapOf<Int, MutableList<Long>>()
        val notes = mutableListOf<SongNote>()

        trackLoop@ while (!reader.isEof()) {
            tick += reader.readVarLen().toLong()

            var status = reader.readUnsignedByte()
            if (status < 0x80) {
                reader.pushBack(status)
                status = runningStatus
            } else {
                runningStatus = status
            }

            when {
                status == 0xFF -> {
                    val metaType = reader.readUnsignedByte()
                    val length = reader.readVarLen()
                    when (metaType) {
                        0x2F -> {
                            reader.skip(length)
                            break@trackLoop
                        }
                        0x51 -> {
                            if (length == 3) {
                                val tempo =
                                    (reader.readUnsignedByte() shl 16) or
                                        (reader.readUnsignedByte() shl 8) or
                                        reader.readUnsignedByte()
                                tempoChanges += TempoChange(tick, tempo.toLong())
                            } else {
                                reader.skip(length)
                            }
                        }
                        0x03 -> {
                            val nameBytes = ByteArray(length)
                            repeat(length) { index -> nameBytes[index] = reader.readUnsignedByte().toByte() }
                            name = nameBytes.toString(Charsets.UTF_8).trim().ifEmpty { null }
                        }
                        else -> reader.skip(length)
                    }
                }
                status == 0xF0 || status == 0xF7 -> {
                    reader.skip(reader.readVarLen())
                }
                status >= 0 -> {
                    val command = status and 0xF0
                    val pitch = reader.readUnsignedByte()
                    val velocity = when (command) {
                        0xC0, 0xD0 -> 0
                        else -> reader.readUnsignedByte()
                    }

                    when (command) {
                        0x90 -> {
                            if (velocity > 0) {
                                activeNotes.getOrPut(pitch) { mutableListOf() }.add(tick)
                            } else {
                                closeNote(activeNotes, notes, pitch, tick, velocity)
                            }
                        }
                        0x80 -> closeNote(activeNotes, notes, pitch, tick, velocity)
                        else -> Unit
                    }
                }
            }
        }

        return ParsedTrack(notes, tempoChanges, name, tick)
    }

    private fun closeNote(
        activeNotes: MutableMap<Int, MutableList<Long>>,
        notes: MutableList<SongNote>,
        pitch: Int,
        endTick: Long,
        velocity: Int
    ) {
        val starts = activeNotes[pitch] ?: return
        val startTick = starts.removeLastOrNull() ?: return
        if (starts.isEmpty()) {
            activeNotes.remove(pitch)
        }

        notes += SongNote(
            pitch = pitch,
            velocity = max(velocity, 1),
            startTick = startTick,
            durationTicks = max(endTick - startTick, 1L)
        )
    }

    private fun readInt(input: DataInputStream): Int {
        return (input.readUnsignedByte() shl 24) or
            (input.readUnsignedByte() shl 16) or
            (input.readUnsignedByte() shl 8) or
            input.readUnsignedByte()
    }

    private class MidiTrackReader(private val bytes: ByteArray) {
        private var index = 0
        private var pushedBack: Int? = null

        fun isEof(): Boolean = pushedBack == null && index >= bytes.size

        fun readUnsignedByte(): Int {
            pushedBack?.let {
                pushedBack = null
                return it
            }
            require(index < bytes.size) { "Unexpected EOF in track" }
            return bytes[index++].toInt() and 0xFF
        }

        fun pushBack(value: Int) {
            pushedBack = value and 0xFF
        }

        fun readVarLen(): Int {
            var value = 0
            while (true) {
                val b = readUnsignedByte()
                value = (value shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) break
            }
            return value
        }

        fun skip(count: Int) {
            repeat(count) { readUnsignedByte() }
        }
    }
}
