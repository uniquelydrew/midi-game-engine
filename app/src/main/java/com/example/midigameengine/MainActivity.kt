package com.example.midigameengine

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import core.chart.ExpectedInput
import core.chart.PlayableChart
import core.judgment.JudgmentEngine
import core.judgment.TimingWindow
import core.midi.NoteOn
import core.runtime.GameSessionStateful

class MainActivity : Activity() {

    private val session = GameSessionStateful(
        PlayableChart(
            listOf(
                ExpectedInput(pitch = 60, targetTimeUs = 1_000_000L),
                ExpectedInput(pitch = 64, targetTimeUs = 1_500_000L)
            )
        ),
        JudgmentEngine(
            TimingWindow(
                perfectUs = 50_000L,
                greatUs = 100_000L,
                goodUs = 200_000L
            )
        )
    )

    private lateinit var statusView: TextView
    private var nextPitchIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 64, 48, 48)
        }

        statusView = TextView(this).apply {
            textSize = 18f
            text = "Android build shell is ready."
        }

        val simulateButton = Button(this).apply {
            text = "Simulate MIDI Note"
            setOnClickListener { simulateNote() }
        }

        container.addView(
            statusView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(simulateButton)

        setContentView(container)
    }

    private fun simulateNote() {
        val scriptedEvents = listOf(
            NoteOn(timestampUs = 1_000_000L, pitch = 60, velocity = 100, channel = 0),
            NoteOn(timestampUs = 1_500_000L, pitch = 64, velocity = 100, channel = 0)
        )

        val event = scriptedEvents[nextPitchIndex % scriptedEvents.size]
        nextPitchIndex++

        val judgment = session.onInput(event)
        statusView.text = buildString {
            append("Pitch ")
            append(event.pitch)
            append(" -> ")
            append(judgment?.name ?: "ignored")
            append("\nCombo: ")
            append(session.getMaxCombo())
            append("\nJudgments: ")
            append(session.getResults().size)
        }
    }
}
