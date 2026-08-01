package com.example.midigameengine

import android.content.Context
import android.midi.AndroidMidiInputReal
import android.net.Uri
import core.chart.ChartGenerator
import core.chart.PlayableChart
import core.chart.PlaybackSettings
import core.chart.PlaybackWindow
import core.judgment.JudgmentEngine
import core.judgment.Judgment
import core.judgment.TimingWindow
import core.midi.ControlChange
import core.midi.NoteOff
import core.midi.NoteOn
import core.midi.StandardMidiFileLoader
import core.model.SongModel
import core.model.SongNote
import core.model.SongTrack
import core.runtime.GameSessionStateful
import core.time.SystemClock
import core.time.Transport
import core.visualization.KeyboardProfile
import core.visualization.KeyboardProfileDetector
import core.visualization.KeyboardProfileMode
import core.visualization.KeyboardZoom
import core.visualization.PitchRange
import kotlin.concurrent.thread

class TeachingSessionController(
    private val context: Context,
    private val onStateChanged: (TeachingUiState) -> Unit,
    private val onTrackSelectionRequired: (List<TrackChoice>) -> Unit
) {

    data class TrackChoice(val index: Int, val id: String, val label: String, val noteCount: Int, val selected: Boolean)

    private val lock = Any()
    private val transport = Transport(SystemClock())
    private val midiInput = AndroidMidiInputReal(context, transport)
    private val playbackSynth = MidiPlaybackSynthesizer()
    private val preferences = AppPreferencesStore(context)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var currentSourceLabel = "Demo"
    private var currentHeadline = "Loading..."
    private var currentDeviceStatus = "Waiting for MIDI device"
    private var currentChart = PlayableChart(emptyList())
    private var session = newSession(currentChart)
    private var pendingSong: SongModel? = null
    private var pendingUri: Uri? = null
    private var pendingDisplayName: String = "MIDI file"
    private var currentSong: SongModel? = null
    private var currentUri: Uri? = null
    private var selectedTrackIds: Set<String> = emptySet()
    private var running = false
    private var released = false
    private var playing = false
    private val physicalHeldPitches = mutableSetOf<Int>()
    private var lastInputPitch: Int? = null
    private var lastInputCorrect: Boolean? = null
    private var inputFeedbackUntilUs = 0L
    private var deviceDescription: String? = null
    private val observedInputPitches = mutableSetOf<Int>()
    private var keyboardProfileMode = KeyboardProfileMode.AUTO
    private var keyboardProfile = KeyboardProfile.KEYS_88
    private var visibleRange = PitchRange(21, 108)
    private var playbackSettings = preferences.playbackSettings()
    private var keyboardZoom = preferences.keyboardZoom()
    private var playbackWindow = PlaybackWindow(0L, 0L)
    private var scrubbing = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            emitState()
            if (running) {
                mainHandler.postDelayed(this, 33L)
            }
        }
    }

    init {
        midiInput.setListener { event ->
            synchronized(lock) {
                val judgment = session.onInput(event)
                currentHeadline = when (event) {
                    is NoteOn -> {
                        physicalHeldPitches += event.pitch
                        observedInputPitches += event.pitch
                        if (keyboardProfileMode == KeyboardProfileMode.AUTO) {
                            keyboardProfile = KeyboardProfileDetector.detect(deviceDescription, observedInputPitches)
                        }
                        lastInputPitch = event.pitch
                        lastInputCorrect = judgment != null && judgment != Judgment.Miss
                        inputFeedbackUntilUs = transport.positionNs() / 1000L + 450_000L
                        "Pitch ${event.pitch} -> ${judgment?.name ?: "ignored"}"
                    }
                    is NoteOff -> {
                        physicalHeldPitches -= event.pitch
                        "Note off ${event.pitch}"
                    }
                    is ControlChange -> "CC ${event.controller} = ${event.value}"
                }
            }
            emitState()
        }

        midiInput.setStatusListener { status ->
            synchronized(lock) {
                currentDeviceStatus = status
            }
            emitState()
        }

        midiInput.setDeviceInfoListener { description ->
            synchronized(lock) {
                deviceDescription = description
                val saved = preferences.layoutPreference(description)
                keyboardProfileMode = saved.mode
                keyboardProfile = if (saved.mode == KeyboardProfileMode.AUTO) {
                    KeyboardProfileDetector.detect(description, observedInputPitches)
                } else {
                    saved.profile
                }
                visibleRange = saved.visibleRange
            }
            emitState()
        }
    }

    fun start() {
        if (released) {
            AppDebugLogger.log("Ignoring start after release")
            return
        }
        if (running) return
        AppDebugLogger.log("Controller start")
        running = true
        midiInput.start()
        if (playing && !transport.isRunning()) {
            transport.resume()
        }
        playbackSynth.sync(transport.positionNs() / 1000L, playing)
        mainHandler.post(frameRunnable)
    }

    fun stop() {
        AppDebugLogger.log("Controller stop")
        running = false
        mainHandler.removeCallbacks(frameRunnable)
        if (playing) {
            transport.pause()
        }
        playbackSynth.sync(transport.positionNs() / 1000L, false)
        midiInput.stop()
        emitState()
    }

    fun play() {
        AppDebugLogger.log("Play requested")
        synchronized(lock) {
            if (transport.positionNs() / 1000L >= playbackWindow.endUs) {
                resetSessionAt(playbackWindow.startUs)
            }
            transport.setRate(playbackSettings.normalizedSpeed)
            transport.seekTo(transport.positionNs() / 1000L)
            if (!playing) {
                transport.resume()
                playing = true
                currentHeadline = "Playing"
                playbackSynth.sync(transport.positionNs() / 1000L, true)
            }
        }
        emitState()
    }

    fun pause() {
        AppDebugLogger.log("Pause requested")
        synchronized(lock) {
            if (playing) {
                transport.pause()
                playing = false
                currentHeadline = "Paused"
                playbackSynth.sync(transport.positionNs() / 1000L, false)
            }
        }
        emitState()
    }

    fun restart() {
        AppDebugLogger.log("Restart requested")
        synchronized(lock) {
            resetSessionAt(playbackWindow.startUs)
            transport.setRate(playbackSettings.normalizedSpeed)
            transport.resume()
            playing = true
            currentHeadline = "Restarted"
            playbackSynth.seek(playbackWindow.startUs, true)
        }
        emitState()
    }

    fun loadBundledDemo() {
        val bytes = context.assets.open("preloaded_song.mid").use { it.readBytes() }
        loadMidiBytes(bytes, "Bundled demo", null, persist = false)
    }

    fun restoreLast() {
        val uriString = preferences.lastUri() ?: run {
            loadBundledDemo()
            pause()
            return
        }
        val uri = Uri.parse(uriString)
        val entry = preferences.library().firstOrNull { it.uri == uriString }
        val preferredTrackIds = entry?.selectedTrackIds
            ?.takeIf { it.isNotEmpty() }
            ?: preferences.lastSelectedTrackIds()
        AppDebugLogger.log(
            "Restoring MIDI uri=$uriString selectedTracks=${preferredTrackIds.size}"
        )
        loadUri(
            uri = uri,
            displayName = entry?.displayName ?: "MIDI file",
            preferredTrackIds = preferredTrackIds,
            startPlaying = false
        )
    }

    fun importMidi(uri: Uri, displayName: String) {
        AppDebugLogger.log("Import requested name=$displayName uri=$uri")
        preferences.setLastPickerUri(uri.toString())
        currentHeadline = "Importing $displayName..."
        emitState()

        thread(name = "midi-import") {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: error("Unable to open the selected MIDI file")
            }.onSuccess { bytes ->
                mainHandler.post {
                    if (released) return@post
                    runCatching {
                        if (released) return@runCatching
                        loadMidiBytes(bytes, displayName, uri, persist = true)
                    }.onFailure { error ->
                        currentHeadline = "Import failed: ${error.message ?: "Unknown error"}"
                        emitState()
                    }
                }
            }.onFailure { error ->
                mainHandler.post {
                    currentHeadline = "Import failed: ${error.message ?: "Unknown error"}"
                    emitState()
                }
            }
        }
    }

    fun openTrackSelection() {
        val song = synchronized(lock) { currentSong } ?: return
        onTrackSelectionRequired(trackChoices(song, selectedTrackIds))
    }

    fun libraryEntries(): List<LibraryEntry> = preferences.library()

    fun lastPickerUri(): Uri? = preferences.lastPickerUri()?.let(Uri::parse)

    fun loadLibraryEntry(entry: LibraryEntry) {
        loadUri(Uri.parse(entry.uri), entry.displayName, entry.selectedTrackIds)
    }

    fun removeLibraryEntry(entry: LibraryEntry) {
        preferences.saveLibrary(preferences.library().filterNot { it.uri == entry.uri })
        if (preferences.lastUri() == entry.uri) {
            preferences.setLastUri(null)
            preferences.setLastSelectedTrackIds(emptySet())
        }
        emitState()
    }

    fun setManualKeyboardProfile(profile: KeyboardProfile) {
        synchronized(lock) {
            keyboardProfileMode = KeyboardProfileMode.MANUAL
            keyboardProfile = profile
            saveLayoutPreference()
        }
        emitState()
    }

    fun setAutoKeyboardProfile() {
        synchronized(lock) {
            keyboardProfileMode = KeyboardProfileMode.AUTO
            keyboardProfile = KeyboardProfileDetector.detect(deviceDescription, physicalHeldPitches)
            saveLayoutPreference()
        }
        emitState()
    }

    fun setVisibleRange(range: PitchRange) {
        synchronized(lock) {
            visibleRange = range
            saveLayoutPreference()
        }
        emitState()
    }

    fun setVisibleRangeToSelectedTracks() {
        synchronized(lock) {
            val pitches = currentSong?.tracks
                ?.filter { it.id in selectedTrackIds }
                ?.flatMap { track -> track.notes.map { it.pitch } }
                .orEmpty()
            if (pitches.isNotEmpty()) {
                visibleRange = PitchRange(
                    (pitches.minOrNull()!! - 2).coerceIn(21, 108),
                    (pitches.maxOrNull()!! + 2).coerceIn(21, 108)
                )
                saveLayoutPreference()
            }
        }
        emitState()
    }

    fun setSpeed(speed: Double) {
        synchronized(lock) {
            playbackSettings = playbackSettings.copy(speed = speed)
            transport.setRate(playbackSettings.normalizedSpeed)
            preferences.savePlaybackSettings(playbackSettings)
        }
        emitState()
    }

    fun setAutoTrimEnabled(enabled: Boolean) {
        synchronized(lock) {
            playbackSettings = playbackSettings.copy(autoTrimEnabled = enabled)
            preferences.savePlaybackSettings(playbackSettings)
            recalculatePlaybackWindow(resetToStart = true)
            resetSessionAt(playbackWindow.startUs)
            playbackSynth.seek(playbackWindow.startUs, playing)
        }
        emitState()
    }

    fun setTrimPaddingMs(paddingMs: Int) {
        synchronized(lock) {
            playbackSettings = playbackSettings.copy(trimPaddingMs = paddingMs)
            preferences.savePlaybackSettings(playbackSettings)
            recalculatePlaybackWindow(resetToStart = true)
            resetSessionAt(playbackWindow.startUs)
            playbackSynth.seek(playbackWindow.startUs, false)
        }
        emitState()
    }

    fun setKeyboardZoom(zoom: KeyboardZoom) {
        synchronized(lock) {
            keyboardZoom = zoom
            preferences.setKeyboardZoom(zoom)
        }
        emitState()
    }

    fun toggleAutoTrim() {
        setAutoTrimEnabled(!playbackSettings.autoTrimEnabled)
    }

    fun beginScrub() {
        synchronized(lock) {
            scrubbing = true
            if (playing) {
                transport.pause()
                playing = false
            }
        }
        emitState()
    }

    fun scrubToFraction(fraction: Float) {
        synchronized(lock) {
            val position = playbackWindow.startUs +
                (playbackWindow.durationUs * fraction.coerceIn(0f, 1f)).toLong()
            resetSessionAt(position)
            transport.seekTo(position)
            playbackSynth.seek(position, false)
        }
        emitState()
    }

    fun endScrub() {
        synchronized(lock) {
            scrubbing = false
        }
        emitState()
    }

    fun selectTracks(indices: List<Int>, sourceLabel: String = pendingDisplayName) {
        val song = synchronized(lock) { pendingSong ?: currentSong }
            ?: error("No MIDI track selection is pending")
        require(indices.isNotEmpty()) { "Select at least one MIDI track" }
        require(indices.all { it in song.tracks.indices }) { "Invalid MIDI track selection" }

        val selectedTracks = indices.distinct().map { song.tracks[it] }
        val selectedIds = selectedTracks.map { it.id }.toSet()
        val sourceUri = synchronized(lock) { pendingUri ?: currentUri }
        synchronized(lock) {
            pendingSong = null
            pendingUri = null
        }
        loadSong(song, sourceLabel, sourceUri, selectedIds, persist = true)
    }

    private fun loadMidiBytes(
        bytes: ByteArray,
        sourceLabel: String,
        uri: Uri?,
        persist: Boolean,
        preferredTrackIds: Set<String> = emptySet(),
        startPlaying: Boolean = true
    ) {
        val song = StandardMidiFileLoader.load(bytes)
        synchronized(lock) {
            pendingSong = null
        }
        if (song.tracks.size > 1) {
            val restoredIds = preferredTrackIds.intersect(song.tracks.map { it.id }.toSet())
            if (restoredIds.isNotEmpty()) {
                loadSong(song, sourceLabel, uri, restoredIds, persist = false, startPlaying = startPlaying)
                return
            }
            synchronized(lock) {
                pendingSong = song
                pendingUri = uri
                pendingDisplayName = sourceLabel
                currentHeadline = "Choose one or more tracks"
            }
            val choices = trackChoices(song, emptySet())
            mainHandler.post { onTrackSelectionRequired(choices) }
            emitState()
            return
        }
        loadSong(song, sourceLabel, uri, song.tracks.map { it.id }.toSet(), persist, startPlaying)
    }

    private fun loadUri(
        uri: Uri,
        displayName: String,
        preferredTrackIds: Set<String> = emptySet(),
        startPlaying: Boolean = true
    ) {
        thread(name = "midi-restore") {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to open saved MIDI file")
            }.onSuccess { bytes ->
                mainHandler.post {
                    runCatching {
                        loadMidiBytes(bytes, displayName, uri, persist = false, preferredTrackIds = preferredTrackIds, startPlaying = startPlaying)
                    }.onFailure {
                        currentHeadline = "Saved MIDI unavailable"
                        emitState()
                    }
                }
            }.onFailure {
                currentHeadline = "Saved MIDI unavailable"
                emitState()
            }
        }
    }

    private fun loadSong(
        song: SongModel,
        sourceLabel: String,
        uri: Uri?,
        trackIds: Set<String>,
        persist: Boolean,
        startPlaying: Boolean = true
    ) {
        val chart = ChartGenerator.fromSong(song, trackIds)
        synchronized(lock) {
            currentSong = song
            currentUri = uri
            selectedTrackIds = trackIds
            currentChart = chart
            session = newSession(chart)
            currentSourceLabel = sourceLabel
            currentHeadline = "Loaded ${sourceLabel}"
            recalculatePlaybackWindow(resetToStart = true)
            transport.setRate(playbackSettings.normalizedSpeed)
            transport.seekTo(playbackWindow.startUs)
            playing = startPlaying
            physicalHeldPitches.clear()
            lastInputPitch = null
            lastInputCorrect = null
            inputFeedbackUntilUs = 0L
            playbackSynth.load(chart.events)
            playbackSynth.seek(playbackWindow.startUs, startPlaying)
            if (persist && uri != null) {
                val updated = preferences.library().filterNot { it.uri == uri.toString() } +
                    LibraryEntry(uri.toString(), sourceLabel, trackIds)
                preferences.saveLibrary(updated)
            }
            if (uri != null) preferences.setLastUri(uri.toString())
            if (uri != null) preferences.setLastSelectedTrackIds(trackIds)
        }
        emitState()
    }

    private fun recalculatePlaybackWindow(resetToStart: Boolean) {
        playbackWindow = if (playbackSettings.autoTrimEnabled) {
            PlaybackWindow.fromChart(currentChart, playbackSettings.trimPaddingUs)
        } else {
            PlaybackWindow.fullChart(currentChart, currentSong?.let { ChartGenerator.durationUs(it) })
        }
        if (resetToStart) {
            transport.seekTo(playbackWindow.startUs)
        }
    }

    private fun resetSessionAt(positionUs: Long) {
        session = newSession(currentChart)
        physicalHeldPitches.clear()
        lastInputPitch = null
        lastInputCorrect = null
        inputFeedbackUntilUs = 0L
        transport.seekTo(playbackWindow.clamp(positionUs))
    }

    private fun trackChoices(song: SongModel, selectedIds: Set<String>): List<TrackChoice> {
        return song.tracks.mapIndexed { index, track ->
            TrackChoice(
                index = index,
                id = track.id,
                label = track.name?.takeIf { it.isNotBlank() } ?: "Track ${index + 1}",
                noteCount = track.notes.size,
                selected = track.id in selectedIds
            )
        }
    }

    private fun saveLayoutPreference() {
        preferences.saveLayoutPreference(
            deviceDescription,
            LayoutPreference(keyboardProfileMode, keyboardProfile, visibleRange)
        )
    }

    private fun newSession(chart: PlayableChart): GameSessionStateful {
        val judgmentEngine = JudgmentEngine(
            TimingWindow(
                perfectUs = 50_000L,
                greatUs = 100_000L,
                goodUs = 200_000L
            )
        )
        return GameSessionStateful(chart, judgmentEngine)
    }

    private fun emitState() {
        val snapshot = synchronized(lock) {
            if (released) return
            val rawTimeUs = transport.positionNs() / 1000L
            if (playing && playbackWindow.durationUs > 0L && rawTimeUs >= playbackWindow.endUs) {
                transport.seekTo(playbackWindow.endUs)
                transport.pause()
                playing = false
                currentHeadline = "Complete"
            }
            val currentTimeUs = playbackWindow.clamp(transport.positionNs() / 1000L)
            playbackSynth.sync(currentTimeUs, playing)
            val notes = currentChart.events.map {
                TeachingNoteState(
                    pitch = it.pitch,
                    startTimeUs = it.targetTimeUs,
                    durationUs = it.durationUs,
                    matched = it.matched
                )
            }
            val activeNotes = notes.filter {
                currentTimeUs in it.startTimeUs..(it.startTimeUs + it.durationUs)
            }
            val nextExpectedNotes = notes.filterNot { it.matched }.take(8)
            val chartLengthUs = playbackWindow.durationUs
            val progress = if (chartLengthUs <= 0L) {
                0f
            } else {
                ((currentTimeUs - playbackWindow.startUs).toFloat() / chartLengthUs.toFloat()).coerceIn(0f, 1f)
            }

            TeachingUiState(
                sourceLabel = currentSourceLabel,
                deviceStatus = currentDeviceStatus,
                headline = currentHeadline,
                playbackTimeUs = currentTimeUs,
                chartLengthUs = chartLengthUs,
                combo = session.getCombo(),
                maxCombo = session.getMaxCombo(),
                judgmentCount = session.getResults().size,
                progress = progress,
                notes = notes,
                nextExpectedNotes = nextExpectedNotes,
                activeNotes = activeNotes,
                physicalHeldPitches = physicalHeldPitches.toSet(),
                lastInputPitch = lastInputPitch,
                lastInputCorrect = lastInputCorrect,
                inputFeedbackUntilUs = inputFeedbackUntilUs,
                trackSummary = currentSong?.tracks
                    ?.filter { it.id in selectedTrackIds }
                    ?.joinToString(" + ") { it.name ?: it.id }
                    ?: "No MIDI loaded",
                physicalProfileLabel = if (keyboardProfileMode == KeyboardProfileMode.AUTO) {
                    "Auto ${keyboardProfile.label}"
                } else {
                    keyboardProfile.label
                },
                visibleRangeFirstPitch = visibleRange.firstPitch,
                visibleRangeLastPitch = visibleRange.lastPitch,
                layoutModeLabel = keyboardProfileMode.name,
                libraryCount = preferences.library().size,
                playbackStartUs = playbackWindow.startUs,
                playbackEndUs = playbackWindow.endUs,
                speed = playbackSettings.normalizedSpeed,
                autoTrimEnabled = playbackSettings.autoTrimEnabled,
                trimPaddingMs = playbackSettings.normalizedTrimPaddingMs,
                keyboardZoomLabel = keyboardZoom.label,
                isPlaying = playing,
                isScrubbing = scrubbing
            )
        }

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            onStateChanged(snapshot)
        } else {
            mainHandler.post {
                onStateChanged(snapshot)
            }
        }
    }

    fun release() {
        if (released) return
        AppDebugLogger.log("Controller release")
        released = true
        running = false
        mainHandler.removeCallbacks(frameRunnable)
        transport.pause()
        midiInput.stop()
        playbackSynth.release()
    }
}
