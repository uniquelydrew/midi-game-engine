package core.model

data class SongModel(
    val ticksPerQuarterNote: Int,
    val tracks: List<SongTrack>
)

data class SongTrack(
    val id: String,
    val notes: List<SongNote>
)

data class SongNote(
    val pitch: Int,
    val velocity: Int,
    val startTick: Long,
    val durationTicks: Long
)
