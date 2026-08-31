package core.judgment

import core.chart.ExpectedInput
import kotlin.math.abs

/**
 * Defines how physical input is associated with chart notes.
 *
 * A same-pitch note-on acquires the closest pending chart note, even outside the
 * timing window. This lets held notes retain both start and release timing for a
 * single final judgment. A different pitch inside the good window consumes the
 * closest pending note as a wrong-key miss.
 */
class NoteAcquisitionPolicy(private val timing: TimingWindow) {
    fun matchingNote(pending: List<ExpectedInput>, pitch: Int, timeUs: Long): ExpectedInput? =
        pending.filter { it.pitch == pitch }.minByOrNull { abs(it.targetTimeUs - timeUs) }

    fun wrongKeyTarget(pending: List<ExpectedInput>, timeUs: Long): ExpectedInput? =
        pending
            .filter { abs(it.targetTimeUs - timeUs) <= timing.goodUs }
            .minByOrNull { abs(it.targetTimeUs - timeUs) }
}

/** The endpoint policy for a note that has already been acquired. */
class HeldNoteJudgmentPolicy(private val timing: TimingWindow) {
    fun classify(deltaUs: Long): Judgment = when (abs(deltaUs)) {
        in 0..timing.perfectUs -> Judgment.Perfect
        in (timing.perfectUs + 1)..timing.goodUs -> Judgment.Good
        else -> Judgment.Miss
    }

    fun finalJudgment(startDeltaUs: Long, endDeltaUs: Long): Judgment {
        val start = classify(startDeltaUs)
        val end = classify(endDeltaUs)
        return when {
            start == Judgment.Perfect && end == Judgment.Perfect -> Judgment.Perfect
            start != Judgment.Miss || end != Judgment.Miss -> Judgment.Good
            else -> Judgment.Miss
        }
    }
}
