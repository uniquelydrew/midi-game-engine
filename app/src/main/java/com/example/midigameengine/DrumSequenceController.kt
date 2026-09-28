package com.example.midigameengine

import android.content.Context
import android.media.ToneGenerator
import android.media.AudioManager
import android.midi.AndroidMidiInputReal
import android.net.Uri
import core.chart.ChartGenerator
import core.drums.DrumKitProfile
import core.drums.DrumKitProfiles
import core.drums.DrumTrigger
import core.drums.DrumSequence
import core.drums.DrumSequenceAssignment
import core.drums.DrumSequenceFeedback
import core.drums.DrumSequenceProfile
import core.drums.DrumTarget
import core.midi.NoteOn
import core.midi.StandardMidiFileLoader
import core.model.SongModel
import core.runtime.DrumSequenceSession
import core.runtime.LoopStopRule
import core.runtime.PracticeLoop
import core.runtime.PracticeLoopSession
import core.time.SystemClock
import core.time.Transport
import kotlin.concurrent.thread

class DrumSequenceController(
    private val context: Context,
    private val onStateChanged: (DrumSequenceUiState) -> Unit,
    private val onTrackSelectionRequired: (List<TeachingSessionController.TrackChoice>) -> Unit
) {
    private val lock = Any()
    private val transport = Transport(SystemClock())
    private val midiInput = AndroidMidiInputReal(context, transport)
    private val preferences = AppPreferencesStore(context)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val countInTone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
    private var running = false
    private var released = false
    private var playing = false
    private var countingIn = false
    private var countInBeat: Int? = null
    private var deviceDescription: String? = null
    private var deviceStatus = "MIDI: Waiting for drum kit"
    private var profile: DrumKitProfile? = preferences.drumKitProfile(null)
    private var sourceProfile = DrumSequenceProfile.generalMidi()
    private var song: SongModel? = null
    private var uri: Uri? = null
    private var sourceLabel = "No drum sequence loaded"
    private var selectedTrackIds = emptySet<String>()
    private var pendingSong: SongModel? = null
    private var pendingUri: Uri? = null
    private var pendingLabel = "MIDI file"
    private var sequence: DrumSequence? = null
    private var session: DrumSequenceSession? = null
    private var practiceLoop: PracticeLoop? = null
    private var stopRule: LoopStopRule = LoopStopRule.Forever
    private var loopSession: PracticeLoopSession? = null
    private var countInEnabled = true
    private var lastFeedback: DrumSequenceFeedback? = null
    private var pendingLearnTarget: DrumTarget? = null

    private val frame = object : Runnable {
        override fun run() {
            emitState()
            if (running) handler.postDelayed(this, 33L)
        }
    }

    init {
        midiInput.setListener { event ->
            synchronized(lock) {
                if (event is NoteOn && pendingLearnTarget != null) {
                    val target = pendingLearnTarget!!
                    val base = profile ?: DrumKitProfile(deviceDescription?.let { "$it drum kit" } ?: "Drum kit", emptyList())
                    val replacement = DrumTrigger(target, event.pitch, event.channel)
                    profile = base.copy(triggers = base.triggers.filterNot {
                        it.target == target || (it.midiNote == event.pitch && it.channel == event.channel)
                    } + replacement)
                    preferences.saveDrumKitProfile(deviceDescription, profile!!)
                    pendingLearnTarget = null
                } else if (event is NoteOn && playing) {
                    profile?.resolve(event)?.let { target ->
                        lastFeedback = session?.onStrike(
                            core.drums.DrumStrike(target, event.pitch, event.velocity, event.channel, event.timestampUs)
                        )
                    }
                }
            }
            emitState()
        }
        midiInput.setStatusListener { status ->
            synchronized(lock) { deviceStatus = "MIDI: $status" }
            emitState()
        }
        midiInput.setDeviceInfoListener { description ->
            synchronized(lock) {
                deviceDescription = description
                profile = preferences.drumKitProfile(description) ?: preferences.drumKitProfile(null)
            }
            emitState()
        }
    }

    fun start() {
        if (released || running) return
        profile = preferences.drumKitProfile(deviceDescription)
        running = true
        midiInput.start()
        handler.post(frame)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(frame)
        if (playing) {
            transport.pause()
            loopSession?.pause(System.nanoTime())
        }
        playing = false
        midiInput.stop()
        emitState()
    }

    fun importMidi(uri: Uri, displayName: String) {
        preferences.setLastPickerUri(uri.toString())
        thread(name = "drum-sequence-import") {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to open MIDI") }
                .onSuccess { bytes -> handler.post { loadSong(StandardMidiFileLoader.load(bytes), displayName, uri, null) } }
                .onFailure { emitState() }
        }
    }

    fun selectTracks(indices: List<Int>) {
        val chosenSong = synchronized(lock) { pendingSong ?: song } ?: return
        val ids = indices.filter { it in chosenSong.tracks.indices }.map { chosenSong.tracks[it].id }.toSet()
        if (ids.isNotEmpty()) pendingUri?.let { loadSong(chosenSong, pendingLabel, it, ids) }
    }

    fun openTrackSelection() {
        val currentSong = synchronized(lock) { song } ?: return
        val selected = synchronized(lock) { selectedTrackIds }
        onTrackSelectionRequired(currentSong.tracks.mapIndexed { index, track ->
            TeachingSessionController.TrackChoice(index, track.id, track.name ?: "Track ${index + 1}", track.notes.size, track.id in selected)
        })
    }

    fun libraryEntries(): List<LibraryEntry> = preferences.library()

    fun loadLibraryEntry(entry: LibraryEntry) {
        importMidi(Uri.parse(entry.uri), entry.displayName)
    }

    fun assignSourcePitch(midiPitch: Int, target: DrumTarget?) {
        synchronized(lock) {
            sourceProfile = sourceProfile.copy(assignments = sourceProfile.assignments +
                (midiPitch to (target?.let { DrumSequenceAssignment.Target(it) } ?: DrumSequenceAssignment.Ignore)))
            uri?.toString()?.let { preferences.saveDrumSourceProfile(it, sourceProfile) }
            rebuildSequence()
        }
        emitState()
    }

    fun useGeneralMidiSourceMapping() {
        synchronized(lock) {
            sourceProfile = DrumSequenceProfile.generalMidi()
            uri?.toString()?.let { preferences.saveDrumSourceProfile(it, sourceProfile) }
            rebuildSequence()
        }
        emitState()
    }

    fun useGeneralMidiKit() {
        synchronized(lock) {
            profile = DrumKitProfiles.generalMidi(deviceDescription?.let { "$it drums" } ?: "General MIDI drums")
            preferences.saveDrumKitProfile(deviceDescription, profile!!)
        }
        emitState()
    }

    fun currentProfile(): DrumKitProfile? = synchronized(lock) { profile }
    fun beginLearn(target: DrumTarget) { synchronized(lock) { pause(); pendingLearnTarget = target }; emitState() }
    fun cancelLearn() { synchronized(lock) { pendingLearnTarget = null }; emitState() }
    fun clearMappings() {
        synchronized(lock) {
            profile = DrumKitProfile(deviceDescription?.let { "$it drum kit" } ?: "Drum kit", emptyList())
            preferences.saveDrumKitProfile(deviceDescription, profile!!)
        }
        emitState()
    }

    fun setPracticeLoop(startUs: Long, endUs: Long) {
        synchronized(lock) {
            val end = sequence?.endUs ?: return@synchronized
            val start = startUs.coerceIn(0L, end)
            val boundedEnd = endUs.coerceIn(0L, end)
            if (boundedEnd <= start) return@synchronized
            practiceLoop = PracticeLoop(start, boundedEnd)
            loopSession = PracticeLoopSession(stopRule)
            resetPass(start)
            saveLoopPreference()
        }
        emitState()
    }

    fun clearPracticeLoop() { synchronized(lock) { practiceLoop = null; loopSession = null; saveLoopPreference() }; emitState() }
    fun setLoopStopRule(rule: LoopStopRule) { synchronized(lock) { stopRule = rule; practiceLoop?.let { loopSession = PracticeLoopSession(rule); resetPass(it.startUs) }; saveLoopPreference() }; emitState() }
    fun setCountInEnabled(enabled: Boolean) { synchronized(lock) { countInEnabled = enabled; saveLoopPreference() }; emitState() }
    fun currentLoop(): PracticeLoop? = synchronized(lock) { practiceLoop }
    fun currentStopRule(): LoopStopRule = synchronized(lock) { stopRule }
    fun setLoopStartAtPlayhead() {
        synchronized(lock) {
            val end = practiceLoop?.endUs ?: sequence?.endUs ?: return@synchronized
            val start = (transport.positionNs() / 1_000L).coerceIn(0L, end - 1L)
            setPracticeLoop(start, end)
        }
    }
    fun setLoopEndAtPlayhead() {
        synchronized(lock) {
            val start = practiceLoop?.startUs ?: 0L
            val end = (transport.positionNs() / 1_000L).coerceAtMost(sequence?.endUs ?: return@synchronized)
            if (end > start) setPracticeLoop(start, end)
        }
    }

    fun play() {
        synchronized(lock) {
            if (sequence == null || session == null || profile?.availableTargets().isNullOrEmpty()) return@synchronized
            if (sequence!!.unclassifiedSourcePitches.isNotEmpty()) return@synchronized
            if (countInEnabled) beginCountIn() else beginPlayback()
        }
        emitState()
    }

    fun pause() {
        synchronized(lock) {
            countingIn = false
            if (playing) {
                transport.pause(); loopSession?.pause(System.nanoTime()); playing = false
            }
        }
        emitState()
    }

    fun restart() { synchronized(lock) { resetPass(practiceLoop?.startUs ?: 0L); if (countInEnabled) beginCountIn() else beginPlayback() }; emitState() }

    private fun beginCountIn() {
        countingIn = true
        playing = false
        countInBeat = 1
        fun tick(beat: Int) {
            synchronized(lock) {
                if (!countingIn) return
                countInBeat = beat
                countInTone.startTone(if (beat == 1) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 80)
                if (beat == 4) {
                    handler.postDelayed({ synchronized(lock) { if (countingIn) beginPlayback() }; emitState() }, 500L)
                } else handler.postDelayed({ tick(beat + 1); emitState() }, 500L)
            }
        }
        tick(1)
    }

    private fun beginPlayback() {
        countingIn = false
        countInBeat = null
        transport.resume()
        playing = true
        loopSession?.start(System.nanoTime())
    }

    private fun loadSong(newSong: SongModel, label: String, newUri: Uri, explicitTracks: Set<String>?) {
        val tracks = explicitTracks ?: run {
            val entry = preferences.library().firstOrNull { it.uri == newUri.toString() }
            entry?.drumSelectedTrackIds?.takeIf { it.isNotEmpty() }
        }
        if (tracks.isNullOrEmpty() && newSong.tracks.size > 1) {
            synchronized(lock) { pendingSong = newSong; pendingUri = newUri; pendingLabel = label }
            onTrackSelectionRequired(newSong.tracks.mapIndexed { index, track ->
                TeachingSessionController.TrackChoice(index, track.id, track.name ?: "Track ${index + 1}", track.notes.size, false)
            })
            return
        }
        synchronized(lock) {
            song = newSong; uri = newUri; sourceLabel = label
            sourceProfile = preferences.drumSourceProfile(newUri.toString()) ?: DrumSequenceProfile.generalMidi()
            selectedTrackIds = tracks ?: newSong.tracks.map { it.id }.toSet()
            pendingSong = null; pendingUri = null
            rebuildSequence()
            preferences.loopPreference(GameMode.DRUM_SEQUENCE, newUri.toString())?.let { saved ->
                val end = sequence?.endUs ?: 0L
                val start = saved.startUs.coerceIn(0L, end)
                val boundedEnd = saved.endUs.coerceIn(0L, end)
                if (boundedEnd > start) {
                    practiceLoop = PracticeLoop(start, boundedEnd)
                    stopRule = saved.stopRule
                    loopSession = PracticeLoopSession(stopRule)
                    countInEnabled = saved.countInEnabled
                    resetPass(start)
                }
            }
            val existing = preferences.library().filterNot { it.uri == newUri.toString() }
            val old = preferences.library().firstOrNull { it.uri == newUri.toString() }
            preferences.saveLibrary(existing + LibraryEntry(newUri.toString(), label, old?.selectedTrackIds.orEmpty(), selectedTrackIds))
        }
        emitState()
    }

    private fun rebuildSequence() {
        val currentSong = song ?: return
        val chart = ChartGenerator.fromSong(currentSong, selectedTrackIds)
        sequence = DrumSequence.fromChart(chart.events, sourceProfile)
        session = DrumSequenceSession(sequence!!)
        transport.reset()
        lastFeedback = null
    }

    private fun resetPass(positionUs: Long) {
        session?.resetPass()
        transport.seekTo(positionUs)
        lastFeedback = null
    }

    private fun saveLoopPreference() {
        val source = uri?.toString() ?: return
        val loop = practiceLoop
        preferences.saveLoopPreference(
            GameMode.DRUM_SEQUENCE,
            source,
            loop?.let { LoopPreference(it.startUs, it.endUs, stopRule, countInEnabled) }
        )
    }

    private fun emitState() {
        val state = synchronized(lock) {
            val current = transport.positionNs() / 1_000L
            val loop = practiceLoop
            if (playing) {
                session?.advanceTo(current)
                if (loop != null && current >= loop.endUs) {
                    if (loopSession?.completePass(System.nanoTime()) == true) {
                        transport.seekTo(loop.endUs); transport.pause(); playing = false
                    } else resetPass(loop.startUs)
                } else if (loop == null && sequence != null && current >= sequence!!.endUs) {
                    transport.seekTo(sequence!!.endUs); transport.pause(); playing = false
                }
            }
            val snapshot = loopSession?.snapshot(System.nanoTime())
            DrumSequenceUiState(
                sourceLabel, deviceStatus, profile?.availableTargets()?.toSet().orEmpty(),
                sequence?.unclassifiedSourcePitches.orEmpty(), transport.positionNs() / 1_000L,
                sequence?.endUs ?: 0L, playing, countingIn, countInEnabled, countInBeat,
                loop?.startUs, loop?.endUs, stopRule.label(), snapshot?.completedPasses ?: 0,
                snapshot?.activeElapsedMs ?: 0L, session?.scoreSummary() ?: DrumSequenceUiState.empty().score, lastFeedback
            )
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) onStateChanged(state)
        else handler.post { onStateChanged(state) }
    }

    fun release() { released = true; stop(); countInTone.release() }

    private fun LoopStopRule.label() = when (this) {
        LoopStopRule.Forever -> "Forever"
        is LoopStopRule.PassCount -> "$passes passes"
        is LoopStopRule.Duration -> "${durationMs / 60_000L} min"
    }
}
