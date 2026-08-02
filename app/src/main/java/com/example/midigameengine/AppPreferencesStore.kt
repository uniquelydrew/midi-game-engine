package com.example.midigameengine

import android.content.Context
import core.chart.PlaybackSettings
import core.visualization.KeyboardProfile
import core.visualization.KeyboardProfileMode
import core.visualization.KeyboardZoom
import core.visualization.PitchRange
import org.json.JSONArray
import org.json.JSONObject

data class LibraryEntry(
    val uri: String,
    val displayName: String,
    val selectedTrackIds: Set<String> = emptySet()
)

data class LayoutPreference(
    val mode: KeyboardProfileMode = KeyboardProfileMode.AUTO,
    val profile: KeyboardProfile = KeyboardProfile.KEYS_88,
    val visibleRange: PitchRange = PitchRange(21, 108)
)

class AppPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("midi-game-state", Context.MODE_PRIVATE)

    fun library(): List<LibraryEntry> {
        val array = runCatching { JSONArray(preferences.getString(KEY_LIBRARY, "[]")) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val selected = item.optJSONArray("tracks") ?: JSONArray()
                add(
                    LibraryEntry(
                        uri = item.optString("uri"),
                        displayName = item.optString("name", "MIDI file"),
                        selectedTrackIds = buildSet {
                            for (trackIndex in 0 until selected.length()) add(selected.optString(trackIndex))
                        }
                    )
                )
            }
        }
    }

    fun saveLibrary(entries: List<LibraryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("uri", entry.uri)
                    .put("name", entry.displayName)
                    .put("tracks", JSONArray(entry.selectedTrackIds.toList()))
            )
        }
        preferences.edit().putString(KEY_LIBRARY, array.toString()).apply()
    }

    fun lastUri(): String? = preferences.getString(KEY_LAST_URI, null)

    fun setLastUri(uri: String?) {
        preferences.edit().putString(KEY_LAST_URI, uri).apply()
    }

    fun lastSelectedTrackIds(): Set<String> {
        val array = runCatching {
            JSONArray(preferences.getString(KEY_LAST_TRACKS, "[]"))
        }.getOrDefault(JSONArray())
        return buildSet {
            for (index in 0 until array.length()) add(array.optString(index))
        }
    }

    fun setLastSelectedTrackIds(trackIds: Set<String>) {
        preferences.edit()
            .putString(KEY_LAST_TRACKS, JSONArray(trackIds.toList()).toString())
            .apply()
    }

    fun lastPickerUri(): String? = preferences.getString(KEY_LAST_PICKER_URI, null)

    fun setLastPickerUri(uri: String) {
        preferences.edit().putString(KEY_LAST_PICKER_URI, uri).apply()
    }

    fun layoutPreference(deviceKey: String?): LayoutPreference {
        val prefix = deviceKey?.let { "layout.$it." } ?: "layout.default."
        val mode = runCatching {
            KeyboardProfileMode.valueOf(preferences.getString(prefix + "mode", KeyboardProfileMode.AUTO.name)!!)
        }.getOrDefault(KeyboardProfileMode.AUTO)
        val profile = runCatching {
            KeyboardProfile.valueOf(preferences.getString(prefix + "profile", KeyboardProfile.KEYS_88.name)!!)
        }.getOrDefault(KeyboardProfile.KEYS_88)
        val first = preferences.getInt(prefix + "first", 21)
        val last = preferences.getInt(prefix + "last", 108)
        return LayoutPreference(mode, profile, PitchRange(first, last))
    }

    fun saveLayoutPreference(deviceKey: String?, preference: LayoutPreference) {
        val prefix = deviceKey?.let { "layout.$it." } ?: "layout.default."
        preferences.edit()
            .putString(prefix + "mode", preference.mode.name)
            .putString(prefix + "profile", preference.profile.name)
            .putInt(prefix + "first", preference.visibleRange.firstPitch)
            .putInt(prefix + "last", preference.visibleRange.lastPitch)
            .apply()
    }

    fun playbackSettings(): PlaybackSettings {
        return PlaybackSettings(
            speed = preferences.getFloat(KEY_SPEED, 1.0f).toDouble(),
            autoTrimEnabled = preferences.getBoolean(KEY_AUTO_TRIM, true),
            trimPaddingMs = preferences.getInt(KEY_TRIM_PADDING_MS, 50)
        )
    }

    fun savePlaybackSettings(settings: PlaybackSettings) {
        preferences.edit()
            .putFloat(KEY_SPEED, settings.normalizedSpeed.toFloat())
            .putBoolean(KEY_AUTO_TRIM, settings.autoTrimEnabled)
            .putInt(KEY_TRIM_PADDING_MS, settings.normalizedTrimPaddingMs)
            .apply()
    }

    fun keyboardZoom(): KeyboardZoom {
        return runCatching {
            KeyboardZoom.valueOf(preferences.getString(KEY_KEYBOARD_ZOOM, KeyboardZoom.STANDARD.name)!!)
        }.getOrDefault(KeyboardZoom.STANDARD)
    }

    fun setKeyboardZoom(zoom: KeyboardZoom) {
        preferences.edit().putString(KEY_KEYBOARD_ZOOM, zoom.name).apply()
    }

    fun gameMode(): GameMode {
        return runCatching {
            GameMode.valueOf(preferences.getString(KEY_GAME_MODE, GameMode.TEACHING.name)!!)
        }.getOrDefault(GameMode.TEACHING)
    }

    fun setGameMode(mode: GameMode) {
        preferences.edit().putString(KEY_GAME_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_LIBRARY = "library"
        const val KEY_LAST_URI = "last-uri"
        const val KEY_LAST_TRACKS = "last-selected-tracks"
        const val KEY_LAST_PICKER_URI = "last-picker-uri"
        const val KEY_SPEED = "playback-speed"
        const val KEY_AUTO_TRIM = "auto-trim"
        const val KEY_TRIM_PADDING_MS = "trim-padding-ms"
        const val KEY_KEYBOARD_ZOOM = "keyboard-zoom"
        const val KEY_GAME_MODE = "game-mode"
    }
}
