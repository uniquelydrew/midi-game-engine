package core.time

class Transport(
    private val clock: Clock
) {
    private var anchorClockNs: Long = 0
    private var anchorPositionNs: Long = 0
    private var running = false
    private var rate = 1.0

    fun start() {
        anchorClockNs = clock.now()
        anchorPositionNs = 0
        running = true
    }

    fun pause() {
        pausedAtNs = positionNs()
        running = false
    }

    fun resume() {
        anchorClockNs = clock.now()
        anchorPositionNs = pausedAtNs
        running = true
    }

    fun reset() {
        anchorClockNs = clock.now()
        anchorPositionNs = 0
        running = false
    }

    fun seekTo(positionUs: Long) {
        anchorClockNs = clock.now()
        anchorPositionNs = positionUs.coerceAtLeast(0L) * 1_000L
    }

    fun setRate(newRate: Double) {
        val position = positionNs()
        rate = newRate.coerceIn(0.25, 2.0)
        anchorClockNs = clock.now()
        anchorPositionNs = position
    }

    fun rate(): Double = rate

    fun positionNs(): Long {
        return if (running) {
            anchorPositionNs + ((clock.now() - anchorClockNs) * rate).toLong()
        } else {
            anchorPositionNs
        }
    }

    fun isRunning(): Boolean = running

    private var pausedAtNs: Long
        get() = anchorPositionNs
        set(value) {
            anchorPositionNs = value
        }
}
