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
    private var lastStateLogMs = 0L
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
            if (now - lastStateLogMs < 1_000L && !state.headline.startsWith("Import")) return
            lastStateLogMs = now
        }
        log(
            "STATE source=${state.sourceLabel} headline=${state.headline} " +
                "playing=${state.isPlaying} scrubbing=${state.isScrubbing} " +
                "positionUs=${state.playbackTimeUs} range=${state.playbackStartUs}..${state.playbackEndUs} " +
                "combo=${state.combo} device=${state.deviceStatus}"
        )
    }

    fun exportText(state: TeachingUiState?): String {
        val logs = synchronized(lock) { logFile?.takeIf { it.exists() }?.readText().orEmpty() }
        return buildString {
            appendLine("MIDI Game Engine diagnostic export")
            appendLine("Generated: ${format.format(Date())}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
            appendLine("CURRENT STATE")
            appendLine(state?.toString() ?: "Unavailable")
            appendLine()
            appendLine("LOG")
            append(logs.ifBlank { "No log entries captured." })
        }
    }
}
