package com.example.midigameengine

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small process-wide diagnostic log that can be exported without adb. */
object AppDebugLogger {
    private const val tag = "MidiGameEngine"
    private const val maxBytes = 2L * 1024L * 1024L
    private val lock = Any()
    private var logFile: File? = null
    private var installed = false
    private var lastTeachingStateLogMs = 0L
    private var lastWhackStateLogMs = 0L
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US)

    fun initialize(context: Context) {
        synchronized(lock) {
            if (logFile == null) {
                logFile = File(context.applicationContext.filesDir, "midi-game-engine-debug.log")
            }
            if (!installed) {
                val previous = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                    log("UNCAUGHT ${thread.name}: ${error.message}", error)
                    previous?.uncaughtException(thread, error)
                }
                installed = true
            }
        }
        log("Logger initialized")
    }

    fun log(message: String, error: Throwable? = null) {
        val line = synchronized(lock) {
            "${format.format(Date())} ${Thread.currentThread().name} $message" +
                (error?.let { "\n${Log.getStackTraceString(it)}" } ?: "") + "\n"
        }
        Log.i(tag, message, error)
        synchronized(lock) {
            val file = logFile ?: return
            runCatching {
                if (file.exists() && file.length() > maxBytes) {
                    val rotated = File(file.parentFile, "midi-game-engine-debug.previous.log")
                    rotated.delete()
                    file.renameTo(rotated)
                }
                file.appendText(line)
            }
        }
    }

    fun logState(state: TeachingUiState) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastTeachingStateLogMs < 1_000L && !state.headline.startsWith("Import")) return
            lastTeachingStateLogMs = now
        }
        log(
            "TEACHING_STATE source=${state.sourceLabel} headline=${state.headline} " +
                "playing=${state.isPlaying} scrubbing=${state.isScrubbing} " +
                "positionUs=${state.playbackTimeUs} range=${state.playbackStartUs}..${state.playbackEndUs} " +
                "combo=${state.combo} device=${state.deviceStatus}"
        )
    }

    fun logState(state: WhackUiState) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastWhackStateLogMs < 1_000L && state.learningTarget == null) return
            lastWhackStateLogMs = now
        }
        log(
            "WHACK_STATE headline=${state.headline} playing=${state.isPlaying} " +
                "device=${state.deviceStatus} profile=${state.profileName ?: "none"} " +
                "difficulty=${state.difficultyLabel} score=${state.scorePoints} " +
                "combo=${state.combo} maxCombo=${state.maxCombo} " +
                "target=${state.target?.name ?: "none"} active=${state.targetActive} " +
                "midiNote=${state.lastMidiNote ?: -1} midiChannel=${state.lastMidiChannel ?: -1} " +
                "midiVelocity=${state.lastMidiVelocity ?: -1} " +
                "hits=${state.hitCount} misses=${state.missCount} wrong=${state.wrongStrikeCount} " +
                "avgMs=${state.averageReactionTimeMs ?: -1} bestMs=${state.bestReactionTimeMs ?: -1} " +
                "learning=${state.learningTarget?.name ?: "none"}"
        )
    }

    fun exportText(
        teachingState: TeachingUiState?,
        whackState: WhackUiState? = null
    ): String {
        val logs = synchronized(lock) { logFile?.takeIf { it.exists() }?.readText().orEmpty() }
        return buildString {
            appendLine("MIDI Game Engine diagnostic export")
            appendLine("Generated: ${format.format(Date())}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
            appendLine("TEACHING STATE")
            appendLine(teachingState?.toString() ?: "Unavailable")
            appendLine()
            appendLine("WHACK-A-MIDI STATE")
            appendLine(whackState?.toString() ?: "Unavailable")
            appendLine()
            appendLine("LOG")
            append(logs.ifBlank { "No log entries captured." })
        }
    }
}
