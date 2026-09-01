package core.input

import core.midi.MidiEvent
import core.midi.NoteOn

data class MidiLearnResult(val padIndex: Int, val midiPitch: Int, val velocity: Int, val channel: Int)

/** Input interceptor used by a UI setup flow before normal judgment receives notes. */
class MidiLearnSession {
    var awaitingPadIndex: Int? = null
        private set
    fun awaitPad(padIndex: Int) { awaitingPadIndex = padIndex }
    fun cancel() { awaitingPadIndex = null }
    fun consume(event: MidiEvent): MidiLearnResult? {
        val index = awaitingPadIndex ?: return null
        val note = event as? NoteOn ?: return null
        if (note.velocity <= 0) return null
        awaitingPadIndex = null
        return MidiLearnResult(index, note.pitch, note.velocity, note.channel)
    }
}
