package core.midi

data class ChannelMidiMessage(
    val status: Int,
    val data1: Int,
    val data2: Int?
) {
    val command: Int
        get() = status and 0xF0

    val channel: Int
        get() = status and 0x0F
}

/**
 * Stateful parser for MIDI 1.0 channel-voice byte streams.
 *
 * Handles:
 * - multiple messages in a single receiver callback
 * - messages split across callbacks
 * - running status
 * - real-time bytes interleaved with channel messages
 *
 * System-common and SysEx messages are intentionally ignored. They clear
 * running status as required so their payload bytes are never misread as
 * channel data.
 */
class MidiByteStreamParser {
    private var runningStatus: Int? = null
    private var activeStatus: Int? = null
    private val pendingData = ArrayList<Int>(2)

    fun feed(
        bytes: ByteArray,
        offset: Int = 0,
        count: Int = bytes.size - offset
    ): List<ChannelMidiMessage> {
        if (count <= 0 || offset !in bytes.indices) return emptyList()

        val end = (offset + count).coerceAtMost(bytes.size)
        val messages = mutableListOf<ChannelMidiMessage>()

        for (index in offset until end) {
            val value = bytes[index].toInt() and 0xFF

            if (value >= 0xF8) {
                // MIDI real-time messages may appear between any two bytes and
                // do not affect channel running status.
                continue
            }

            if (value and 0x80 != 0) {
                if (value in 0x80..0xEF) {
                    runningStatus = value
                    activeStatus = value
                    pendingData.clear()
                } else {
                    // System Common and SysEx cancel running status.
                    runningStatus = null
                    activeStatus = null
                    pendingData.clear()
                }
                continue
            }

            val status = activeStatus ?: runningStatus ?: continue
            val expectedDataBytes = dataLength(status)
            pendingData += value

            if (pendingData.size == expectedDataBytes) {
                messages += ChannelMidiMessage(
                    status = status,
                    data1 = pendingData[0],
                    data2 = pendingData.getOrNull(1)
                )
                pendingData.clear()
                activeStatus = runningStatus
            }
        }

        return messages
    }

    fun reset() {
        runningStatus = null
        activeStatus = null
        pendingData.clear()
    }

    private fun dataLength(status: Int): Int {
        return when (status and 0xF0) {
            0xC0, 0xD0 -> 1
            else -> 2
        }
    }
}
