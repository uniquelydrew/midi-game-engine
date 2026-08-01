package core.visualization

enum class KeyboardZoom(val label: String, val scale: Float) {
    COMPACT("Compact", 0.75f),
    STANDARD("Standard", 1.0f),
    LARGE("Large", 1.35f)
}
