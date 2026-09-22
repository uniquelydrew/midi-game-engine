package core.runtime

enum class WhackDifficulty(
    val label: String,
    val targetDurationUs: Long,
    val interTargetDelayUs: Long
) {
    RELAXED(
        label = "Relaxed",
        targetDurationUs = 2_000_000L,
        interTargetDelayUs = 350_000L
    ),
    STANDARD(
        label = "Standard",
        targetDurationUs = 1_500_000L,
        interTargetDelayUs = 250_000L
    ),
    FAST(
        label = "Fast",
        targetDurationUs = 1_000_000L,
        interTargetDelayUs = 150_000L
    ),
    EXPERT(
        label = "Expert",
        targetDurationUs = 650_000L,
        interTargetDelayUs = 90_000L
    );

    fun config(): WhackGameConfig {
        return WhackGameConfig(
            targetDurationUs = targetDurationUs,
            interTargetDelayUs = interTargetDelayUs
        )
    }
}
