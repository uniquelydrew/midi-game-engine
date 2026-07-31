package android.midi

import core.midi.*
import core.time.Transport

class AndroidMidiInput(
    private val transport: Transport
) : MidiInput {

    private var listener: ((MidiEvent) -> Unit)? = null

    override fun setListener(listener: (MidiEvent) -> Unit) {
        this.listener = listener
    }

    override fun start() {
        // TODO: Hook into android.media.midi.MidiManager
    }

    override fun stop() {
        // TODO: Close device
    }

    // Placeholder method to demonstrate normalization
    fun onRawMidi(pitch: Int, velocity: Int, on: Boolean) {
        val timeUs = transport.positionNs() / 1000

        val event = if (on) {
            NoteOn(timeUs, pitch, velocity, 0)
        } else {
            NoteOff(timeUs, pitch, 0)
        }

        listener?.invoke(event)
    }
}
