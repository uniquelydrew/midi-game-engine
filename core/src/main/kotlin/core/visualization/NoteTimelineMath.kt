package core.visualization

data class NoteTimelineRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

object NoteTimelineMath {

    fun projectNote(
        pitch: Int,
        startTimeUs: Long,
        durationUs: Long,
        currentTimeUs: Long,
        keyboardTopPx: Float,
        pixelsPerSecond: Float,
        totalWidthPx: Float,
        visibleRange: PitchRange = PitchRange(21, 108)
    ): Pair<KeyGeometry, NoteTimelineRect> {
        val key = KeyboardLayout.keyGeometry(pitch, visibleRange, totalWidthPx)
        val deltaStartSeconds = (startTimeUs - currentTimeUs) / 1_000_000f
        val deltaDurationSeconds = durationUs / 1_000_000f

        val bottom = keyboardTopPx - (deltaStartSeconds * pixelsPerSecond)
        val top = bottom - (deltaDurationSeconds * pixelsPerSecond)

        return key to NoteTimelineRect(
            left = key.left,
            top = top,
            right = key.left + key.width,
            bottom = bottom
        )
    }
}
