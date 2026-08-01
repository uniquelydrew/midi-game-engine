package core.visualization

data class KeyGeometry(
    val pitch: Int,
    val left: Float,
    val width: Float,
    val black: Boolean
)

object Keyboard88Layout {

    const val firstPitch = 21
    const val lastPitch = 108
    private const val whiteKeyCount = 52
    private val blackPitchClasses = setOf(1, 3, 6, 8, 10)

    fun isBlackPitch(pitch: Int): Boolean {
        require(pitch in firstPitch..lastPitch)
        return pitch % 12 in blackPitchClasses
    }

    fun whiteKeyIndexBefore(pitch: Int): Int {
        require(pitch in firstPitch..lastPitch)
        var whites = 0
        for (note in firstPitch until pitch) {
            if (!isBlackPitchInternal(note)) {
                whites++
            }
        }
        return whites
    }

    fun keyGeometry(
        pitch: Int,
        totalWidth: Float,
        blackKeyWidthRatio: Float = 0.62f
    ): KeyGeometry {
        require(pitch in firstPitch..lastPitch)
        val whiteWidth = totalWidth / whiteKeyCount
        val whiteIndex = whiteKeyIndexBefore(pitch)

        return if (isBlackPitchInternal(pitch)) {
            val blackWidth = whiteWidth * blackKeyWidthRatio
            KeyGeometry(
                pitch = pitch,
                left = whiteIndex * whiteWidth - blackWidth / 2f,
                width = blackWidth,
                black = true
            )
        } else {
            KeyGeometry(
                pitch = pitch,
                left = whiteIndex * whiteWidth,
                width = whiteWidth,
                black = false
            )
        }
    }

    fun allKeys(totalWidth: Float): List<KeyGeometry> {
        return (firstPitch..lastPitch).map { keyGeometry(it, totalWidth) }
    }

    private fun isBlackPitchInternal(pitch: Int): Boolean {
        return pitch % 12 in blackPitchClasses
    }
}
