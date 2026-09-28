package com.example.midigameengine

import android.content.Context
import android.midi.AndroidMidiInputReal
import core.drums.DrumKitProfile
import core.drums.DrumKitProfiles
import core.drums.DrumTarget
import core.drums.DrumTrigger
import core.midi.NoteOn
import core.time.SystemClock
import core.time.Transport

data class DrumKitConfigState(
    val deviceKey: String?, val deviceStatus: String, val library: DrumKitProfileLibrary,
    val learningTarget: DrumTarget? = null
) { val active: DrumKitProfile get() = library.profiles.first { it.name == library.activeName } }

private val DrumKitProfileLibrary.active: DrumKitProfile
    get() = profiles.first { it.name == activeName }

class DrumKitConfigController(private val context: Context, private val onChanged: (DrumKitConfigState) -> Unit) {
    private val preferences = AppPreferencesStore(context)
    private val transport = Transport(SystemClock())
    private val midiInput = AndroidMidiInputReal(context, transport)
    private var running = false
    private var deviceKey: String? = null
    private var deviceStatus = "MIDI: Waiting for drum kit"
    private var library = preferences.drumKitProfiles(null)
    private var learning: DrumTarget? = null

    init {
        midiInput.setDeviceInfoListener { key -> deviceKey = key; library = preferences.drumKitProfiles(key); emit() }
        midiInput.setStatusListener { status -> deviceStatus = "MIDI: $status"; emit() }
        midiInput.setListener { event -> if (event is NoteOn && learning != null) {
            val target = learning!!
            updateActive { profile -> profile.copy(triggers = profile.triggers.filterNot {
                it.target == target || (it.midiNote == event.pitch && it.channel == event.channel)
            } + DrumTrigger(target, event.pitch, event.channel)) }
            learning = null; emit()
        } }
    }
    fun start() { if (!running) { running = true; midiInput.start(); emit() } }
    fun stop() { if (running) { running = false; midiInput.stop() } }
    fun state(): DrumKitConfigState = DrumKitConfigState(deviceKey, deviceStatus, library, learning)
    fun select(name: String) { preferences.setActiveDrumKitProfile(deviceKey, name); library = preferences.drumKitProfiles(deviceKey); emit() }
    fun create(name: String) { addProfile(name, library.active.copy(name = name, triggers = emptyList())) }
    fun duplicate(name: String) { addProfile(name, library.active.copy(name = name)) }
    fun rename(name: String) { val old = library.active; val profiles = library.profiles.filterNot { it.name == old.name } + old.copy(name = name); save(DrumKitProfileLibrary(name, profiles)) }
    fun deleteActive() { if (library.profiles.size <= 1) return; val remaining = library.profiles.filterNot { it.name == library.activeName }; save(DrumKitProfileLibrary(remaining.first().name, remaining)) }
    fun useGeneralMidi() { updateActive { DrumKitProfiles.generalMidi(it.name).copy(layout = it.layout) } }
    fun setVisible(target: DrumTarget, visible: Boolean) = updateActive { p -> p.copy(layout = p.layout.map { if (it.target == target) it.copy(visible = visible) else it }) }
    fun move(target: DrumTarget, x: Float, y: Float) = updateActive { p -> p.copy(layout = p.layout.map { if (it.target == target) it.copy(xFraction = x.coerceIn(0f, 1f), yFraction = y.coerceIn(0f, 1f)) else it }) }
    fun learn(target: DrumTarget) { learning = target; emit() }
    fun cancelLearn() { learning = null; emit() }
    private fun addProfile(name: String, profile: DrumKitProfile) { if (name.isBlank() || library.profiles.any { it.name == name }) return; save(DrumKitProfileLibrary(name, library.profiles + profile)) }
    private fun updateActive(transform: (DrumKitProfile) -> DrumKitProfile) { val updated = transform(library.active); save(DrumKitProfileLibrary(updated.name, library.profiles.filterNot { it.name == library.activeName } + updated)) }
    private fun save(value: DrumKitProfileLibrary) { preferences.saveDrumKitProfiles(deviceKey, value); library = preferences.drumKitProfiles(deviceKey); emit() }
    private fun emit() = onChanged(state())
    fun release() { stop() }
}
