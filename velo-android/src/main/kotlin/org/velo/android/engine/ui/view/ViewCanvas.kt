package org.velo.android.engine.ui.view

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import core.VeloFunction
import org.velo.android.engine.ui.Shape

/**
 * Canvas drawing and pointer operations for a [ViewState] whose view is a [VeloCanvasView].
 * Each `draw*` appends a [DrawOp] and returns a [Shape] to style it; on a non-canvas view
 * they no-op into an inert shape, like every other kind-specific operation.
 */

/**
 * Build an op on the canvas and return a [Shape] over it. Runs on the Velo worker thread:
 * a [DrawOp] is plain data (geometry already in px, a [Paint] to mutate), so there is no
 * main-thread hop here — [VeloCanvasView.add] guards the list and coalesces the repaint.
 */
private fun ViewState.canvasOp(build: (VeloCanvasView) -> DrawOp): Shape {
    val cv = av as? VeloCanvasView ?: return Shape.make(this, null)
    val op = build(cv)
    cv.add(op)
    return Shape.make(this, op)
}

/** A line from ([x1],[y1]) to ([x2],[y2]); stroked by default. */
internal fun ViewState.drawLine(x1: Int, y1: Int, x2: Int, y2: Int): Shape = canvasOp { cv ->
    val d = cv.density
    val paint = defaultPaint(cv).apply { style = Paint.Style.STROKE; strokeWidth = d }
    DrawOp(paint) { c, p -> c.drawLine(x1 * d, y1 * d, x2 * d, y2 * d, p) }
}

/** A rectangle at ([x],[y]) of size [w]×[h]; filled by default. */
internal fun ViewState.drawRect(x: Int, y: Int, w: Int, h: Int): Shape = canvasOp { cv ->
    val d = cv.density
    DrawOp(defaultPaint(cv)) { c, p -> c.drawRect(x * d, y * d, (x + w) * d, (y + h) * d, p) }
}

/** A rounded rectangle at ([x],[y]) of size [w]×[h] with corner radius [r]. */
internal fun ViewState.drawRoundRect(x: Int, y: Int, w: Int, h: Int, r: Int): Shape = canvasOp { cv ->
    val d = cv.density
    DrawOp(defaultPaint(cv)) { c, p ->
        c.drawRoundRect(x * d, y * d, (x + w) * d, (y + h) * d, r * d, r * d, p)
    }
}

/** A circle centred at ([cx],[cy]) with radius [r]; filled by default. */
internal fun ViewState.drawCircle(cx: Int, cy: Int, r: Int): Shape = canvasOp { cv ->
    val d = cv.density
    DrawOp(defaultPaint(cv)) { c, p -> c.drawCircle(cx * d, cy * d, r * d, p) }
}

/** An ellipse in the box ([x],[y]) of size [w]×[h]; filled by default. */
internal fun ViewState.drawOval(x: Int, y: Int, w: Int, h: Int): Shape = canvasOp { cv ->
    val d = cv.density
    val rect = RectF(x * d, y * d, (x + w) * d, (y + h) * d)
    DrawOp(defaultPaint(cv)) { c, p -> c.drawOval(rect, p) }
}

/** An open arc in box ([x],[y]) size [w]×[h], from [start]° sweeping [sweep]°. */
internal fun ViewState.drawArc(x: Int, y: Int, w: Int, h: Int, start: Int, sweep: Int): Shape = canvasOp { cv ->
    val d = cv.density
    val rect = RectF(x * d, y * d, (x + w) * d, (y + h) * d)
    val paint = defaultPaint(cv).apply { style = Paint.Style.STROKE; strokeWidth = d }
    DrawOp(paint) { c, p -> c.drawArc(rect, start.toFloat(), sweep.toFloat(), false, p) }
}

/** A pie slice (arc closed to the centre) in box ([x],[y]) size [w]×[h]; filled by default. */
internal fun ViewState.drawPie(x: Int, y: Int, w: Int, h: Int, start: Int, sweep: Int): Shape = canvasOp { cv ->
    val d = cv.density
    val rect = RectF(x * d, y * d, (x + w) * d, (y + h) * d)
    DrawOp(defaultPaint(cv)) { c, p -> c.drawArc(rect, start.toFloat(), sweep.toFloat(), true, p) }
}

/** An SVG-style path (`M`/`L`/`Q`/`C`/`Z`, absolute or relative); filled by default. */
internal fun ViewState.drawPath(spec: String): Shape = canvasOp { cv ->
    val path: Path = parseSvgPath(spec, cv.density)
    DrawOp(defaultPaint(cv)) { c, p -> c.drawPath(path, p) }
}

/** A point set `"x,y x,y …"` drawn as dots ("points"), segments ("lines") or a polyline ("polygon"). */
internal fun ViewState.drawPoints(spec: String, mode: String): Shape = canvasOp { cv ->
    val d = cv.density
    val pts = parsePoints(spec, d)
    val paint = defaultPaint(cv).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4 * d
    }
    DrawOp(paint) { c, p ->
        when (mode) {
            "lines" -> if (pts.size >= 4) c.drawLines(pts, p)
            "polygon" -> {
                var k = 0
                while (k + 3 < pts.size) {
                    c.drawLine(pts[k], pts[k + 1], pts[k + 2], pts[k + 3], p)
                    k += 2
                }
            }
            else -> c.drawPoints(pts, p)
        }
    }
}

/** A run of [s] at ([x],[y]) with font size [size] sp; filled by default. */
internal fun ViewState.drawText(x: Int, y: Int, s: String, size: Int): Shape = canvasOp { cv ->
    val d = cv.density
    val paint = defaultPaint(cv).apply { textSize = cv.sp(size) }
    DrawOp(paint) { c, p -> c.drawText(s, x * d, y * d, p) }
}

/** Remove every shape from the canvas. */
internal fun ViewState.clearCanvas() {
    (av as? VeloCanvasView)?.clearOps()
}

/** Fire [cb] with the dp tap point when the canvas is tapped. */
internal fun ViewState.onTap(cb: VeloFunction) {
    retain(cb)
    ui { (av as? VeloCanvasView)?.onTap = { x, y -> cb.post(x, y) } }
}

internal fun ViewState.onPointerDown(cb: VeloFunction) {
    retain(cb)
    ui { (av as? VeloCanvasView)?.onDown = { x, y -> cb.post(x, y) } }
}

internal fun ViewState.onPointerMove(cb: VeloFunction) {
    retain(cb)
    ui { (av as? VeloCanvasView)?.onMove = { x, y -> cb.post(x, y) } }
}

internal fun ViewState.onPointerUp(cb: VeloFunction) {
    retain(cb)
    ui { (av as? VeloCanvasView)?.onUp = { x, y -> cb.post(x, y) } }
}
