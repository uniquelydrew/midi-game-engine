package com.example.midigameengine

import core.drums.DrumSequenceFeedback
import core.drums.DrumSequenceScoreSummary
import core.drums.DrumTarget

data class DrumSequenceUiState(
    val sourceLabel: String,
    val deviceStatus: String,
    val mappedTargets: Set<DrumTarget>,
    val unclassifiedSourcePitches: Set<Int>,
    val playbackTimeUs: Long,
    val playbackEndUs: Long,
    val isPlaying: Boolean,
    val isCountingIn: Boolean,
    val countInEnabled: Boolean,
    val countInBeat: Int?,
    val loopStartUs: Long?,
    val loopEndUs: Long?,
    val loopRuleLabel: String,
    val completedPasses: Int,
    val practiceElapsedMs: Long,
    val score: DrumSequenceScoreSummary,
    val lastFeedback: DrumSequenceFeedback?
) {
    companion object {
        fun empty() = DrumSequenceUiState(
            sourceLabel = "No drum sequence loaded",
            deviceStatus = "MIDI: Waiting for drum kit",
            mappedTargets = emptySet(),
            unclassifiedSourcePitches = emptySet(),
            playbackTimeUs = 0L,
            playbackEndUs = 0L,
            isPlaying = false,
            isCountingIn = false,
            countInEnabled = true,
            countInBeat = null,
            loopStartUs = null,
            loopEndUs = null,
            loopRuleLabel = "Forever",
            completedPasses = 0,
            practiceElapsedMs = 0L,
            score = DrumSequenceScoreSummary(0, 0, 0, 0, 0, 0, null),
            lastFeedback = null
        )
    }
}
