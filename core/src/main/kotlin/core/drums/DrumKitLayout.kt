package core.drums

/** Normalized visual placement for a single logical piece in a kit profile. */
data class DrumKitLayoutPiece(
    val target: DrumTarget,
    val visible: Boolean = true,
    val xFraction: Float,
    val yFraction: Float,
    /** User-controlled uniform scale. Rendering always applies this to both axes. */
    val scale: Float = 1f,
    /** Higher values draw over lower values when kit pieces overlap. */
    val zIndex: Int = 0
) {
    init {
        require(xFraction in 0f..1f) { "Layout x must be normalized" }
        require(yFraction in 0f..1f) { "Layout y must be normalized" }
        require(scale in MIN_SCALE..MAX_SCALE) { "Layout scale must be within supported bounds" }
        require(zIndex >= 0) { "Layout z-index cannot be negative" }
    }

    companion object {
        const val MIN_SCALE = .55f
        const val MAX_SCALE = 1.75f
    }
}

object DrumKitLayouts {
    /**
     * The complete visual default for a drum kit profile. Use this to reset a
     * customized layout; it intentionally includes visibility, scale, and layer order.
     */
    fun standard(): List<DrumKitLayoutPiece> = listOf(
        DrumKitLayoutPiece(DrumTarget.CRASH, xFraction = .18f, yFraction = .14f, zIndex = 1),
        DrumKitLayoutPiece(DrumTarget.RIDE, xFraction = .82f, yFraction = .14f, zIndex = 2),
        DrumKitLayoutPiece(DrumTarget.TOM_1, xFraction = .40f, yFraction = .34f, zIndex = 3),
        DrumKitLayoutPiece(DrumTarget.TOM_2, xFraction = .60f, yFraction = .34f, zIndex = 4),
        DrumKitLayoutPiece(DrumTarget.HI_HAT, xFraction = .16f, yFraction = .56f, zIndex = 5),
        DrumKitLayoutPiece(DrumTarget.SNARE, xFraction = .38f, yFraction = .60f, zIndex = 6),
        DrumKitLayoutPiece(DrumTarget.FLOOR_TOM, xFraction = .74f, yFraction = .60f, zIndex = 7),
        DrumKitLayoutPiece(DrumTarget.KICK, xFraction = .52f, yFraction = .82f, zIndex = 8)
    )

    fun normalized(pieces: List<DrumKitLayoutPiece>): List<DrumKitLayoutPiece> {
        val byTarget = pieces.associateBy { it.target }
        return standard().map { default -> byTarget[default.target] ?: default.copy(visible = false) }
    }
}
