package core.input

enum class MidiDeviceClass { KEYBOARD, DRUM_PAD, DRUM_KIT, UNKNOWN }

object MidiDeviceClassifier {
    fun classify(description: String?, observedPitches: Set<Int>, observedChannels: Set<Int> = emptySet()): MidiDeviceClass {
        val name = description.orEmpty().lowercase()
        val drumRange = observedPitches.count { GeneralMidiDrumProfile.resolveInput(it) != null }
        return when {
            "drum" in name && "pad" in name -> MidiDeviceClass.DRUM_PAD
            "drum" in name || 9 in observedChannels -> MidiDeviceClass.DRUM_KIT
            observedPitches.size >= 4 && drumRange * 2 >= observedPitches.size -> MidiDeviceClass.DRUM_PAD
            observedPitches.size >= 12 -> MidiDeviceClass.KEYBOARD
            else -> MidiDeviceClass.UNKNOWN
        }
    }
}
