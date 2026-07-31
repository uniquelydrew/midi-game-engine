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
            perfect = 120_000,
            great = 220_000,
            good = 350_000
        ),
        allowMisses = true,
        maxChordSize = 2,
        requireReleaseAccuracy = false
    )

    val Intermediate = DifficultyProfile(
        name = "Intermediate",
        timing = TimingWindow(
            perfect = 70_000,
            great = 140_000,
            good = 220_000
        ),
        allowMisses = true,
        maxChordSize = 4,
        requireReleaseAccuracy = false
    )

    val Advanced = DifficultyProfile(
        name = "Advanced",
        timing = TimingWindow(
            perfect = 40_000,
            great = 90_000,
            good = 150_000
        ),
        allowMisses = false,
        maxChordSize = Int.MAX_VALUE,
        requireReleaseAccuracy = true
    )
}
