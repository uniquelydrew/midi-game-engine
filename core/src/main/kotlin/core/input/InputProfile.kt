package core.input

enum class InputInstrumentType { KEYBOARD, DRUM_PADS }

enum class InputTargetCategory { KEY, KICK, SNARE, HI_HAT, TOM, CYMBAL, PERCUSSION, CUSTOM }

data class InputTarget(
    val id: String,
    val midiPitches: Set<Int>,
    val label: String,
    val shortLabel: String = label,
    val laneIndex: Int,
    val category: InputTargetCategory
)

interface InputProfile {
    val instrumentType: InputInstrumentType
    fun resolveInput(midiPitch: Int, midiChannel: Int? = null): InputTarget?
    fun displayTargets(): List<InputTarget>
}

class KeyboardInputProfile(private val firstPitch: Int = 21, private val lastPitch: Int = 108) : InputProfile {
    override val instrumentType = InputInstrumentType.KEYBOARD
    override fun resolveInput(midiPitch: Int, midiChannel: Int?): InputTarget? =
        if (midiPitch in firstPitch..lastPitch) InputTarget("key-$midiPitch", setOf(midiPitch), "$midiPitch", laneIndex = midiPitch - firstPitch, category = InputTargetCategory.KEY) else null
    override fun displayTargets(): List<InputTarget> = (firstPitch..lastPitch).mapNotNull { resolveInput(it) }
}

open class DrumPadInputProfile(private val targets: List<InputTarget>) : InputProfile {
    override val instrumentType = InputInstrumentType.DRUM_PADS
    override fun resolveInput(midiPitch: Int, midiChannel: Int?): InputTarget? = targets.firstOrNull { midiPitch in it.midiPitches }
    override fun displayTargets(): List<InputTarget> = targets.sortedBy { it.laneIndex }
}

object GeneralMidiDrumProfile : DrumPadInputProfile(
    listOf(
        InputTarget("kick", setOf(35, 36), "Kick", laneIndex = 0, category = InputTargetCategory.KICK),
        InputTarget("snare", setOf(38, 40), "Snare", laneIndex = 1, category = InputTargetCategory.SNARE),
        InputTarget("closed_hihat", setOf(42, 44), "Closed Hi-Hat", "Hi-Hat", 2, InputTargetCategory.HI_HAT),
        InputTarget("open_hihat", setOf(46), "Open Hi-Hat", "Open Hat", 3, InputTargetCategory.HI_HAT),
        InputTarget("low_tom", setOf(41, 43), "Low Tom", laneIndex = 4, category = InputTargetCategory.TOM),
        InputTarget("mid_tom", setOf(45, 47), "Mid Tom", laneIndex = 5, category = InputTargetCategory.TOM),
        InputTarget("high_tom", setOf(48, 50), "High Tom", laneIndex = 6, category = InputTargetCategory.TOM),
        InputTarget("crash", setOf(49, 52, 55, 57), "Crash", laneIndex = 7, category = InputTargetCategory.CYMBAL),
        InputTarget("ride", setOf(51, 53, 59), "Ride", laneIndex = 8, category = InputTargetCategory.CYMBAL)
    )
)
