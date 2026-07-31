package core.midi

interface MidiInput {
    fun start()
    fun stop()
    fun setListener(listener: (MidiEvent) -> Unit)
}
