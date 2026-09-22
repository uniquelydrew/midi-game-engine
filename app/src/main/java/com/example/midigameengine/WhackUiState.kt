package com.example.midigameengine

import core.drums.DrumTarget
import core.strike.StrikeOutcome

data class WhackUiState(
    val deviceStatus: String,
    val profileName: String?,
    val mappedTargets: Set<DrumTarget>,
    val target: DrumTarget?,
    val targetActive: Boolean,
    val targetRemainingMs: Long?,
    val hitCount: Int,
    val missCount: Int,
    val wrongStrikeCount: Int,
    val averageReactionTimeMs: Long?,
    val bestReactionTimeMs: Long?,
    val lastOutcome: StrikeOutcome?,
    val lastStrikeTarget: DrumTarget?,
    val lastVelocity: Int?,
    val lastReactionTimeMs: Long?,
    val learningTarget: DrumTarget?,
    val headline: String,
    val isPlaying: Boolean
) {
    companion object {
        fun empty(): WhackUiState {
            return WhackUiState(
                deviceStatus = "Waiting for a MIDI input device",
                profileName = null,
                mappedTargets = emptySet(),
                target = null,
                targetActive = false,
                targetRemainingMs = null,
                hitCount = 0,
                missCount = 0,
                wrongStrikeCount = 0,
                averageReactionTimeMs = null,
                bestReactionTimeMs = null,
                lastOutcome = null,
                lastStrikeTarget = null,
                lastVelocity = null,
                lastReactionTimeMs = null,
                learningTarget = null,
                headline = "Configure a drum kit to begin",
                isPlaying = false
            )
        }
    }
}
