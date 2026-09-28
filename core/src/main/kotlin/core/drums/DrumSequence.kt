package core.drums

import core.chart.ExpectedInput

sealed interface DrumSequenceAssignment {
    data class Target(val target: DrumTarget) : DrumSequenceAssignment
    data object Ignore : DrumSequenceAssignment
}

/** Maps authored MIDI drum pitches to logical targets independently of a player's kit. */
data class DrumSequenceProfile(
    val assignments: Map<Int, DrumSequenceAssignment>
) {
    init { require(assignments.keys.all { it in 0..127 }) { "MIDI pitches must be 0..127" } }

    fun assignmentFor(midiNote: Int): DrumSequenceAssignment? = assignments[midiNote]

    fun targetFor(midiNote: Int): DrumTarget? =
        (assignmentFor(midiNote) as? DrumSequenceAssignment.Target)?.target

    fun unclassifiedPitches(notes: Collection<Int>): Set<Int> =
        notes.filter { it !in assignments }.toSet()

    companion object {
        fun generalMidi(): DrumSequenceProfile = DrumSequenceProfile(
            DrumKitProfiles.generalMidi().triggers.associate { trigger ->
                trigger.midiNote to DrumSequenceAssignment.Target(trigger.target)
            }
        )
    }
}

data class DrumSequenceEvent(
    val id: Int,
    val sourceMidiNote: Int,
    val target: DrumTarget,
    val targetTimeUs: Long
)

data class DrumSequence(
    val events: List<DrumSequenceEvent>,
    val unclassifiedSourcePitches: Set<Int>
) {
    val endUs: Long get() = events.maxOfOrNull { it.targetTimeUs } ?: 0L

    companion object {
        fun fromChart(chart: List<ExpectedInput>, profile: DrumSequenceProfile): DrumSequence {
            val sourcePitches = chart.map { it.pitch }.toSet()
            return DrumSequence(
                events = chart.mapNotNull { event ->
                    profile.targetFor(event.pitch)?.let { target ->
                        DrumSequenceEvent(event.id, event.pitch, target, event.targetTimeUs)
                    }
                }.sortedBy { it.targetTimeUs },
                unclassifiedSourcePitches = profile.unclassifiedPitches(sourcePitches)
            )
        }
    }
}
