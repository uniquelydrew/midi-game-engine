package core.visualization

object PitchNames {
    private val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun name(pitch: Int): String {
        require(pitch in 0..127) { "MIDI pitch must be between 0 and 127" }
        return "${names[pitch % 12]}${pitch / 12 - 1}"
    }
}
