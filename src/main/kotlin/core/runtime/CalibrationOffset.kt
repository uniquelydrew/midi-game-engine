package core.runtime

import core.midi.MidiEvent

class CalibrationOffset(
    private var offsetUs: Long = 0
) {

    fun setOffset(offsetUs: Long) {
        this.offsetUs = offsetUs
    }

    fun apply(event: MidiEvent): MidiEvent {
        return when (event) {
            is core.midi.NoteOn -> event.copy(timestampUs = event.timestampUs - offsetUs)
            is core.midi.NoteOff -> event.copy(timestampUs = event.timestampUs - offsetUs)
            is core.midi.ControlChange -> event.copy(timestampUs = event.timestampUs - offsetUs)
        }
    }
}
