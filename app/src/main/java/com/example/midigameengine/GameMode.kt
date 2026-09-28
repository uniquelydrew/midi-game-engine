package com.example.midigameengine

enum class GameMode(
    val label: String,
    val description: String
) {
    HOME(
        label = "Choose instrument",
        description = "Choose Piano Practice or Whack-a-MIDI before opening a play screen."
    ),
    TEACHING(
        label = "Piano Practice",
        description = "Follow the cascade and practice the expected notes."
    ),
    DRUMS_HUB(
        label = "Drums",
        description = "Choose Whack-a-MIDI or timed Drum Sequence Training."
    ),
    DRUM_KIT_CONFIG(
        label = "Configure Drum Kit",
        description = "Arrange kit pieces and map physical drum pads."
    ),
    DRUM_SEQUENCE(
        label = "Drum Sequence Training",
        description = "Practice imported drum MIDI sequences with timing feedback."
    ),
    GAME(
        label = "Whack-a-MIDI",
        description = "Strike the highlighted drum pad as quickly and accurately as possible."
    )
}
