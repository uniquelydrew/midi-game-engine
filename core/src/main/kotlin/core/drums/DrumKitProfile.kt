package core.drums

import core.midi.NoteOn

data class DrumTrigger(
    val target: DrumTarget,
    val midiNote: Int,
    val channel: Int? = null,
    val minVelocity: Int = 1
) {
    init {
        require(midiNote in 0..127) { "MIDI note must be between 0 and 127" }
        require(channel == null || channel in 0..15) { "MIDI channel must be between 0 and 15" }
        require(minVelocity in 1..127) { "Minimum velocity must be between 1 and 127" }
    }

    fun matches(note: NoteOn): Boolean {
        return note.pitch == midiNote &&
            (channel == null || note.channel == channel) &&
            note.velocity >= minVelocity
    }
}

data class DrumKitProfile(
    val name: String,
    val triggers: List<DrumTrigger>,
    val layout: List<DrumKitLayoutPiece> = DrumKitLayouts.standard()
) {
    init {
        require(name.isNotBlank()) { "Drum kit profile name cannot be blank" }
        require(layout.map { it.target }.distinct().size == layout.size) { "A kit layout may contain each target once" }
    }

    fun resolve(note: NoteOn): DrumTarget? {
        return triggers.firstOrNull { it.matches(note) }?.target
    }

    fun availableTargets(): List<DrumTarget> {
        return triggers.map { it.target }.distinct()
    }
}
