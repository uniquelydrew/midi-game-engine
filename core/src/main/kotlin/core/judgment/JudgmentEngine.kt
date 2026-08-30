package core.judgment

import core.chart.ExpectedInput
import core.midi.MidiEvent
import core.midi.NoteOff
import core.midi.NoteOn
import kotlin.math.abs

class JudgmentEngine(
    private val timing: TimingWindow
) {

    private data class NoteState(
        val note: ExpectedInput
    ) {
        var noteOnTimeUs: Long? = null
        var noteOffTimeUs: Long? = null
        var resolved: Boolean = false
    }

    private val notes = mutableListOf<NoteState>()
    private val results = mutableListOf<JudgmentResult>()
    private var totalNotes: Int = 0

    fun load(chartEvents: List<ExpectedInput>) {
        notes.clear()
        results.clear()
        totalNotes = chartEvents.size
        chartEvents.forEachIndexed { index, event ->
            val id = if (event.id >= 0) event.id else index
            notes += NoteState(
                event.copy(id = id, matched = false, judgment = null, missReason = null, missDetail = null)
            )
        }
    }

    fun advanceTo(timeUs: Long): List<JudgmentResult> {
        val finalized = mutableListOf<JudgmentResult>()
        notes.forEach { state ->
            if (state.resolved) return@forEach
            val note = state.note
            when {
                state.noteOnTimeUs == null && timeUs >= note.targetTimeUs + timing.goodUs -> {
                    finalized += resolveMiss(
                        state = state,
                        reason = MissReason.NoInput,
                        detail = null,
                    )
                }

                note.durationUs > 0L &&
                    state.noteOnTimeUs != null &&
                    state.noteOffTimeUs == null &&
                    timeUs >= note.endTimeUs + timing.goodUs -> {
                    val startDelta = state.noteOnTimeUs!! - note.targetTimeUs
                    finalized += resolveMiss(
                        state = state,
                        reason = MissReason.TimingRelease,
                        detail = timingMissDetail(
                            startDeltaUs = startDelta,
                            endDeltaUs = timeUs - note.endTimeUs
                        )
                    )
                }
            }
        }
        return finalized
    }

    fun onInput(event: MidiEvent): InputFeedback? {
        return when (event) {
            is NoteOn -> {
                val feedback = onNoteOn(event.pitch, event.timestampUs)
                advanceTo(event.timestampUs)
                feedback
            }
            is NoteOff -> {
                val feedback = onNoteOff(event.pitch, event.timestampUs)
                advanceTo(event.timestampUs)
                feedback
            }
            else -> null
        }
    }

    fun results(): List<JudgmentResult> = results.toList()

    fun scoreSummary(): ScoreSummary {
        val perfectCount = results.count { it.judgment == Judgment.Perfect }
        val goodCount = results.count { it.judgment == Judgment.Good }
        val missCount = results.count { it.judgment == Judgment.Miss }
        val noInputCount = results.count { it.missReason == MissReason.NoInput }
        val wrongKeyCount = results.count { it.missReason == MissReason.WrongKey }
        val timingReleaseCount = results.count { it.missReason == MissReason.TimingRelease }
        val judgedNotes = results.size
        val weightedScore = if (totalNotes <= 0) {
            0.0
        } else {
            ((perfectCount + (goodCount * 0.5)) / totalNotes.toDouble()) * 100.0
        }

        return ScoreSummary(
            totalNotes = totalNotes,
            judgedNotes = judgedNotes,
            perfectCount = perfectCount,
            goodCount = goodCount,
            missCount = missCount,
            scorePercent = weightedScore.coerceIn(0.0, 100.0).toInt(),
            noInputCount = noInputCount,
            wrongKeyCount = wrongKeyCount,
            timingReleaseCount = timingReleaseCount
        )
    }

    private fun onNoteOn(pitch: Int, timeUs: Long): InputFeedback? {
        val samePitch = notes
            .filter { !it.resolved && it.note.pitch == pitch && it.noteOnTimeUs == null }
            .minByOrNull { abs(it.note.targetTimeUs - timeUs) }

        if (samePitch != null) {
            samePitch.noteOnTimeUs = timeUs
            val startDelta = timeUs - samePitch.note.targetTimeUs
            val tier = classify(startDelta)

            if (samePitch.note.durationUs <= 0L) {
                val final = when (tier) {
                    Judgment.Perfect -> resolveHit(samePitch, Judgment.Perfect)
                    Judgment.Good -> resolveHit(samePitch, Judgment.Good)
                    Judgment.Miss -> resolveMiss(
                        state = samePitch,
                        reason = MissReason.TimingRelease,
                        detail = timingMissDetail(startDeltaUs = startDelta, endDeltaUs = null),
                    )
                }
                return final.toFeedback()
            }

            return when (tier) {
                Judgment.Perfect -> InputFeedback(
                    noteId = samePitch.note.id,
                    pitch = pitch,
                    judgment = Judgment.Perfect,
                    message = "Perfect start"
                )
                Judgment.Good -> InputFeedback(
                    noteId = samePitch.note.id,
                    pitch = pitch,
                    judgment = Judgment.Good,
                    message = "Good start"
                )
                Judgment.Miss -> InputFeedback(
                    noteId = samePitch.note.id,
                    pitch = pitch,
                    judgment = Judgment.Miss,
                    missReason = MissReason.TimingRelease,
                    missDetail = timingMissDetail(startDeltaUs = startDelta, endDeltaUs = null),
                    message = if (startDelta < 0L) "Started early" else "Started late"
                )
            }
        }

        val wrongKeyTarget = notes
            .filter { !it.resolved && it.noteOnTimeUs == null }
            .filter { abs(it.note.targetTimeUs - timeUs) <= timing.goodUs }
            .minByOrNull { abs(it.note.targetTimeUs - timeUs) }

        return if (wrongKeyTarget != null) {
            resolveMiss(
                state = wrongKeyTarget,
                reason = MissReason.WrongKey,
                detail = null,
            ).toFeedback()
        } else {
            null
        }
    }

    private fun onNoteOff(pitch: Int, timeUs: Long): InputFeedback? {
        val active = notes
            .filter { !it.resolved && it.note.pitch == pitch && it.noteOnTimeUs != null && it.noteOffTimeUs == null }
            .minByOrNull { abs(it.noteOnTimeUs!! - it.note.targetTimeUs) }
            ?: return null

        active.noteOffTimeUs = timeUs
        val startDelta = active.noteOnTimeUs!! - active.note.targetTimeUs
        val endDelta = timeUs - active.note.endTimeUs
        val startTier = classify(startDelta)
        val endTier = classify(endDelta)

        val result = when {
            active.note.durationUs <= 0L -> when (startTier) {
                Judgment.Perfect -> resolveHit(active, Judgment.Perfect)
                Judgment.Good -> resolveHit(active, Judgment.Good)
                Judgment.Miss -> resolveMiss(
                    state = active,
                    reason = MissReason.TimingRelease,
                    detail = timingMissDetail(startDeltaUs = startDelta, endDeltaUs = null),
                )
            }

            startTier == Judgment.Perfect && endTier == Judgment.Perfect -> {
                resolveHit(active, Judgment.Perfect)
            }

            startTier != Judgment.Miss || endTier != Judgment.Miss -> {
                resolveHit(active, Judgment.Good)
            }

            else -> resolveMiss(
                state = active,
                reason = MissReason.TimingRelease,
                detail = timingMissDetail(startDeltaUs = startDelta, endDeltaUs = endDelta),
            )
        }

        return result.toFeedback()
    }

    private fun resolveHit(
        state: NoteState,
        judgment: Judgment
    ): JudgmentResult {
        val note = state.note
        state.resolved = true
        note.matched = true
        note.judgment = judgment
        note.missReason = null
        note.missDetail = null

        val result = JudgmentResult(
            noteId = note.id,
            pitch = note.pitch,
            expectedTimeUs = note.targetTimeUs,
            expectedEndTimeUs = note.endTimeUs,
            actualStartTimeUs = state.noteOnTimeUs,
            actualEndTimeUs = state.noteOffTimeUs,
            startDeltaUs = state.noteOnTimeUs?.minus(note.targetTimeUs),
            endDeltaUs = state.noteOffTimeUs?.minus(note.endTimeUs),
            judgment = judgment
        )
        results += result
        return result
    }

    private fun resolveMiss(
        state: NoteState,
        reason: MissReason,
        detail: TimingMissDetail?
    ): JudgmentResult {
        val note = state.note
        state.resolved = true
        note.matched = true
        note.judgment = Judgment.Miss
        note.missReason = reason
        note.missDetail = detail

        val result = JudgmentResult(
            noteId = note.id,
            pitch = note.pitch,
            expectedTimeUs = note.targetTimeUs,
            expectedEndTimeUs = note.endTimeUs,
            actualStartTimeUs = state.noteOnTimeUs,
            actualEndTimeUs = state.noteOffTimeUs,
            startDeltaUs = state.noteOnTimeUs?.minus(note.targetTimeUs),
            endDeltaUs = state.noteOffTimeUs?.minus(note.endTimeUs),
            judgment = Judgment.Miss,
            missReason = reason,
            missDetail = detail
        )
        results += result
        return result
    }

    private fun JudgmentResult.toFeedback(): InputFeedback {
        return InputFeedback(
            noteId = noteId,
            pitch = pitch,
            judgment = judgment,
            missReason = missReason,
            missDetail = missDetail,
            message = when {
                judgment == Judgment.Perfect -> "Perfect"
                judgment == Judgment.Good -> "Good"
                missReason == MissReason.WrongKey -> "Wrong key"
                missReason == MissReason.NoInput -> "No input"
                missReason == MissReason.TimingRelease -> "Timing miss"
                else -> "Miss"
            }
        )
    }

    private fun classify(deltaUs: Long): Judgment {
        val absDelta = abs(deltaUs)
        return when {
            absDelta <= timing.perfectUs -> Judgment.Perfect
            absDelta <= timing.goodUs -> Judgment.Good
            else -> Judgment.Miss
        }
    }

    private fun timingMissDetail(startDeltaUs: Long?, endDeltaUs: Long?): TimingMissDetail? {
        val start = when {
            startDeltaUs == null -> null
            startDeltaUs < -timing.goodUs -> "early"
            startDeltaUs > timing.goodUs -> "late"
            else -> "on"
        }
        val end = when {
            endDeltaUs == null -> null
            endDeltaUs < -timing.goodUs -> "early"
            endDeltaUs > timing.goodUs -> "late"
            else -> "on"
        }

        return when {
            start == "early" && end == null -> TimingMissDetail.EarlyStartOnly
            start == "late" && end == null -> TimingMissDetail.LateStartOnly
            start == "early" && end == "early" -> TimingMissDetail.EarlyStartEarlyRelease
            start == "early" && end == "late" -> TimingMissDetail.EarlyStartLateRelease
            start == "late" && end == "early" -> TimingMissDetail.LateStartEarlyRelease
            start == "late" && end == "late" -> TimingMissDetail.LateStartLateRelease
            start == null && end == "early" -> TimingMissDetail.EarlyReleaseOnly
            start == null && end == "late" -> TimingMissDetail.LateReleaseOnly
            start == "on" && end == "early" -> TimingMissDetail.EarlyReleaseOnly
            start == "on" && end == "late" -> TimingMissDetail.LateReleaseOnly
            start == "early" && end == "on" -> TimingMissDetail.EarlyStartOnly
            start == "late" && end == "on" -> TimingMissDetail.LateStartOnly
            else -> null
        }
    }
}
