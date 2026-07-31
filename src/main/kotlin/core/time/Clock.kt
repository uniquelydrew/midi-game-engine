package core.time

interface Clock {
    fun now(): Long
}

class SystemClock : Clock {
    override fun now(): Long = System.nanoTime()
}
