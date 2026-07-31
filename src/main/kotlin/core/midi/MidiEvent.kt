package core.midi

sealed interface MidiEvent {
    val timestampUs: Long
}

data class NoteOn(
    override val timestampUs: Long,
    val pitch: Int,
    val velocity: Int,
    val channel: Int
) : MidiEvent

data class NoteOff(
    override val timestampUs: Long,
    val pitch: Int,
    val channel: Int
) : MidiEvent

data class ControlChange(
    override val timestampUs: Long,
    val controller: Int,
    val value: Int,
    val channel: Int
) : MidiEvent
