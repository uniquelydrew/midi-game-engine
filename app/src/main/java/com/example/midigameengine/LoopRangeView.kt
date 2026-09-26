package com.example.midigameengine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Compact dual-handle, transport-time loop selector shared by training screens. */
class LoopRangeView(context: Context) : View(context) {
    private val rail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(92, 102, 119) }
    private val selected = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(52, 151, 214) }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 245, 255) }
    private var windowStartUs = 0L
    private var windowEndUs = 0L
    private var loopStartUs: Long? = null
    private var loopEndUs: Long? = null
    private var draggingStart = true

    var onRangeCommitted: ((Long, Long) -> Unit)? = null

    init {
        minimumHeight = (48 * resources.displayMetrics.density).toInt()
        contentDescription = "Practice loop range. Drag the left and right handles to set loop start and end."
    }

    fun submit(windowStart: Long, windowEnd: Long, loopStart: Long?, loopEnd: Long?) {
        windowStartUs = windowStart
        windowEndUs = max(windowEnd, windowStart + 1L)
        loopStartUs = loopStart
        loopEndUs = loopEnd
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val y = height / 2f
        val left = paddingLeft.toFloat() + 12f
        val right = width - paddingRight.toFloat() - 12f
        canvas.drawRoundRect(left, y - 4f, right, y + 4f, 4f, 4f, rail)
        val start = loopStartUs ?: windowStartUs
        val end = loopEndUs ?: windowEndUs
        val startX = xFor(start, left, right)
        val endX = xFor(end, left, right)
        canvas.drawRoundRect(startX, y - 7f, endX, y + 7f, 7f, 7f, selected)
        canvas.drawCircle(startX, y, 10f, handle)
        canvas.drawCircle(endX, y, 10f, handle)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (windowEndUs <= windowStartUs) return false
        val left = paddingLeft.toFloat() + 12f
        val right = width - paddingRight.toFloat() - 12f
        val currentStart = loopStartUs ?: windowStartUs
        val currentEnd = loopEndUs ?: windowEndUs
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggingStart = abs(event.x - xFor(currentStart, left, right)) <=
                    abs(event.x - xFor(currentEnd, left, right))
                return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val selectedTime = timeFor(event.x, left, right)
                val minLengthUs = min(100_000L, (windowEndUs - windowStartUs) / 10L).coerceAtLeast(1L)
                if (draggingStart) {
                    loopStartUs = selectedTime.coerceAtMost(currentEnd - minLengthUs)
                    loopEndUs = currentEnd
                } else {
                    loopStartUs = currentStart
                    loopEndUs = selectedTime.coerceAtLeast(currentStart + minLengthUs)
                }
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    onRangeCommitted?.invoke(loopStartUs!!, loopEndUs!!)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun xFor(timeUs: Long, left: Float, right: Float): Float {
        val fraction = (timeUs - windowStartUs).toFloat() / (windowEndUs - windowStartUs).toFloat()
        return left + (right - left) * fraction.coerceIn(0f, 1f)
    }

    private fun timeFor(x: Float, left: Float, right: Float): Long {
        val fraction = ((x - left) / (right - left)).coerceIn(0f, 1f)
        return windowStartUs + ((windowEndUs - windowStartUs) * fraction).toLong()
    }
}
