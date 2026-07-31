package core.runtime

import core.midi.MidiEvent

class InputRouter {

    private var downstream: ((MidiEvent) -> Unit)? = null

    fun setOutput(consumer: (MidiEvent) -> Unit) {
        downstream = consumer
    }

    fun onEvent(event: MidiEvent) {
        // Future expansion points:
        // - device filtering
        // - channel remapping
        // - latency compensation
        // - multi-device merge
        // - instrument profile transforms

        downstream?.invoke(event)
    }
}
