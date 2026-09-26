package core.drums

data class DrumStrike(
    val target: DrumTarget,
    val midiNote: Int,
    val velocity: Int,
    val channel: Int,
    val timestampUs: Long
)
