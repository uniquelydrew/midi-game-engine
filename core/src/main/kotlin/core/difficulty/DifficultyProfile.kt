package core.difficulty

import core.judgment.TimingWindow

data class DifficultyProfile(
    val name: String,
    val timing: TimingWindow,
    val allowMisses: Boolean = true,
    val maxChordSize: Int = Int.MAX_VALUE,
    val requireReleaseAccuracy: Boolean = false
)

object DifficultyPresets {

    val Beginner = DifficultyProfile(
        name = "Beginner",
        timing = TimingWindow(
            perfectUs = 120_000,
            greatUs = 220_000,
            goodUs = 350_000
        ),
        allowMisses = true,
        maxChordSize = 2,
        requireReleaseAccuracy = false
    )

    val Intermediate = DifficultyProfile(
        name = "Intermediate",
        timing = TimingWindow(
            perfectUs = 70_000,
            greatUs = 140_000,
            goodUs = 220_000
        ),
        allowMisses = true,
        maxChordSize = 4,
        requireReleaseAccuracy = false
    )

    val Advanced = DifficultyProfile(
        name = "Advanced",
        timing = TimingWindow(
            perfectUs = 40_000,
            greatUs = 90_000,
            goodUs = 150_000
        ),
        allowMisses = false,
        maxChordSize = Int.MAX_VALUE,
        requireReleaseAccuracy = true
    )
}
