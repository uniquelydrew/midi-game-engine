package android.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import core.midi.ControlChange
import core.midi.MidiEvent
import core.midi.MidiInput
import core.midi.NoteOff
import core.midi.NoteOn
import core.time.Transport

class AndroidMidiInputReal(
    private val context: Context,
    private val transport: Transport
) : MidiInput {

    private var listener: ((MidiEvent) -> Unit)? = null
    private var statusListener: ((String) -> Unit)? = null
    private var deviceInfoListener: ((String) -> Unit)? = null
    private var device: MidiDevice? = null
    private var deviceInfo: MidiDeviceInfo? = null
    private var outputPort: MidiOutputPort? = null
    private var registered = false

    private val midiManager by lazy {
        context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(addedDevice: MidiDeviceInfo) {
            connectToBestAvailableDevice()
        }

        override fun onDeviceRemoved(removedDevice: MidiDeviceInfo) {
            if (deviceInfo?.id == removedDevice.id) {
                disconnect()
            }
            connectToBestAvailableDevice()
        }
    }

    override fun setListener(listener: (MidiEvent) -> Unit) {
        this.listener = listener
    }

    fun setStatusListener(listener: (String) -> Unit) {
        statusListener = listener
    }

    fun setDeviceInfoListener(listener: (String) -> Unit) {
        deviceInfoListener = listener
    }

    override fun start() {
        if (!registered) {
            midiManager.registerDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            registered = true
        }

        connectToBestAvailableDevice()
    }

    override fun stop() {
        disconnect()

        if (registered) {
            midiManager.unregisterDeviceCallback(deviceCallback)
            registered = false
        }

        reportStatus("Disconnected")
    }

    private fun connectToBestAvailableDevice() {
        val devices = midiManager.devices.filter { it.outputPortCount > 0 }
        if (devices.isEmpty()) {
            reportStatus("Waiting for a MIDI input device")
            return
        }

        val selected = devices.maxByOrNull { scoreDevice(it) } ?: return
        if (deviceInfo?.id == selected.id && outputPort != null) {
            return
        }

        disconnect()
        reportStatus("Connecting to ${describeDevice(selected)}")

        midiManager.openDevice(selected, openDeviceCallback@{ openedDevice ->
            val opened = openedDevice ?: run {
                reportStatus("Could not open ${describeDevice(selected)}")
                return@openDeviceCallback
            }
            device = opened
            deviceInfo = selected

            val portNumber = selected.ports
                .firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
                ?.portNumber
                ?: run {
                    reportStatus("${describeDevice(selected)} has no readable MIDI output port")
                    disconnect()
                    return@openDeviceCallback
                }

            val port = opened.openOutputPort(portNumber) ?: run {
                reportStatus("Could not open MIDI port on ${describeDevice(selected)}")
                disconnect()
                return@openDeviceCallback
            }
            outputPort = port

            port.connect(object : MidiReceiver() {
                override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    parseMidi(data, offset, count, timestamp)
                }
            })

            reportStatus("Connected to ${describeDevice(selected)}")
            deviceInfoListener?.invoke(describeDevice(selected))
        }, null)
    }

    private fun disconnect() {
        outputPort?.close()
        outputPort = null
        device?.close()
        device = null
        deviceInfo = null
    }

    private fun reportStatus(message: String) {
        statusListener?.invoke(message)
    }

    private fun describeDevice(info: MidiDeviceInfo): String {
        val props = info.properties
        val name = props.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown device"
        val manufacturer = props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
        return if (manufacturer.isNullOrBlank()) name else "$manufacturer $name"
    }

    /**
     * Prefer direct hardware transports without making assumptions about the
     * attached instrument. Keyboards, drum modules, pad controllers, and other
     * class-compliant MIDI devices are intentionally scored the same by name.
     */
    private fun scoreDevice(info: MidiDeviceInfo): Int {
        val transportScore = when (info.type) {
            MidiDeviceInfo.TYPE_USB -> 300
            MidiDeviceInfo.TYPE_BLUETOOTH -> 200
            MidiDeviceInfo.TYPE_VIRTUAL -> 100
            else -> 0
        }
        return transportScore + info.outputPortCount
    }

    private fun parseMidi(
        data: ByteArray,
        offset: Int,
        count: Int,
        timestampNs: Long
    ) {
        if (count <= 0 || offset !in data.indices) return

        val status = data[offset].toInt() and 0xFF
        val command = status and 0xF0
        val channel = status and 0x0F

        val pitch = data.getOrNull(offset + 1)?.toInt()?.and(0xFF) ?: return
        val velocity = data.getOrNull(offset + 2)?.toInt()?.and(0xFF) ?: 0

        val timeUs = if (timestampNs > 0L) {
            transport.positionAtClockNs(timestampNs) / 1_000L
        } else {
            transport.positionNs() / 1_000L
        }

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
