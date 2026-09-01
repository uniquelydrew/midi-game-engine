package com.example.midigameengine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import core.input.GeneralMidiDrumProfile

/** Lane-based percussion renderer kept separate from the piano keyboard visualizer. */
class DrumPadVisualizerView(context: Context) : View(context) {
    private var state = TeachingUiState.empty()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
    fun submitState(value: TeachingUiState) { state = value; contentDescription = "Drum pads. ${value.headline}"; postInvalidateOnAnimation() }
    override fun onDraw(canvas: Canvas) {
        val targets = GeneralMidiDrumProfile.displayTargets()
        val laneWidth = width.toFloat() / targets.size.coerceAtLeast(1)
        canvas.drawColor(Color.rgb(12, 16, 29))
        targets.forEachIndexed { index, target ->
            val left = index * laneWidth
            paint.color = if (state.physicalHeldPitches.any { it in target.midiPitches }) Color.rgb(39, 122, 128) else Color.rgb(26, 34, 56)
            canvas.drawRect(left + 2, 0f, left + laneWidth - 2, height.toFloat(), paint)
            text.textSize = 24f
            canvas.drawText(target.shortLabel, left + 8, height - 24f, text)
            canvas.drawLine(left, height * .76f, left + laneWidth, height * .76f, Paint(paint).apply { color = Color.YELLOW; strokeWidth = 4f })
        }
        state.notes.filterNot { it.matched }.forEach { note ->
            val target = GeneralMidiDrumProfile.resolveInput(note.pitch) ?: return@forEach
            val x = target.laneIndex * laneWidth
            val deltaSeconds = (note.startTimeUs - state.playbackTimeUs) / 1_000_000f
            val y = height * .76f - deltaSeconds * (height * .18f)
            if (y !in 0f..height.toFloat()) return@forEach
            paint.color = Color.rgb(99, 201, 255)
            canvas.drawCircle(x + laneWidth / 2, y, laneWidth.coerceAtMost(56f) * .25f, paint)
        }
        text.textSize = 34f
        val score = if (state.experienceMode == ExperienceMode.GAME) "GAME  Score ${state.liveScorePoints}  Combo ${state.combo}" else "PRACTICE"
        canvas.drawText(score, 18f, 42f, text)
    }
}
