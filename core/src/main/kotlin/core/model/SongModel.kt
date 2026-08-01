package core.model

data class SongModel(
    val ticksPerQuarterNote: Int,
    val tempoUsPerQuarterNote: Long = 500_000L,
    val tracks: List<SongTrack>,
    val tempoChanges: List<TempoChange> = emptyList(),
    val durationTicks: Long = 0L
)

data class TempoChange(
    val tick: Long,
    val microsecondsPerQuarterNote: Long
)

data class SongTrack(
    val id: String,
    val notes: List<SongNote>,
    val name: String? = null
)

data class SongNote(
    val pitch: Int,
    val velocity: Int,
    val startTick: Long,
    val durationTicks: Long
)
