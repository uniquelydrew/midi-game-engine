package core.time

class Transport(
    private val clock: Clock
) {
    private var startTimeNs: Long = 0
    private var pausedAtNs: Long = 0
    private var running = false

    fun start() {
        startTimeNs = clock.now()
        running = true
    }

    fun pause() {
        pausedAtNs = positionNs()
        running = false
    }

    fun resume() {
        startTimeNs = clock.now() - pausedAtNs
        running = true
    }

    fun positionNs(): Long {
        return if (running) clock.now() - startTimeNs else pausedAtNs
    }
}
