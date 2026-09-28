package core.drums

object DrumKitProfiles {
    fun generalMidi(name: String = "General MIDI drums"): DrumKitProfile {
        return DrumKitProfile(
            name = name,
            triggers = listOf(
                DrumTrigger(DrumTarget.KICK, midiNote = 36),
                DrumTrigger(DrumTarget.SNARE, midiNote = 38),
                DrumTrigger(DrumTarget.HI_HAT, midiNote = 42),
                DrumTrigger(DrumTarget.TOM_1, midiNote = 48),
                DrumTrigger(DrumTarget.TOM_2, midiNote = 45),
                DrumTrigger(DrumTarget.FLOOR_TOM, midiNote = 41),
                DrumTrigger(DrumTarget.CRASH, midiNote = 49),
                DrumTrigger(DrumTarget.RIDE, midiNote = 51)
            )
        )
    }
}
