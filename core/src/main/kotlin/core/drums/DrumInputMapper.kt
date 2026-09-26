package core.drums

import core.midi.MidiEvent
import core.midi.NoteOn

class DrumInputMapper(
    initialProfile: DrumKitProfile
) {
    private var profile: DrumKitProfile = initialProfile

    fun updateProfile(profile: DrumKitProfile) {
        this.profile = profile
    }

    fun currentProfile(): DrumKitProfile = profile

    fun map(event: MidiEvent): DrumStrike? {
        if (event !is NoteOn) return null

        val target = profile.resolve(event) ?: return null
        return DrumStrike(
            target = target,
            midiNote = event.pitch,
            velocity = event.velocity,
            channel = event.channel,
            timestampUs = event.timestampUs
        )
    }
}
