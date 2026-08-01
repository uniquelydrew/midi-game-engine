package core.visualization

object KeyboardLayout {
    private val blackPitchClasses = setOf(1, 3, 6, 8, 10)

    fun allKeys(range: PitchRange, totalWidth: Float): List<KeyGeometry> {
        val whiteCount = (range.firstPitch..range.lastPitch).count { !isBlack(it) }
        val whiteWidth = totalWidth / whiteCount.coerceAtLeast(1)
        return (range.firstPitch..range.lastPitch).map { pitch ->
            val whiteIndex = (range.firstPitch until pitch).count { !isBlack(it) }
            if (isBlack(pitch)) {
                val blackWidth = whiteWidth * 0.62f
                KeyGeometry(
                    pitch = pitch,
                    left = (whiteIndex * whiteWidth - blackWidth / 2f)
                        .coerceIn(0f, (totalWidth - blackWidth).coerceAtLeast(0f)),
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
    }

    fun isBlack(pitch: Int): Boolean = pitch % 12 in blackPitchClasses

    fun keyGeometry(pitch: Int, range: PitchRange, totalWidth: Float): KeyGeometry {
        require(pitch in range.firstPitch..range.lastPitch) {
            "Pitch $pitch is outside visible range ${range.firstPitch}..${range.lastPitch}"
        }
        return allKeys(range, totalWidth).first { it.pitch == pitch }
    }

    fun contains(pitch: Int, range: PitchRange): Boolean =
        pitch in range.firstPitch..range.lastPitch
}
