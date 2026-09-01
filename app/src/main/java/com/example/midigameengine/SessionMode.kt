package com.example.midigameengine

enum class ExperienceMode(val label: String) { PRACTICE("Practice"), GAME("Game") }
enum class InstrumentMode(val label: String) { KEYBOARD("Keyboard"), DRUMS("Drums") }

data class DrumPadMapping(val padIndex: Int, val midiPitch: Int, val logicalTargetId: String?)
data class DrumPadLayout(val id: String, val name: String, val pads: List<DrumPadMapping>)
