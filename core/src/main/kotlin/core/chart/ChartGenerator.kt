package core.chart

import core.model.SongTrack

object ChartGenerator {

    fun fromTrack(track: SongTrack): PlayableChart {
        val events = track.notes.map {
            ExpectedInput(
                pitch = it.pitch,
                targetTimeUs = it.startTick * 1000
            )
        }
        return PlayableChart(events)
    }
}
