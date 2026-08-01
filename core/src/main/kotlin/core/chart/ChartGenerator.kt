package core.chart

import core.model.SongModel
import core.model.SongTrack
import core.model.TempoChange

object ChartGenerator {

    fun fromTrack(track: SongTrack): PlayableChart {
        val events = track.notes.map {
            ExpectedInput(
                pitch = it.pitch,
                targetTimeUs = it.startTick * 1000,
                velocity = it.velocity
            )
        }
        return PlayableChart(events)
    }

    fun fromTrack(
        track: SongTrack,
        ticksPerQuarterNote: Int,
        tempoUsPerQuarterNote: Long
    ): PlayableChart {
        val events = track.notes.map {
            ExpectedInput(
                pitch = it.pitch,
                targetTimeUs = (it.startTick * tempoUsPerQuarterNote) / ticksPerQuarterNote.toLong(),
                durationUs = (it.durationTicks * tempoUsPerQuarterNote) / ticksPerQuarterNote.toLong(),
                velocity = it.velocity
            )
        }.sortedBy { it.targetTimeUs }

        return PlayableChart(events)
    }

    fun fromSong(song: SongModel, selectedTrackIds: Set<String> = song.tracks.map { it.id }.toSet()): PlayableChart {
        val selectedTracks = song.tracks.filter { it.id in selectedTrackIds }
        require(selectedTracks.isNotEmpty()) { "At least one MIDI track must be selected" }
        val tempoMap = buildList {
            if (song.tempoChanges.firstOrNull()?.tick != 0L) {
                add(TempoChange(0L, song.tempoUsPerQuarterNote))
            }
            addAll(song.tempoChanges)
        }
        return PlayableChart(
            selectedTracks
                .flatMap { track ->
                    track.notes.map { note ->
                        ExpectedInput(
                            pitch = note.pitch,
                            targetTimeUs = tickToMicros(
                                note.startTick,
                                song.ticksPerQuarterNote,
                                tempoMap
                            ),
                            durationUs = (tickToMicros(
                                note.startTick + note.durationTicks,
                                song.ticksPerQuarterNote,
                                tempoMap
                            ) - tickToMicros(
                                note.startTick,
                                song.ticksPerQuarterNote,
                                tempoMap
                            )).coerceAtLeast(1L),
                            velocity = note.velocity
                        )
                    }
                }
                .sortedBy { it.targetTimeUs }
        )
    }

    fun durationUs(song: SongModel): Long {
        val tempoMap = buildList {
            if (song.tempoChanges.firstOrNull()?.tick != 0L) {
                add(TempoChange(0L, song.tempoUsPerQuarterNote))
            }
            addAll(song.tempoChanges)
        }
        return tickToMicros(song.durationTicks, song.ticksPerQuarterNote, tempoMap)
    }

    private fun tickToMicros(tick: Long, ticksPerQuarterNote: Int, tempoMap: List<TempoChange>): Long {
        var elapsedTicks = 0L
        var elapsedMicros = 0L
        var tempo = tempoMap.firstOrNull()?.microsecondsPerQuarterNote ?: 500_000L
        for (change in tempoMap.drop(1)) {
            if (change.tick >= tick) break
            val segmentTicks = change.tick - elapsedTicks
            elapsedMicros += segmentTicks * tempo / ticksPerQuarterNote.toLong()
            elapsedTicks = change.tick
            tempo = change.microsecondsPerQuarterNote
        }
        elapsedMicros += (tick - elapsedTicks) * tempo / ticksPerQuarterNote.toLong()
        return elapsedMicros
    }
}
