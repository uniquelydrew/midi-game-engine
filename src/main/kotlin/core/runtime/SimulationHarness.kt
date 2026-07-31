package core.runtime

import core.chart.PlayableChart
import core.judgment.JudgmentEngine
import core.judgment.TimingWindow
import core.midi.NoteOn
import core.time.SystemClock
import core.time.Transport

class SimulationHarness(
    private val chart: PlayableChart
) {

    private val transport = Transport(SystemClock())
    private val judgment = JudgmentEngine(
        TimingWindow(
            perfect = 50_000,
            great = 100_000,
            good = 200_000
        )
    )

    private val session = GameSessionStateful(chart, judgment)

    fun runSimulation(latencyOffsetUs: Long = 0L, jitterUs: Long = 0L) {
        transport.start()

        chart.events.forEach { event ->
            val simulatedTime = event.targetTimeUs + latencyOffsetUs + randomJitter(jitterUs)

            val input = NoteOn(
                timestampUs = simulatedTime,
                pitch = event.pitch,
                velocity = 100,
                channel = 0
            )

            session.onInput(input)
        }

        println("Simulation complete")
    }

    private fun randomJitter(jitter: Long): Long {
        if (jitter == 0L) return 0L
        return (-jitter..jitter).random()
    }
}
