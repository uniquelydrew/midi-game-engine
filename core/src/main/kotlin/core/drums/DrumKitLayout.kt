package core.drums

/** Normalized visual placement for a single logical piece in a kit profile. */
data class DrumKitLayoutPiece(
    val target: DrumTarget,
    val visible: Boolean = true,
    val xFraction: Float,
    val yFraction: Float
) {
    init {
        require(xFraction in 0f..1f) { "Layout x must be normalized" }
        require(yFraction in 0f..1f) { "Layout y must be normalized" }
    }
}

object DrumKitLayouts {
    fun standard(): List<DrumKitLayoutPiece> = listOf(
        DrumKitLayoutPiece(DrumTarget.CRASH, xFraction = .18f, yFraction = .14f),
        DrumKitLayoutPiece(DrumTarget.RIDE, xFraction = .82f, yFraction = .14f),
        DrumKitLayoutPiece(DrumTarget.TOM_1, xFraction = .40f, yFraction = .34f),
        DrumKitLayoutPiece(DrumTarget.TOM_2, xFraction = .60f, yFraction = .34f),
        DrumKitLayoutPiece(DrumTarget.HI_HAT, xFraction = .16f, yFraction = .56f),
        DrumKitLayoutPiece(DrumTarget.SNARE, xFraction = .38f, yFraction = .60f),
        DrumKitLayoutPiece(DrumTarget.FLOOR_TOM, xFraction = .74f, yFraction = .60f),
        DrumKitLayoutPiece(DrumTarget.KICK, xFraction = .52f, yFraction = .82f)
    )

    fun normalized(pieces: List<DrumKitLayoutPiece>): List<DrumKitLayoutPiece> {
        val byTarget = pieces.associateBy { it.target }
        return standard().map { default -> byTarget[default.target] ?: default.copy(visible = false) }
    }
}
