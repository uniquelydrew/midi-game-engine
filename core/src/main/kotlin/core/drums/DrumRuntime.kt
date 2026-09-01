package core.drums

import core.input.DrumPadInputProfile
import core.midi.MidiEvent
import core.midi.NoteOn
import kotlin.math.abs

enum class DrumJudgment { Perfect, Great, Good, Miss }
enum class DrumMissReason { NoInput, WrongTarget }
enum class VelocityGrade { Matched, Acceptable, TooSoft, TooHard }

data class DrumTimingWindow(val perfectUs: Long = 40_000, val greatUs: Long = 75_000, val goodUs: Long = 110_000)
data class DrumJudgmentSettings(val evaluateTiming: Boolean = true, val evaluateVelocity: Boolean = true, val velocityTolerance: Int = 24)
data class VelocityAssessment(val expectedVelocity: Int, val actualVelocity: Int, val delta: Int, val grade: VelocityGrade)
data class DrumJudgmentResult(val noteId: Int, val targetId: String, val sourcePitch: Int, val expectedTimeUs: Long, val actualTimeUs: Long?, val judgment: DrumJudgment, val missReason: DrumMissReason? = null, val velocity: VelocityAssessment? = null) {
    val timingOffsetUs: Long? get() = actualTimeUs?.minus(expectedTimeUs)
}
data class DrumTargetMetrics(val targetId: String, val hits: Int, val misses: Int, val averageOffsetUs: Long)
data class DrumPracticeMetrics(val timingAccuracyPercent: Double, val perfectHits: Int, val greatHits: Int, val goodHits: Int, val misses: Int, val wrongPadHits: Int, val averageTimingOffsetUs: Long, val earlyCount: Int, val lateCount: Int, val perTarget: List<DrumTargetMetrics>)
data class DrumInputFeedback(val targetId: String?, val judgment: DrumJudgment?, val message: String, val wrongTarget: Boolean = false, val velocity: VelocityAssessment? = null)

class DrumNoteAcquisitionPolicy(private val timing: DrumTimingWindow) {
    fun acquire(pending: List<DrumChartEvent>, targetId: String, inputTimeUs: Long): DrumChartEvent? =
        pending.firstOrNull { it.targetId == targetId && abs(it.targetTimeUs - inputTimeUs) <= timing.goodUs }
}

class DrumPracticeRuntime(
    private val projection: DrumChartProjection,
    private val profile: DrumPadInputProfile,
    private val timing: DrumTimingWindow = DrumTimingWindow(),
    private val settings: DrumJudgmentSettings = DrumJudgmentSettings()
) {
    private val pending = projection.events.toMutableList()
    private val finalized = mutableListOf<DrumJudgmentResult>()
    private val acquire = DrumNoteAcquisitionPolicy(timing)

    fun onInput(event: MidiEvent): DrumInputFeedback? {
        if (event !is NoteOn) return null
        val target = profile.resolveInput(event.pitch, event.channel)
            ?: return DrumInputFeedback(null, null, "Unmapped pad ${event.pitch}")
        val sameTarget = acquire.acquire(pending, target.id, event.timestampUs)
        if (sameTarget != null) return resolveHit(sameTarget, event)
        val wrong = pending.firstOrNull { abs(it.targetTimeUs - event.timestampUs) <= timing.goodUs }
        return if (wrong != null) {
            pending.remove(wrong)
            finalized += DrumJudgmentResult(wrong.noteId, wrong.targetId, wrong.sourcePitch, wrong.targetTimeUs, event.timestampUs, DrumJudgment.Miss, DrumMissReason.WrongTarget)
            DrumInputFeedback(target.id, DrumJudgment.Miss, "${target.shortLabel}: wrong pad", wrongTarget = true)
        } else DrumInputFeedback(target.id, null, "${target.shortLabel}: extra hit")
    }

    fun advanceTo(timeUs: Long): List<DrumJudgmentResult> {
        val missed = pending.takeWhile { timeUs > it.targetTimeUs + timing.goodUs }
        pending.removeAll(missed.toSet())
        val results = missed.map { DrumJudgmentResult(it.noteId, it.targetId, it.sourcePitch, it.targetTimeUs, null, DrumJudgment.Miss, DrumMissReason.NoInput) }
        finalized += results
        return results
    }

    fun results(): List<DrumJudgmentResult> = finalized.toList()
    fun pendingEvents(): List<DrumChartEvent> = pending.toList()
    fun metrics(): DrumPracticeMetrics {
        val hits = finalized.filter { it.judgment != DrumJudgment.Miss }
        val offsets = hits.mapNotNull { it.timingOffsetUs }
        val targets = projection.events.map { it.targetId }.distinct().map { id ->
            val values = finalized.filter { it.targetId == id }
            DrumTargetMetrics(id, values.count { it.judgment != DrumJudgment.Miss }, values.count { it.judgment == DrumJudgment.Miss }, values.mapNotNull { it.timingOffsetUs }.average().takeIf { !it.isNaN() }?.toLong() ?: 0)
        }
        val weighted = finalized.fold(0) { total, result -> total + when (result.judgment) { DrumJudgment.Perfect -> 100; DrumJudgment.Great -> 80; DrumJudgment.Good -> 50; DrumJudgment.Miss -> 0 } }
        return DrumPracticeMetrics(if (projection.events.isEmpty()) 0.0 else weighted * 100.0 / (projection.events.size * 100), finalized.count { it.judgment == DrumJudgment.Perfect }, finalized.count { it.judgment == DrumJudgment.Great }, finalized.count { it.judgment == DrumJudgment.Good }, finalized.count { it.judgment == DrumJudgment.Miss }, finalized.count { it.missReason == DrumMissReason.WrongTarget }, offsets.average().takeIf { !it.isNaN() }?.toLong() ?: 0, offsets.count { it < 0 }, offsets.count { it > 0 }, targets)
    }

    private fun resolveHit(expected: DrumChartEvent, input: NoteOn): DrumInputFeedback {
        pending.remove(expected)
        val delta = input.timestampUs - expected.targetTimeUs
        val judgment = when (abs(delta)) { in 0..timing.perfectUs -> DrumJudgment.Perfect; in (timing.perfectUs + 1)..timing.greatUs -> DrumJudgment.Great; else -> DrumJudgment.Good }
        val velocity = assessVelocity(expected.expectedVelocity, input.velocity)
        finalized += DrumJudgmentResult(expected.noteId, expected.targetId, expected.sourcePitch, expected.targetTimeUs, input.timestampUs, judgment, velocity = velocity)
        return DrumInputFeedback(expected.targetId, judgment, "${expected.targetId}: $judgment", velocity = velocity)
    }
    private fun assessVelocity(expected: Int, actual: Int): VelocityAssessment? {
        if (!settings.evaluateVelocity) return null
        val delta = actual - expected
        val grade = when { abs(delta) <= settings.velocityTolerance / 2 -> VelocityGrade.Matched; abs(delta) <= settings.velocityTolerance -> VelocityGrade.Acceptable; delta < 0 -> VelocityGrade.TooSoft; else -> VelocityGrade.TooHard }
        return VelocityAssessment(expected, actual, delta, grade)
    }
}
