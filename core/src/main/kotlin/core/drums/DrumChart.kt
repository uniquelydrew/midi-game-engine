package core.drums

import core.chart.PlayableChart
import core.input.DrumPadInputProfile

data class DrumChartEvent(val noteId: Int, val targetId: String, val sourcePitch: Int, val targetTimeUs: Long, val expectedVelocity: Int)
data class DrumChartProjection(val events: List<DrumChartEvent>, val unmappedEventCount: Int)
data class DrumHitGroup(val timeUs: Long, val events: List<DrumChartEvent>)

class DrumChartProjector(private val profile: DrumPadInputProfile) {
    fun project(chart: PlayableChart): DrumChartProjection {
        val mapped = chart.events.mapNotNull { note ->
            profile.resolveInput(note.pitch, note.channel)?.let { target ->
                DrumChartEvent(note.id, target.id, note.pitch, note.targetTimeUs, note.velocity)
            }
        }.sortedBy { it.targetTimeUs }
        return DrumChartProjection(mapped, chart.events.size - mapped.size)
    }

    fun hitGroups(chart: PlayableChart, thresholdUs: Long = 10_000L): List<DrumHitGroup> {
        return project(chart).events.fold(mutableListOf()) { groups, event ->
            val prior = groups.lastOrNull()
            if (prior != null && event.targetTimeUs - prior.timeUs <= thresholdUs) {
                groups[groups.lastIndex] = prior.copy(events = prior.events + event)
            } else groups += DrumHitGroup(event.targetTimeUs, listOf(event))
            groups
        }
    }
}
