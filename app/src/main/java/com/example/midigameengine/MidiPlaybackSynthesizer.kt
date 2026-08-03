package com.example.midigameengine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import core.chart.ExpectedInput
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Small local piano-like synth used for chart playback feedback. */
class MidiPlaybackSynthesizer {
    private val sampleRate = 44_100
    private val lock = Object()
    private val voices = mutableListOf<Voice>()
    private var events: List<ExpectedInput> = emptyList()
    private var eventIndex = 0
    private var lastTimeUs = 0L
    private var playbackRate = 1.0
    private var playing = false
    private var released = false
    private val audioTrack: AudioTrack?

    init {
        audioTrack = runCatching {
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate / 5)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also {
                    Thread(::renderLoop, "midi-audio").apply {
                        isDaemon = true
                        start()
                    }
                }
        }.getOrNull()
    }

    fun load(chartEvents: List<ExpectedInput>) {
        synchronized(lock) {
            events = chartEvents.sortedBy { it.targetTimeUs }
            eventIndex = 0
            lastTimeUs = 0L
            voices.clear()
        }
    }

    fun setRate(rate: Double) {
        synchronized(lock) {
            playbackRate = rate.coerceIn(0.25, 2.0)
        }
    }

    fun sync(timeUs: Long, shouldPlay: Boolean) {
        synchronized(lock) {
            if (timeUs < lastTimeUs) {
                eventIndex = 0
                voices.clear()
            }
            addEventsThrough(timeUs)
            voices.removeAll { it.endTimeUs <= timeUs }
            lastTimeUs = timeUs
            playing = shouldPlay
            setAudioPlaying(playing)
            lock.notifyAll()
        }
    }

    fun seek(timeUs: Long, shouldPlay: Boolean) {
        synchronized(lock) {
            eventIndex = 0
            voices.clear()
            lastTimeUs = 0L
            addEventsThrough(timeUs)
            voices.removeAll { it.endTimeUs <= timeUs }
            lastTimeUs = timeUs
            playing = shouldPlay
            resetAudioTrack(playing)
            lock.notifyAll()
        }
    }

    private fun setAudioPlaying(shouldPlay: Boolean) {
        val track = audioTrack ?: return
        if (released || track.state != AudioTrack.STATE_INITIALIZED) return
        runCatching {
            if (shouldPlay) track.play() else track.pause()
        }.onFailure { error ->
            AppDebugLogger.log("AudioTrack state transition failed", error)
        }
    }

    private fun resetAudioTrack(shouldPlay: Boolean) {
        val track = audioTrack ?: return
        if (released || track.state != AudioTrack.STATE_INITIALIZED) return
        runCatching {
            track.pause()
            track.flush()
            if (shouldPlay) track.play()
        }.onFailure { error ->
            AppDebugLogger.log("AudioTrack seek reset failed", error)
        }
    }

    private fun addEventsThrough(timeUs: Long) {
        while (eventIndex < events.size && events[eventIndex].targetTimeUs <= timeUs) {
            val event = events[eventIndex]
            val endTimeUs = event.targetTimeUs + event.durationUs.coerceAtLeast(100_000L)
            if (endTimeUs > timeUs) {
                voices += Voice(
                    pitch = event.pitch,
                    velocity = event.velocity,
                    endTimeUs = endTimeUs,
                    remainingSamples = (
                        event.durationUs.coerceAtLeast(100_000L).toDouble() /
                            playbackRate * sampleRate / 1_000_000.0
                        ).toInt().coerceAtLeast(1)
                )
            }
            eventIndex++
        }
    }

    fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            playing = false
            lock.notifyAll()
        }
        runCatching { audioTrack?.release() }
    }

    private fun renderLoop() {
        val samples = ShortArray(1024)
        while (true) {
            synchronized(lock) {
                while (!released && !playing) lock.wait(50L)
                if (released) return
            }

            val activeVoices = synchronized(lock) { voices.toList() }
            for (index in samples.indices) {
                var value = 0.0
                activeVoices.forEach { voice ->
                    if (voice.remainingSamples > 0) {
                        val frequency = 440.0 * 2.0.pow((voice.pitch - 69) / 12.0)
                        val fundamental = sin(voice.phase)
                        val harmonic = 0.35 * sin(voice.phase * 2.0)
                        value += (fundamental + harmonic) * voice.amplitude
                        voice.phase += 2.0 * PI * frequency / sampleRate
                        if (voice.phase > 2.0 * PI) voice.phase -= 2.0 * PI
                        voice.remainingSamples--
                    }
                }
                samples[index] = (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            runCatching {
                if (audioTrack?.state == AudioTrack.STATE_INITIALIZED && !released) {
                    audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                }
            }.onFailure { error ->
                AppDebugLogger.log("AudioTrack write failed", error)
                synchronized(lock) { playing = false }
            }
        }
    }

    private data class Voice(
        val pitch: Int,
        val velocity: Int,
        val endTimeUs: Long,
        var remainingSamples: Int,
        var phase: Double = 0.0
    ) {
        val amplitude: Double
            get() = (velocity.coerceIn(1, 127) / 127.0) * 0.12
    }
}
