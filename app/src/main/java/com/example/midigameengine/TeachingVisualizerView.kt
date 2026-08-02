package com.example.midigameengine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import core.visualization.KeyboardLayout
import core.visualization.NoteTimelineMath
import core.visualization.PitchNames
import core.visualization.PitchRange

class TeachingVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var state: TeachingUiState = TeachingUiState.empty()
    private val laneBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(80, 255, 255, 255)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * resources.displayMetrics.density
    }
    private val textPaintDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 220, 220, 220)
        textSize = 13f * resources.displayMetrics.density
    }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(120, 255, 255, 255)
    }
    private val activeOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nextOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matchedOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.rgb(255, 203, 70)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 200, 255)
    }
    private val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
    }
    private val keyboardBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(17, 17, 17)
    }
    private val whiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(242, 242, 242)
    }
    private val blackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(16, 16, 16)
    }
    private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val physicalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 66, 225, 255)
    }
    private val physicalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.rgb(66, 225, 255)
    }
    private val feedbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val outOfRangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 170, 80)
        strokeWidth = 3f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    fun submitState(newState: TeachingUiState) {
        state = newState
        contentDescription = "MIDI visualizer. ${newState.headline}. " +
            "Next notes: ${newState.nextExpectedNotes.take(3).joinToString { PitchNames.name(it.pitch) }}. " +
            "Combo ${newState.combo}."
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthF = width.toFloat().coerceAtLeast(1f)
        val heightF = height.toFloat().coerceAtLeast(1f)
        val density = resources.displayMetrics.density
        val padding = 20f * density
        val keyboardHeight = if (widthF > heightF) {
            (96f * density * zoomScale()).coerceAtMost(heightF * 0.30f)
        } else {
            (128f * density * zoomScale()).coerceAtMost(heightF * 0.28f)
        }.coerceAtLeast(64f * density)
        val keyboardTop = heightF - keyboardHeight - padding
        val laneTop = padding * 2.2f
        val pixelsPerSecond = (keyboardTop - laneTop).coerceAtLeast(1f) / 4f
        val currentTimeUs = state.playbackTimeUs
        val nextPitches = state.nextExpectedNotes.map { it.pitch }.toSet()
        val activePitches = state.activeNotes.map { it.pitch }.toSet()
        val visibleRange = PitchRange(state.visibleRangeFirstPitch, state.visibleRangeLastPitch)
        val physicalPitches = state.physicalHeldPitches
        val feedbackPitch = state.lastInputPitch
            ?.takeIf { currentTimeUs <= state.inputFeedbackUntilUs }
        val feedbackProgress = if (feedbackPitch == null) {
            1f
        } else {
            1f - ((state.inputFeedbackUntilUs - currentTimeUs) / 450_000f).coerceIn(0f, 1f)
        }

        laneBackgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            keyboardTop,
            Color.rgb(18, 22, 34),
            Color.rgb(26, 28, 40),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, widthF, keyboardTop, laneBackgroundPaint)
        canvas.drawRect(0f, keyboardTop, widthF, heightF, keyboardBackgroundPaint)

        drawHeader(canvas, padding, laneTop - (10f * density), widthF - (padding * 2f))
        drawProgress(canvas, padding, keyboardTop - (8f * density), widthF - padding * 2f)

        state.notes.forEach { note ->
            if (!KeyboardLayout.contains(note.pitch, visibleRange)) {
                drawOutOfRangeNote(canvas, note, currentTimeUs, keyboardTop, pixelsPerSecond, widthF, laneTop)
                return@forEach
            }
            val geometry = NoteTimelineMath.projectNote(
                pitch = note.pitch,
                startTimeUs = note.startTimeUs,
                durationUs = note.durationUs.coerceAtLeast(1L),
                currentTimeUs = currentTimeUs,
                keyboardTopPx = keyboardTop,
                pixelsPerSecond = pixelsPerSecond,
                totalWidthPx = widthF,
                visibleRange = visibleRange
            )
            val rect = geometry.second
            if (rect.bottom < laneTop || rect.top > heightF) return@forEach

            val isNext = state.nextExpectedNotes.any { it.sameNoteAs(note) }
            val isActive = state.activeNotes.any { it.sameNoteAs(note) }
            val isMatched = note.matched

            barPaint.color = when {
                isActive -> Color.rgb(66, 225, 255)
                isNext -> Color.rgb(255, 207, 85)
                isMatched -> Color.rgb(103, 190, 131)
                else -> Color.rgb(89, 125, 255)
            }
            barPaint.alpha = when {
                isActive -> 245
                isNext -> 235
                isMatched -> 180
                else -> 150
            }

            val barRect = RectF(
                rect.left + 1f,
                rect.top,
                rect.right - 1f,
                rect.bottom
            )
            canvas.drawRoundRect(barRect, 12f, 12f, barPaint)
            canvas.drawRoundRect(barRect, 12f, 12f, keyStrokePaint)
            if (barRect.height() >= 24f * density) {
                textPaint.textSize = 12f * density
                textPaint.color = Color.WHITE
                canvas.drawText(PitchNames.name(note.pitch), barRect.left + 6f, barRect.centerY() + 4f, textPaint)
            }
        }

        drawKeyboard(
            canvas,
            widthF,
            keyboardTop,
            heightF,
            nextPitches,
            activePitches,
            physicalPitches,
            feedbackPitch,
            state.lastInputCorrect,
            feedbackProgress,
            currentTimeUs
        )
    }

    private fun zoomScale(): Float = when (state.keyboardZoomLabel) {
        "Compact" -> 0.75f
        "Large" -> 1.35f
        else -> 1.0f
    }

    private fun drawHeader(canvas: Canvas, left: Float, top: Float, maxWidth: Float) {
        val header1 = state.sourceLabel
        val header2 = "${state.deviceStatus} | ${state.headline}"
        val header3 = "${state.trackSummary} | ${state.physicalProfileLabel} | Zoom ${state.keyboardZoomLabel}"
        val header4 = "Visible ${PitchNames.name(state.visibleRangeFirstPitch)}-${PitchNames.name(state.visibleRangeLastPitch)}  Combo ${state.combo}  Progress ${(state.progress * 100f).toInt()}%"

        canvas.drawText(fitText(header1, textPaint, maxWidth), left, top, textPaint)
        canvas.drawText(fitText(header2, textPaintDim, maxWidth), left, top + 22f * resources.displayMetrics.density, textPaintDim)
        canvas.drawText(fitText(header3, textPaintDim, maxWidth), left, top + 42f * resources.displayMetrics.density, textPaintDim)
        canvas.drawText(fitText(header4, textPaintDim, maxWidth), left, top + 62f * resources.displayMetrics.density, textPaintDim)
    }

    private fun fitText(value: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(value) <= maxWidth) return value
        val suffix = "..."
        var end = value.length
        while (end > 0 && paint.measureText(value.substring(0, end) + suffix) > maxWidth) end--
        return value.substring(0, end.coerceAtLeast(1)) + suffix
    }

    private fun drawOutOfRangeNote(
        canvas: Canvas,
        note: TeachingNoteState,
        currentTimeUs: Long,
        keyboardTop: Float,
        pixelsPerSecond: Float,
        widthF: Float,
        laneTop: Float
    ) {
        val deltaStartSeconds = (note.startTimeUs - currentTimeUs) / 1_000_000f
        val deltaDurationSeconds = note.durationUs.coerceAtLeast(1L) / 1_000_000f
        val bottom = keyboardTop - (deltaStartSeconds * pixelsPerSecond)
        val top = bottom - (deltaDurationSeconds * pixelsPerSecond)
        if (bottom < laneTop || top > keyboardTop) return
        val x = if (note.pitch < state.visibleRangeFirstPitch) 8f else widthF - 8f
        canvas.drawLine(x, top, x, bottom, outOfRangePaint)
        if (bottom - top >= 24f * resources.displayMetrics.density) {
            textPaintDim.color = Color.rgb(255, 190, 110)
            textPaintDim.textSize = 10f * resources.displayMetrics.density
            val label = "${PitchNames.name(note.pitch)} outside view"
            val labelX = if (x < widthF / 2f) 14f else widthF - textPaintDim.measureText(label) - 14f
            canvas.drawText(label, labelX, top + 14f * resources.displayMetrics.density, textPaintDim)
        }
    }

    private fun drawProgress(canvas: Canvas, left: Float, top: Float, widthF: Float) {
        canvas.drawRoundRect(RectF(left, top, left + widthF, top + 8f), 4f, 4f, progressBgPaint)
        canvas.drawRoundRect(RectF(left, top, left + widthF * state.progress.coerceIn(0f, 1f), top + 8f), 4f, 4f, progressPaint)
    }

    private fun drawKeyboard(
        canvas: Canvas,
        widthF: Float,
        keyboardTop: Float,
        heightF: Float,
        nextPitches: Set<Int>,
        activePitches: Set<Int>,
        physicalPitches: Set<Int>,
        feedbackPitch: Int?,
        feedbackCorrect: Boolean?,
        feedbackProgress: Float,
        currentTimeUs: Long
    ) {
        val visibleRange = PitchRange(state.visibleRangeFirstPitch, state.visibleRangeLastPitch)
        val whiteKeys = KeyboardLayout.allKeys(visibleRange, widthF).filterNot { it.black }
        val blackKeys = KeyboardLayout.allKeys(visibleRange, widthF).filter { it.black }

        whiteKeys.forEach { key ->
            val rect = RectF(key.left, keyboardTop, key.left + key.width, heightF)
            canvas.drawRect(rect, whiteKeyPaint)
            canvas.drawRect(rect, keyBorderPaint)
            if (key.pitch in activePitches || key.pitch in nextPitches) {
                val expectedPulse = if (key.pitch in nextPitches) {
                    (0.5f + 0.5f * kotlin.math.sin(currentTimeUs / 160_000.0)).toFloat()
                } else {
                    1f
                }
                highlightFillPaint.color = if (key.pitch in activePitches) {
                        Color.argb(135, 66, 225, 255)
                    } else {
                        Color.argb((75 + expectedPulse * 75).toInt(), 255, 207, 85)
                    }
                canvas.drawRect(rect, highlightFillPaint)
                canvas.drawRect(rect, highlightStrokePaint)
            }
            if (key.pitch in physicalPitches) {
                physicalFillPaint.alpha = 145
                canvas.drawRect(rect, physicalFillPaint)
                canvas.drawRect(rect, physicalStrokePaint)
            }
            if (key.pitch == feedbackPitch) {
                val color = if (feedbackCorrect == true) Color.rgb(90, 230, 145) else Color.rgb(255, 95, 100)
                val inset = 3f + 14f * feedbackProgress
                feedbackPaint.alpha = (255f * (1f - feedbackProgress)).toInt()
                feedbackPaint.color = color
                canvas.drawRect(RectF(rect.left - inset, rect.top - inset, rect.right + inset, rect.bottom + inset), feedbackPaint)
            }
            if (key.pitch % 12 == 0 || key.pitch == visibleRange.firstPitch || key.pitch == visibleRange.lastPitch) {
                textPaintDim.textSize = 10f * resources.displayMetrics.density
                textPaintDim.color = Color.rgb(45, 45, 45)
                canvas.drawText(PitchNames.name(key.pitch), rect.left + 2f, heightF - 8f * resources.displayMetrics.density, textPaintDim)
            }
        }

        blackKeys.forEach { key ->
            val rect = RectF(key.left, keyboardTop, key.left + key.width, heightF - (24f * resources.displayMetrics.density))
            canvas.drawRoundRect(rect, 6f, 6f, blackKeyPaint)
            canvas.drawRoundRect(rect, 6f, 6f, keyBorderPaint)
            if (key.pitch in activePitches || key.pitch in nextPitches) {
                highlightFillPaint.color = if (key.pitch in activePitches) Color.argb(150, 66, 225, 255) else Color.argb(135, 255, 207, 85)
                canvas.drawRoundRect(rect, 6f, 6f, highlightFillPaint)
                canvas.drawRoundRect(rect, 6f, 6f, highlightStrokePaint)
            }
            if (key.pitch in physicalPitches) {
                physicalFillPaint.alpha = 170
                canvas.drawRoundRect(rect, 6f, 6f, physicalFillPaint)
                canvas.drawRoundRect(rect, 6f, 6f, physicalStrokePaint)
            }
            if (key.pitch == feedbackPitch) {
                val color = if (feedbackCorrect == true) Color.rgb(90, 230, 145) else Color.rgb(255, 95, 100)
                val inset = 2f + 10f * feedbackProgress
                feedbackPaint.alpha = (255f * (1f - feedbackProgress)).toInt()
                feedbackPaint.color = color
                canvas.drawRoundRect(RectF(rect.left - inset, rect.top - inset, rect.right + inset, rect.bottom + inset), 8f, 8f, feedbackPaint)
            }
            if (key.pitch in nextPitches || key.pitch in activePitches) {
                textPaintDim.textSize = 9f * resources.displayMetrics.density
                textPaintDim.color = Color.WHITE
                canvas.drawText(PitchNames.name(key.pitch), rect.left + 1f, rect.top + 18f * resources.displayMetrics.density, textPaintDim)
            }
        }
    }

    private fun TeachingNoteState.sameNoteAs(other: TeachingNoteState): Boolean =
        pitch == other.pitch &&
            startTimeUs == other.startTimeUs &&
            durationUs == other.durationUs
}
