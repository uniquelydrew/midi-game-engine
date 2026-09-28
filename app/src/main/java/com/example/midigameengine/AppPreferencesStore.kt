package com.example.midigameengine

import android.content.Context
import core.chart.PlaybackSettings
import core.drums.DrumKitProfile
import core.drums.DrumTarget
import core.drums.DrumTrigger
import core.drums.DrumKitLayoutPiece
import core.drums.DrumKitLayouts
import core.drums.DrumSequenceAssignment
import core.drums.DrumSequenceProfile
import core.runtime.WhackDifficulty
import core.runtime.LoopStopRule
import core.visualization.KeyboardProfile
import core.visualization.KeyboardProfileMode
import core.visualization.KeyboardZoom
import core.visualization.PitchRange
import org.json.JSONArray
import org.json.JSONObject

data class LibraryEntry(
    val uri: String,
    val displayName: String,
    val selectedTrackIds: Set<String> = emptySet(),
    val drumSelectedTrackIds: Set<String> = emptySet()
)

data class LayoutPreference(
    val mode: KeyboardProfileMode = KeyboardProfileMode.AUTO,
    val profile: KeyboardProfile = KeyboardProfile.KEYS_88,
    val visibleRange: PitchRange = PitchRange(21, 108),
    val visibleRangeMode: VisibleRangeMode = VisibleRangeMode.SELECTED_TRACKS
)

data class LoopPreference(
    val startUs: Long,
    val endUs: Long,
    val stopRule: LoopStopRule = LoopStopRule.Forever,
    val countInEnabled: Boolean = true
)

data class DrumKitProfileLibrary(val activeName: String, val profiles: List<DrumKitProfile>)

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
                        },
                        drumSelectedTrackIds = buildSet {
                            val drumTracks = item.optJSONArray("drumTracks") ?: JSONArray()
                            for (trackIndex in 0 until drumTracks.length()) add(drumTracks.optString(trackIndex))
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
                    .put("drumTracks", JSONArray(entry.drumSelectedTrackIds.toList()))
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
        val rangeMode = runCatching {
            VisibleRangeMode.valueOf(
                preferences.getString(prefix + "range-mode", VisibleRangeMode.SELECTED_TRACKS.name)!!
            )
        }.getOrDefault(VisibleRangeMode.SELECTED_TRACKS)
        return LayoutPreference(mode, profile, PitchRange(first, last), rangeMode)
    }

    fun saveLayoutPreference(deviceKey: String?, preference: LayoutPreference) {
        val prefix = deviceKey?.let { "layout.$it." } ?: "layout.default."
        preferences.edit()
            .putString(prefix + "mode", preference.mode.name)
            .putString(prefix + "profile", preference.profile.name)
            .putInt(prefix + "first", preference.visibleRange.firstPitch)
            .putInt(prefix + "last", preference.visibleRange.lastPitch)
            .putString(prefix + "range-mode", preference.visibleRangeMode.name)
            .apply()
    }

    fun drumKitProfile(deviceKey: String?): DrumKitProfile? {
        val library = drumKitProfiles(deviceKey)
        return library.profiles.firstOrNull { it.name == library.activeName } ?: library.profiles.firstOrNull()
    }

    fun drumKitProfiles(deviceKey: String?): DrumKitProfileLibrary {
        val key = drumProfileLibraryKey(deviceKey)
        val raw = preferences.getString(key, null)
        if (raw == null) {
            val legacy = readLegacyDrumKitProfile(deviceKey)
            val profiles = listOf(legacy ?: DrumKitProfile("My drum kit", emptyList()))
            val migrated = DrumKitProfileLibrary(profiles.first().name, profiles)
            saveDrumKitProfiles(deviceKey, migrated)
            return migrated
        }
        return runCatching {
            val root = JSONObject(raw)
            val profiles = (root.optJSONArray("profiles") ?: JSONArray()).let { array ->
                buildList { for (i in 0 until array.length()) array.optJSONObject(i)?.let { parseDrumKitProfile(it) }?.let(::add) }
            }.ifEmpty { listOf(DrumKitProfile("My drum kit", emptyList())) }
            val active = root.optString("active", profiles.first().name).takeIf { candidate -> profiles.any { it.name == candidate } }
                ?: profiles.first().name
            DrumKitProfileLibrary(active, profiles)
        }.getOrElse { DrumKitProfileLibrary("My drum kit", listOf(DrumKitProfile("My drum kit", emptyList()))) }
    }

    fun saveDrumKitProfiles(deviceKey: String?, library: DrumKitProfileLibrary) {
        require(library.profiles.isNotEmpty())
        require(library.profiles.map { it.name }.distinct().size == library.profiles.size)
        val root = JSONObject().put("active", library.activeName).put("profiles", JSONArray().apply {
            library.profiles.forEach { put(drumKitProfileJson(it)) }
        })
        preferences.edit().putString(drumProfileLibraryKey(deviceKey), root.toString()).apply()
    }

    fun setActiveDrumKitProfile(deviceKey: String?, name: String) {
        val library = drumKitProfiles(deviceKey)
        if (library.profiles.any { it.name == name }) saveDrumKitProfiles(deviceKey, library.copy(activeName = name))
    }

    fun saveDrumKitProfile(deviceKey: String?, profile: DrumKitProfile) {
        val library = drumKitProfiles(deviceKey)
        val replaced = library.profiles.filterNot { it.name == profile.name } + profile
        saveDrumKitProfiles(deviceKey, DrumKitProfileLibrary(profile.name, replaced))
    }

    private fun readLegacyDrumKitProfile(deviceKey: String?): DrumKitProfile? {
        val key = drumProfileKey(deviceKey)
        val raw = preferences.getString(key, null) ?: return null
        return runCatching { parseDrumKitProfile(JSONObject(raw)) }.getOrNull()
    }

    private fun drumKitProfileJson(profile: DrumKitProfile): JSONObject {
        val triggers = JSONArray()
        profile.triggers.forEach { trigger ->
            triggers.put(
                JSONObject()
                    .put("target", trigger.target.name)
                    .put("note", trigger.midiNote)
                    .put("channel", trigger.channel ?: JSONObject.NULL)
                    .put("minVelocity", trigger.minVelocity)
            )
        }
        return JSONObject()
            .put("name", profile.name)
            .put("triggers", triggers)
            .put("layout", JSONArray().apply { profile.layout.forEach { piece ->
                put(JSONObject().put("target", piece.target.name).put("visible", piece.visible).put("x", piece.xFraction).put("y", piece.yFraction))
            } })
    }

    private fun parseDrumKitProfile(root: JSONObject): DrumKitProfile {
        val triggersJson = root.optJSONArray("triggers") ?: JSONArray()
        val triggers = buildList { for (index in 0 until triggersJson.length()) {
            val item = triggersJson.optJSONObject(index) ?: continue
            val target = runCatching { DrumTarget.valueOf(item.getString("target")) }.getOrNull() ?: continue
            val note = item.optInt("note", -1); if (note !in 0..127) continue
            add(DrumTrigger(target, note, if (item.has("channel") && !item.isNull("channel")) item.optInt("channel").takeIf { it in 0..15 } else null, item.optInt("minVelocity", 1).coerceIn(1, 127)))
        } }
        val layout = (root.optJSONArray("layout") ?: JSONArray()).let { array -> buildList { for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val target = runCatching { DrumTarget.valueOf(item.getString("target")) }.getOrNull() ?: continue
            add(DrumKitLayoutPiece(target, item.optBoolean("visible", true), item.optDouble("x", .5).toFloat().coerceIn(0f, 1f), item.optDouble("y", .5).toFloat().coerceIn(0f, 1f)))
        } } }
        return DrumKitProfile(root.optString("name", "Drum kit"), triggers, DrumKitLayouts.normalized(layout))
    }

    fun clearDrumKitProfile(deviceKey: String?) {
        preferences.edit().remove(drumProfileKey(deviceKey)).remove(drumProfileLibraryKey(deviceKey)).apply()
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
            GameMode.valueOf(preferences.getString(KEY_GAME_MODE, GameMode.HOME.name)!!)
        }.getOrDefault(GameMode.HOME)
    }

    fun setGameMode(mode: GameMode) {
        preferences.edit().putString(KEY_GAME_MODE, mode.name).apply()
    }

    fun whackDifficulty(): WhackDifficulty {
        return runCatching {
            WhackDifficulty.valueOf(
                preferences.getString(KEY_WHACK_DIFFICULTY, WhackDifficulty.STANDARD.name)!!
            )
        }.getOrDefault(WhackDifficulty.STANDARD)
    }

    fun setWhackDifficulty(difficulty: WhackDifficulty) {
        preferences.edit().putString(KEY_WHACK_DIFFICULTY, difficulty.name).apply()
    }

    fun loopPreference(mode: GameMode, sourceUri: String): LoopPreference? {
        val root = runCatching { JSONObject(preferences.getString(KEY_LOOPS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val item = root.optJSONObject("${mode.name}:$sourceUri") ?: return null
        val start = item.optLong("start", -1L)
        val end = item.optLong("end", -1L)
        if (start < 0L || end <= start) return null
        val rule = when (item.optString("rule", "FOREVER")) {
            "PASSES" -> LoopStopRule.PassCount(item.optInt("value", 4).coerceAtLeast(1))
            "DURATION" -> LoopStopRule.Duration(item.optLong("value", 5 * 60_000L).coerceAtLeast(1L))
            else -> LoopStopRule.Forever
        }
        return LoopPreference(start, end, rule, item.optBoolean("countIn", true))
    }

    fun saveLoopPreference(mode: GameMode, sourceUri: String, value: LoopPreference?) {
        val root = runCatching { JSONObject(preferences.getString(KEY_LOOPS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val key = "${mode.name}:$sourceUri"
        if (value == null) root.remove(key) else {
            val (rule, ruleValue) = when (val stop = value.stopRule) {
                LoopStopRule.Forever -> "FOREVER" to 0L
                is LoopStopRule.PassCount -> "PASSES" to stop.passes.toLong()
                is LoopStopRule.Duration -> "DURATION" to stop.durationMs
            }
            root.put(key, JSONObject()
                .put("start", value.startUs).put("end", value.endUs)
                .put("rule", rule).put("value", ruleValue).put("countIn", value.countInEnabled))
        }
        preferences.edit().putString(KEY_LOOPS, root.toString()).apply()
    }

    fun drumSourceProfile(sourceUri: String): DrumSequenceProfile? {
        val root = runCatching { JSONObject(preferences.getString(KEY_DRUM_SOURCE_MAPPINGS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val item = root.optJSONObject(sourceUri) ?: return null
        val assignments = buildMap {
            item.keys().forEach { rawPitch ->
                val pitch = rawPitch.toIntOrNull()?.takeIf { it in 0..127 } ?: return@forEach
                val raw = item.optString(rawPitch)
                val assignment = if (raw == "IGNORE") DrumSequenceAssignment.Ignore else {
                    runCatching { DrumTarget.valueOf(raw) }.getOrNull()?.let { DrumSequenceAssignment.Target(it) }
                }
                if (assignment != null) put(pitch, assignment)
            }
        }
        return DrumSequenceProfile(assignments)
    }

    fun saveDrumSourceProfile(sourceUri: String, profile: DrumSequenceProfile) {
        val root = runCatching { JSONObject(preferences.getString(KEY_DRUM_SOURCE_MAPPINGS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val item = JSONObject()
        profile.assignments.forEach { (pitch, assignment) ->
            item.put(pitch.toString(), when (assignment) {
                DrumSequenceAssignment.Ignore -> "IGNORE"
                is DrumSequenceAssignment.Target -> assignment.target.name
            })
        }
        root.put(sourceUri, item)
        preferences.edit().putString(KEY_DRUM_SOURCE_MAPPINGS, root.toString()).apply()
    }

    private fun drumProfileKey(deviceKey: String?): String {
        return deviceKey?.let { "drums.$it.profile" } ?: "drums.default.profile"
    }

    private fun drumProfileLibraryKey(deviceKey: String?): String =
        deviceKey?.let { "drums.$it.profiles" } ?: "drums.default.profiles"

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
        const val KEY_WHACK_DIFFICULTY = "whack-difficulty"
        const val KEY_LOOPS = "practice-loops"
        const val KEY_DRUM_SOURCE_MAPPINGS = "drum-source-mappings"
    }
}
