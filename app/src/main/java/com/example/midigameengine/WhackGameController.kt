package com.example.midigameengine

import android.content.Context
import android.midi.AndroidMidiInputReal
import core.drums.DrumKitProfile
import core.drums.DrumKitProfiles
import core.drums.DrumTarget
import core.drums.DrumTrigger
import core.midi.NoteOn
import core.runtime.WhackDifficulty
import core.runtime.WhackGameSession
import core.strike.StrikeOutcome
import core.time.SystemClock
import core.time.Transport

class WhackGameController(
    context: Context,
    private val onStateChanged: (WhackUiState) -> Unit,
    private val onMappingLearned: (DrumTarget, Int) -> Unit
) {
    private val lock = Any()
    private val transport = Transport(SystemClock())
    private val midiInput = AndroidMidiInputReal(context, transport)
    private val preferences = AppPreferencesStore(context)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var running = false
    private var released = false
    private var playing = false
    private var hasStartedGame = false
    private var deviceConnected = false
    private var deviceDescription: String? = null
    private var deviceStatus = "Waiting for a MIDI input device"
    private var profile: DrumKitProfile? = preferences.drumKitProfile(null)
    private var difficulty: WhackDifficulty = preferences.whackDifficulty()
    private var session: WhackGameSession? = createSessionAt(0L)
    private var pendingLearnTarget: DrumTarget? = null

    private var headline = if (profile?.availableTargets().isNullOrEmpty()) {
        "Configure a drum kit to begin"
    } else {
        "Ready"
    }
    private var lastOutcome: StrikeOutcome? = null
    private var lastStrikeTarget: DrumTarget? = null
    private var lastMidiNote: Int? = null
    private var lastMidiChannel: Int? = null
    private var lastMidiVelocity: Int? = null
    private var lastVelocity: Int? = null
    private var lastReactionTimeUs: Long? = null

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
            var learned: Pair<DrumTarget, Int>? = null

            synchronized(lock) {
                if (event is NoteOn) {
                    lastMidiNote = event.pitch
                    lastMidiChannel = event.channel
                    lastMidiVelocity = event.velocity
                }

                val learnTarget = pendingLearnTarget
                if (event is NoteOn && learnTarget != null) {
                    val base = profile ?: DrumKitProfile(
                        name = deviceDescription?.let { "$it drum kit" } ?: "Drum kit",
                        triggers = emptyList()
                    )
                    val replacement = DrumTrigger(
                        target = learnTarget,
                        midiNote = event.pitch,
                        channel = event.channel
                    )
                    profile = base.copy(
                        triggers = base.triggers.filterNot {
                            it.target == learnTarget ||
                                (it.midiNote == event.pitch && it.channel == event.channel)
                        } + replacement
                    )
                    preferences.saveDrumKitProfile(deviceDescription, profile!!)
                    pendingLearnTarget = null
                    session = createSessionAt(transport.positionNs() / 1_000L)
                    headline = "${learnTarget.label} mapped to MIDI ${event.pitch}"
                    learned = learnTarget to event.pitch
                    lastOutcome = null
                    lastStrikeTarget = learnTarget
                    lastVelocity = event.velocity
                    lastReactionTimeUs = null
                } else if (event is NoteOn && playing) {
                    val feedback = session?.onInput(event)
                    if (feedback != null) {
                        lastOutcome = feedback.outcome
                        lastStrikeTarget = feedback.strike?.target ?: feedback.target.target
                        lastVelocity = feedback.strike?.velocity
                        lastReactionTimeUs = feedback.reactionTimeUs
                        headline = when (feedback.outcome) {
                            StrikeOutcome.HIT -> {
                                val reactionMs = feedback.reactionTimeUs?.div(1_000L)
                                if (reactionMs != null) {
                                    "Hit: ${feedback.target.target.label} in ${reactionMs}ms"
                                } else {
                                    "Hit: ${feedback.target.target.label}"
                                }
                            }
                            StrikeOutcome.WRONG_TARGET ->
                                "Wrong pad: ${feedback.strike?.target?.label ?: "unknown"}"
                            StrikeOutcome.MISS ->
                                "Miss: ${feedback.target.target.label}"
                        }
                    }
                }
            }

            learned?.let { (target, note) ->
                mainHandler.post { onMappingLearned(target, note) }
            }
            emitState()
        }

        midiInput.setStatusListener { status ->
            synchronized(lock) {
                deviceStatus = status
                deviceConnected = status.startsWith("Connected to ")
                if (!deviceConnected && !status.startsWith("Connecting to ")) {
                    deviceDescription = null
                }
            }
            emitState()
        }

        midiInput.setDeviceInfoListener { description ->
            synchronized(lock) {
                deviceDescription = description
                deviceConnected = true
                profile = preferences.drumKitProfile(description)
                    ?: preferences.drumKitProfile(null)
                session = createSessionAt(transport.positionNs() / 1_000L)
                headline = if (profile?.availableTargets().isNullOrEmpty()) {
                    "Configure drum mappings for $description"
                } else {
                    "Ready"
                }
            }
            emitState()
        }
    }

    fun start() {
        if (released || running) return
        running = true
        midiInput.start()
        if (playing && !transport.isRunning()) {
            transport.resume()
        }
        mainHandler.post(frameRunnable)
        emitState()
    }

    fun stop() {
        if (!running) return
        running = false
        mainHandler.removeCallbacks(frameRunnable)
        if (playing && transport.isRunning()) {
            transport.pause()
        }
        midiInput.stop()
        emitState()
    }

    fun play() {
        synchronized(lock) {
            if (profile?.availableTargets().isNullOrEmpty()) {
                playing = false
                headline = "Configure a drum kit before playing"
                return@synchronized
            }
            if (session == null) {
                session = createSessionAt(transport.positionNs() / 1_000L)
            }
            if (!transport.isRunning()) {
                transport.resume()
            }
            playing = true
            hasStartedGame = true
            headline = "Whack-a-MIDI"
        }
        emitState()
    }

    fun pause() {
        synchronized(lock) {
            if (transport.isRunning()) {
                transport.pause()
            }
            playing = false
            if (pendingLearnTarget == null) {
                headline = "Paused"
            }
        }
        emitState()
    }

    fun restart() {
        synchronized(lock) {
            transport.reset()
            session = createSessionAt(0L)
            resetFeedback()
            if (session == null) {
                playing = false
                hasStartedGame = false
                headline = "Configure a drum kit before playing"
            } else {
                transport.resume()
                playing = true
                hasStartedGame = true
                headline = "Whack-a-MIDI"
            }
        }
        emitState()
    }

    fun beginLearn(target: DrumTarget) {
        synchronized(lock) {
            if (transport.isRunning()) {
                transport.pause()
            }
            playing = false
            pendingLearnTarget = target
            headline = "Hit the ${target.label}"
        }
        emitState()
    }

    fun cancelLearn() {
        synchronized(lock) {
            pendingLearnTarget = null
            headline = if (profile?.availableTargets().isNullOrEmpty()) {
                "Configure a drum kit to begin"
            } else {
                "Ready"
            }
        }
        emitState()
    }

    fun useGeneralMidiMapping() {
        synchronized(lock) {
            profile = DrumKitProfiles.generalMidi(
                deviceDescription?.let { "$it drums" } ?: "General MIDI drums"
            )
            preferences.saveDrumKitProfile(deviceDescription, profile!!)
            pendingLearnTarget = null
            transport.reset()
            session = createSessionAt(0L)
            playing = false
            hasStartedGame = false
            resetFeedback()
            headline = "General MIDI drum mapping loaded"
        }
        emitState()
    }

    fun clearMappings() {
        synchronized(lock) {
            profile = if (deviceDescription != null) {
                DrumKitProfile(
                    name = "${deviceDescription} drum kit",
                    triggers = emptyList()
                ).also { preferences.saveDrumKitProfile(deviceDescription, it) }
            } else {
                preferences.clearDrumKitProfile(null)
                null
            }
            session = null
            pendingLearnTarget = null
            if (transport.isRunning()) {
                transport.pause()
            }
            transport.reset()
            playing = false
            hasStartedGame = false
            resetFeedback()
            headline = "Drum mappings cleared"
        }
        emitState()
    }

    fun currentProfile(): DrumKitProfile? = synchronized(lock) { profile }

    fun currentDifficulty(): WhackDifficulty = synchronized(lock) { difficulty }

    fun setDifficulty(newDifficulty: WhackDifficulty) {
        synchronized(lock) {
            if (difficulty == newDifficulty) return@synchronized
            val wasPlaying = playing
            if (transport.isRunning()) {
                transport.pause()
            }
            difficulty = newDifficulty
            preferences.setWhackDifficulty(newDifficulty)
            session = createSessionAt(transport.positionNs() / 1_000L)
            resetFeedback()
            playing = wasPlaying && session != null
            if (playing) {
                transport.resume()
            }
            headline = if (session == null) {
                "Configure a drum kit before playing"
            } else {
                "${newDifficulty.label} difficulty"
            }
        }
        emitState()
    }

    fun release() {
        if (released) return
        released = true
        running = false
        mainHandler.removeCallbacks(frameRunnable)
        if (transport.isRunning()) {
            transport.pause()
        }
        midiInput.stop()
    }

    private fun createSessionAt(timeUs: Long): WhackGameSession? {
        val currentProfile = profile ?: return null
        if (currentProfile.availableTargets().isEmpty()) return null
        return WhackGameSession(
            profile = currentProfile,
            config = difficulty.config()
        ).also { it.start(timeUs) }
    }

    private fun resetFeedback() {
        lastOutcome = null
        lastStrikeTarget = null
        lastVelocity = null
        lastReactionTimeUs = null
    }

    private fun emitState() {
        val state = synchronized(lock) {
            if (released) return
            val currentTimeUs = transport.positionNs() / 1_000L

            if (playing) {
                val misses = session?.advanceTo(currentTimeUs).orEmpty()
                if (misses.isNotEmpty()) {
                    val latest = misses.last()
                    lastOutcome = StrikeOutcome.MISS
                    lastStrikeTarget = latest.target.target
                    lastVelocity = null
                    lastReactionTimeUs = null
                    headline = "Miss: ${latest.target.target.label}"
                }
            }

            val currentTarget = session?.currentTarget()
            val targetActive = currentTarget != null &&
                currentTimeUs >= currentTarget.appearsAtUs &&
                currentTimeUs <= currentTarget.expiresAtUs
            val summary = session?.scoreSummary()

            val mappedTargets = profile?.availableTargets()?.toSet().orEmpty()
            val readiness = WhackReadiness.derive(
                deviceConnected = deviceConnected,
                mappedTargetCount = mappedTargets.size,
                isPlaying = playing,
                hasStartedGame = hasStartedGame,
                learningTarget = pendingLearnTarget
            )

            WhackUiState(
                readiness = readiness,
                deviceConnected = deviceConnected,
                deviceStatus = deviceStatus,
                profileName = profile?.name,
                mappedTargets = mappedTargets,
                mappedTargetCount = mappedTargets.size,
                target = currentTarget?.target,
                targetActive = targetActive,
                targetRemainingMs = currentTarget
                    ?.takeIf { targetActive }
                    ?.let { ((it.expiresAtUs - currentTimeUs).coerceAtLeast(0L)) / 1_000L },
                difficultyLabel = difficulty.label,
                scorePoints = session?.scorePoints() ?: 0,
                combo = session?.combo() ?: 0,
                maxCombo = session?.maxCombo() ?: 0,
                hitCount = summary?.hitCount ?: 0,
                missCount = summary?.missCount ?: 0,
                wrongStrikeCount = summary?.wrongStrikeCount ?: 0,
                averageReactionTimeMs = summary?.averageReactionTimeUs?.div(1_000L),
                bestReactionTimeMs = summary?.bestReactionTimeUs?.div(1_000L),
                lastOutcome = lastOutcome,
                lastStrikeTarget = lastStrikeTarget,
                lastMidiNote = lastMidiNote,
                lastMidiChannel = lastMidiChannel,
                lastMidiVelocity = lastMidiVelocity,
                lastVelocity = lastVelocity,
                lastReactionTimeMs = lastReactionTimeUs?.div(1_000L),
                learningTarget = pendingLearnTarget,
                headline = headline,
                isPlaying = playing
            )
        }

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            onStateChanged(state)
        } else {
            mainHandler.post { onStateChanged(state) }
        }
    }
}
