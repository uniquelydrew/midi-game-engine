package com.example.midigameengine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import core.drums.DrumTarget
import core.strike.StrikeOutcome
import kotlin.math.min

class WhackGameView(context: Context) : View(context) {
    private var state = WhackUiState.empty()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(18, 22, 29)
    }
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val padStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.rgb(118, 129, 145)
    }
    private val activeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = Color.rgb(255, 205, 70)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(185, 194, 207)
        textAlign = Paint.Align.CENTER
    }
    private val feedbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun submitState(newState: WhackUiState) {
        state = newState
        contentDescription = buildString {
            append("Whack-a-MIDI. ")
            append(newState.headline)
            append(". ")
            newState.target?.takeIf { newState.targetActive }?.let {
                append("Target ${it.label}. ")
            }
            append("Score ${newState.scorePoints}, combo ${newState.combo}. ")
            append("Hits ${newState.hitCount}, misses ${newState.missCount}, wrong pads ${newState.wrongStrikeCount}.")
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthF = width.toFloat().coerceAtLeast(1f)
        val heightF = height.toFloat().coerceAtLeast(1f)
        val density = resources.displayMetrics.density

        canvas.drawRect(0f, 0f, widthF, heightF, backgroundPaint)

        textPaint.textSize = 24f * density
        canvas.drawText("Whack-a-MIDI", widthF / 2f, 38f * density, textPaint)

        secondaryTextPaint.textSize = 13f * density
        canvas.drawText(state.deviceStatus, widthF / 2f, 61f * density, secondaryTextPaint)

        textPaint.textSize = 16f * density
        canvas.drawText(state.headline, widthF / 2f, 86f * density, textPaint)

        val scoreLine = "Score ${state.scorePoints}   Combo x${state.combo}   ${state.difficultyLabel}"
        textPaint.textSize = 15f * density
        canvas.drawText(scoreLine, widthF / 2f, 111f * density, textPaint)

        val stats = "Hits ${state.hitCount}   Misses ${state.missCount}   Wrong ${state.wrongStrikeCount}"
        secondaryTextPaint.textSize = 13f * density
        canvas.drawText(stats, widthF / 2f, 133f * density, secondaryTextPaint)

        val reaction = buildString {
            state.averageReactionTimeMs?.let { append("Avg ${it}ms") }
            if (isNotEmpty() && state.bestReactionTimeMs != null) append("   ")
            state.bestReactionTimeMs?.let { append("Best ${it}ms") }
            if (isNotEmpty() && state.maxCombo > 0) append("   ")
            if (state.maxCombo > 0) append("Max combo x${state.maxCombo}")
        }
        if (reaction.isNotEmpty()) {
            canvas.drawText(reaction, widthF / 2f, 153f * density, secondaryTextPaint)
        }

        val kitTop = 170f * density
        val kitBottom = heightF - 24f * density
        val kitHeight = (kitBottom - kitTop).coerceAtLeast(1f)
        val pads = buildPadRects(widthF, kitTop, kitHeight)

        pads.forEach { (target, rect) ->
            val mapped = target in state.mappedTargets
            val active = state.targetActive && state.target == target
            val last = state.lastStrikeTarget == target

            padPaint.color = when {
                active -> Color.rgb(255, 181, 46)
                last && state.lastOutcome == StrikeOutcome.HIT -> Color.rgb(65, 181, 111)
                last && state.lastOutcome == StrikeOutcome.WRONG_TARGET -> Color.rgb(202, 75, 78)
                last && state.lastOutcome == StrikeOutcome.MISS -> Color.rgb(135, 68, 72)
                mapped -> Color.rgb(61, 72, 89)
                else -> Color.rgb(34, 39, 49)
            }

            canvas.drawOval(rect, padPaint)
            canvas.drawOval(rect, padStrokePaint)
            if (active) {
                val inset = 7f * density
                canvas.drawOval(
                    RectF(
                        rect.left - inset,
                        rect.top - inset,
                        rect.right + inset,
                        rect.bottom + inset
                    ),
                    activeStrokePaint
                )
            }

            textPaint.textSize = 12f * density
            textPaint.color = if (mapped || active) Color.WHITE else Color.rgb(110, 117, 128)
            canvas.drawText(
                target.label,
                rect.centerX(),
                rect.centerY() + 4f * density,
                textPaint
            )
        }

        state.targetRemainingMs?.let { remaining ->
            textPaint.textSize = 18f * density
            textPaint.color = Color.WHITE
            canvas.drawText(
                "${remaining}ms",
                widthF / 2f,
                kitBottom - 4f * density,
                textPaint
            )
        }

        state.learningTarget?.let { target ->
            feedbackPaint.color = Color.rgb(89, 213, 255)
            feedbackPaint.textSize = 18f * density
            canvas.drawText(
                "Hit ${target.label} to map it",
                widthF / 2f,
                heightF - 8f * density,
                feedbackPaint
            )
        }
    }

    private fun buildPadRects(
        widthF: Float,
        top: Float,
        heightF: Float
    ): Map<DrumTarget, RectF> {
        val unit = min(widthF, heightF)
        val cymbalW = unit * 0.25f
        val cymbalH = unit * 0.11f
        val drumW = unit * 0.22f
        val drumH = unit * 0.16f

        fun centered(cx: Float, cy: Float, w: Float, h: Float): RectF =
            RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)

        return mapOf(
            DrumTarget.CRASH to centered(widthF * 0.20f, top + heightF * 0.12f, cymbalW, cymbalH),
            DrumTarget.RIDE to centered(widthF * 0.80f, top + heightF * 0.12f, cymbalW, cymbalH),
            DrumTarget.TOM_1 to centered(widthF * 0.40f, top + heightF * 0.32f, drumW, drumH),
            DrumTarget.TOM_2 to centered(widthF * 0.60f, top + heightF * 0.32f, drumW, drumH),
            DrumTarget.HI_HAT to centered(widthF * 0.16f, top + heightF * 0.52f, cymbalW, cymbalH),
            DrumTarget.SNARE to centered(widthF * 0.38f, top + heightF * 0.57f, drumW, drumH),
            DrumTarget.FLOOR_TOM to centered(widthF * 0.72f, top + heightF * 0.58f, drumW, drumH),
            DrumTarget.KICK to centered(widthF * 0.52f, top + heightF * 0.80f, drumW * 1.08f, drumH * 1.15f)
        )
    }
}
