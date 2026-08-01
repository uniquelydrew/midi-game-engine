package core.visualization

enum class KeyboardProfile(
    val keyCount: Int,
    val firstPitch: Int,
    val lastPitch: Int
) {
    KEYS_25(25, 48, 72),
    KEYS_49(49, 36, 84),
    KEYS_61(61, 36, 96),
    KEYS_76(76, 28, 103),
    KEYS_88(88, 21, 108);

    val label: String
        get() = "$keyCount-key"
}

enum class KeyboardProfileMode {
    AUTO,
    MANUAL
}

data class PitchRange(
    val firstPitch: Int,
    val lastPitch: Int
) {
    init {
        require(firstPitch in 0..127)
        require(lastPitch in firstPitch..127)
    }
}
