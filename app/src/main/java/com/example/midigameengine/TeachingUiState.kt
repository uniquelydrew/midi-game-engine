package com.example.midigameengine

data class TeachingNoteState(
    val pitch: Int,
    val startTimeUs: Long,
    val durationUs: Long,
    val matched: Boolean
)

data class TeachingUiState(
    val sourceLabel: String,
    val deviceStatus: String,
    val headline: String,
    val playbackTimeUs: Long,
    val chartLengthUs: Long,
    val combo: Int,
    val maxCombo: Int,
    val judgmentCount: Int,
    val progress: Float,
    val notes: List<TeachingNoteState>,
    val nextExpectedNotes: List<TeachingNoteState>,
    val activeNotes: List<TeachingNoteState>,
    val physicalHeldPitches: Set<Int>,
    val lastInputPitch: Int?,
    val lastInputCorrect: Boolean?,
    val inputFeedbackUntilUs: Long,
    val trackSummary: String,
    val physicalProfileLabel: String,
    val visibleRangeFirstPitch: Int,
    val visibleRangeLastPitch: Int,
    val layoutModeLabel: String,
    val libraryCount: Int,
    val playbackStartUs: Long,
    val playbackEndUs: Long,
    val speed: Double,
    val autoTrimEnabled: Boolean,
    val trimPaddingMs: Int,
    val keyboardZoomLabel: String,
    val isPlaying: Boolean,
    val isScrubbing: Boolean
) {
    companion object {
        fun empty(): TeachingUiState {
            return TeachingUiState(
                sourceLabel = "Demo",
                deviceStatus = "Waiting for MIDI device",
                headline = "Loading...",
                playbackTimeUs = 0L,
                chartLengthUs = 0L,
                combo = 0,
                maxCombo = 0,
                judgmentCount = 0,
                progress = 0f,
                notes = emptyList(),
                nextExpectedNotes = emptyList(),
                activeNotes = emptyList(),
                physicalHeldPitches = emptySet(),
                lastInputPitch = null,
                lastInputCorrect = null,
                inputFeedbackUntilUs = 0L,
                trackSummary = "No MIDI loaded",
                physicalProfileLabel = "Auto 88-key",
                visibleRangeFirstPitch = 21,
                visibleRangeLastPitch = 108,
                layoutModeLabel = "AUTO",
                libraryCount = 0,
                playbackStartUs = 0L,
                playbackEndUs = 0L,
                speed = 1.0,
                autoTrimEnabled = true,
                trimPaddingMs = 50,
                keyboardZoomLabel = "Standard",
                isPlaying = false,
                isScrubbing = false
            )
        }
    }
}
