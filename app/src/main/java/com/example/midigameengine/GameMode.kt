package com.example.midigameengine

enum class GameMode(
    val label: String,
    val description: String
) {
    TEACHING(
        label = "Teaching",
        description = "Follow the cascade and practice the expected notes."
    ),
    GAME(
        label = "Whack-a-MIDI",
        description = "Strike the highlighted drum pad as quickly and accurately as possible."
    )
}
