package android.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import core.midi.*
import core.time.Transport

class AndroidMidiInputReal(
    private val context: Context,
    private val transport: Transport
) : MidiInput {

    private var listener: ((MidiEvent) -> Unit)? = null
    private var device: MidiDevice? = null
    private var outputPort: MidiOutputPort? = null

    override fun setListener(listener: (MidiEvent) -> Unit) {
        this.listener = listener
    }

    override fun start() {
        val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager

        val devices = midiManager.devices
        if (devices.isEmpty()) return

        val info = devices.first()

        midiManager.openDevice(info, openDeviceCallback@{ openedDevice ->
            val opened = openedDevice ?: return@openDeviceCallback
            device = opened

            val port = opened.openOutputPort(0) ?: return@openDeviceCallback
            outputPort = port

            port.connect(object : MidiReceiver() {
                override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    parseMidi(data, offset, count)
                }
            })
        }, null)
    }

    override fun stop() {
        outputPort?.close()
        outputPort = null
        device?.close()
        device = null
    }

    private fun parseMidi(data: ByteArray, offset: Int, count: Int) {
        val status = data[offset].toInt() and 0xFF
        val command = status and 0xF0
        val channel = status and 0x0F

        val pitch = data.getOrNull(offset + 1)?.toInt()?.and(0xFF) ?: return
        val velocity = data.getOrNull(offset + 2)?.toInt()?.and(0xFF) ?: 0

        val timeUs = transport.positionNs() / 1000

        val event: MidiEvent? = when (command) {
            0x90 -> if (velocity > 0) {
                NoteOn(timeUs, pitch, velocity, channel)
            } else {
                NoteOff(timeUs, pitch, channel)
            }
            0x80 -> NoteOff(timeUs, pitch, channel)
            0xB0 -> ControlChange(timeUs, pitch, velocity, channel)
            else -> null
        }

        event?.let { listener?.invoke(it) }
    }
}
