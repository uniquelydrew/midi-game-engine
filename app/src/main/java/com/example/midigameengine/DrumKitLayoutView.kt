package com.example.midigameengine

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import core.drums.DrumKitLayoutPiece
import core.drums.DrumTarget
import kotlin.math.abs
import kotlin.math.min

/** Shared renderer/editor for the active kit's normalized visual layout. */
class DrumKitLayoutView(context: Context) : View(context) {
    private val atlas = BitmapFactory.decodeResource(resources, resources.getIdentifier("drum_kit_atlas", "drawable", context.packageName))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xffffcc46.toInt() }
    private var pieces: List<DrumKitLayoutPiece> = emptyList()
    var selectedTarget: DrumTarget? = null
    var editable = false
    var mappedTargets: Set<DrumTarget> = emptySet()
    var activeTarget: DrumTarget? = null
    var onSelect: ((DrumTarget) -> Unit)? = null
    var onMove: ((DrumTarget, Float, Float) -> Unit)? = null
    private var dragging: DrumTarget? = null

    fun submit(layout: List<DrumKitLayoutPiece>, mapped: Set<DrumTarget> = emptySet(), active: DrumTarget? = null) {
        pieces = layout; mappedTargets = mapped; activeTarget = active; invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat().coerceAtLeast(1f)
        pieces.filter { it.visible }.forEach { piece ->
            val rect = rectFor(piece, size)
            atlas?.let { bitmap -> canvas.drawBitmap(bitmap, sourceFor(piece.target, bitmap.width, bitmap.height), rect, paint) }
            if (piece.target == selectedTarget || piece.target == activeTarget) canvas.drawRoundRect(rect, 14f, 14f, ring)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editable) return false
        val target = pieces.filter { it.visible }.minByOrNull { piece ->
            val rect = rectFor(piece, min(width, height).toFloat()); abs(rect.centerX() - event.x) + abs(rect.centerY() - event.y)
        }?.target
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragging = target; target?.let { selectedTarget = it; onSelect?.invoke(it); invalidate() }; return target != null }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> dragging?.let { selected ->
                onMove?.invoke(selected, (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f), (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f))
                if (event.actionMasked == MotionEvent.ACTION_UP) dragging = null
                return true
            }
        }
        return true
    }
    private fun rectFor(piece: DrumKitLayoutPiece, size: Float): RectF {
        val scale = when (piece.target) { DrumTarget.KICK -> .25f; DrumTarget.CRASH, DrumTarget.RIDE, DrumTarget.HI_HAT -> .22f; else -> .19f }
        val w = size * scale; val h = w * .68f
        return RectF(piece.xFraction * width - w / 2, piece.yFraction * height - h / 2, piece.xFraction * width + w / 2, piece.yFraction * height + h / 2)
    }
    private fun sourceFor(target: DrumTarget, width: Int, height: Int): Rect {
        val index = when (target) { DrumTarget.CRASH -> 0; DrumTarget.RIDE -> 1; DrumTarget.TOM_1 -> 2; DrumTarget.TOM_2 -> 3; DrumTarget.HI_HAT -> 4; DrumTarget.SNARE -> 5; DrumTarget.FLOOR_TOM -> 6; DrumTarget.KICK -> 7 }
        val col = index % 4; val row = index / 4
        return Rect(col * width / 4, row * height / 2, (col + 1) * width / 4, (row + 1) * height / 2)
    }
}
