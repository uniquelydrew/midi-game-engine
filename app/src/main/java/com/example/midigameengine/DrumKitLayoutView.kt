package com.example.midigameengine

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import core.drums.DrumKitLayoutPiece
import core.drums.DrumTarget
import kotlin.math.abs
import kotlin.math.min

/** Shared renderer/editor for the active kit's normalized visual layout. */
class DrumKitLayoutView(context: Context) : View(context) {
    private val componentImages = DrumTarget.values().associateWith { target ->
        BitmapFactory.decodeResource(resources, resources.getIdentifier(resourceName(target), "drawable", context.packageName))
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xffffcc46.toInt() }
    private var pieces: List<DrumKitLayoutPiece> = emptyList()
    var selectedTarget: DrumTarget? = null
    var editable = false
    var mappedTargets: Set<DrumTarget> = emptySet()
    var activeTarget: DrumTarget? = null
    var onSelect: ((DrumTarget) -> Unit)? = null
    var onMove: ((DrumTarget, Float, Float) -> Unit)? = null
    var onResize: ((DrumTarget, Float) -> Unit)? = null
    private var dragging: DrumTarget? = null
    private var scaling: DrumTarget? = null
    private var currentGestureScale = 1f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scaling = nearestTarget(detector.focusX, detector.focusY)
            currentGestureScale = pieces.firstOrNull { it.target == scaling }?.scale ?: 1f
            return scaling != null
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaling?.let { target ->
                currentGestureScale = (currentGestureScale * detector.scaleFactor).coerceIn(DrumKitLayoutPiece.MIN_SCALE, DrumKitLayoutPiece.MAX_SCALE)
                onResize?.invoke(target, currentGestureScale)
            }
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { scaling = null }
    })

    fun submit(layout: List<DrumKitLayoutPiece>, mapped: Set<DrumTarget> = emptySet(), active: DrumTarget? = null) {
        pieces = layout; mappedTargets = mapped; activeTarget = active; invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat().coerceAtLeast(1f)
        val visiblePieces = pieces.filter { it.visible }
        val elevated = visiblePieces.firstOrNull { it.target == selectedTarget || it.target == activeTarget }
        // The selected piece is deliberately last so overlapping components remain selectable and visible.
        (visiblePieces.filterNot { it == elevated }.sortedBy { it.zIndex } + listOfNotNull(elevated)).forEach { piece ->
            val rect = rectFor(piece, size)
            componentImages[piece.target]?.let { bitmap -> canvas.drawBitmap(bitmap, null, rect, paint) }
            if (piece.target == selectedTarget || piece.target == activeTarget) canvas.drawRoundRect(rect, 14f, 14f, ring)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editable) return false
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress || event.pointerCount > 1) return true
        val target = nearestTarget(event.x, event.y)
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
        val base = when (piece.target) { DrumTarget.KICK -> .28f; DrumTarget.CRASH, DrumTarget.RIDE, DrumTarget.HI_HAT -> .25f; else -> .23f }
        val w = size * base * piece.scale
        // Atlas cells are square. Keeping the destination square prevents non-uniform scaling.
        val h = w
        return RectF(piece.xFraction * width - w / 2, piece.yFraction * height - h / 2, piece.xFraction * width + w / 2, piece.yFraction * height + h / 2)
    }
    private fun nearestTarget(x: Float, y: Float): DrumTarget? {
        val visible = pieces.filter { it.visible }
        val candidates = visible.filter { rectFor(it, min(width, height).toFloat()).contains(x, y) }
        return (if (candidates.isNotEmpty()) candidates else visible)
            .sortedWith(compareByDescending<DrumKitLayoutPiece> { it.zIndex }.thenBy { piece ->
                val rect = rectFor(piece, min(width, height).toFloat())
                abs(rect.centerX() - x) + abs(rect.centerY() - y)
            })
            .firstOrNull()?.target
    }
    private fun resourceName(target: DrumTarget): String = when (target) {
        DrumTarget.CRASH -> "drum_crash"; DrumTarget.RIDE -> "drum_ride"
        DrumTarget.TOM_1, DrumTarget.TOM_2 -> "drum_tom"
        DrumTarget.HI_HAT -> "drum_hihat"; DrumTarget.SNARE -> "drum_snare"
        DrumTarget.FLOOR_TOM -> "drum_floor_tom"; DrumTarget.KICK -> "drum_kick"
    }
}
