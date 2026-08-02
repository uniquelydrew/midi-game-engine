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
        label = "Game",
        description = "Game rules are coming soon; this currently uses the teaching engine."
    )
}
