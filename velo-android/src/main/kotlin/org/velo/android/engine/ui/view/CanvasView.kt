package org.velo.android.engine.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * The Android view behind a Velo `canvas`: a retained display list of [DrawOp]s rendered
 * in order on every frame, plus pointer dispatch. Programs build the list with the canvas
 * `draw*` methods (each appends an op and returns a [Shape] to style it) and react to
 * touches via `onTap` / `onPointer*`. Coordinates the program gives are in dp; ops bake the
 * dp→px conversion at creation time so [onDraw] stays a plain replay.
 */
internal class VeloCanvasView(context: Context) : View(context) {

    private val ops = ArrayList<DrawOp>()

    // Pointer callbacks (dp coordinates), installed by the canvas onTap/onPointer* ops.
    var onTap: ((Int, Int) -> Unit)? = null
    var onDown: ((Int, Int) -> Unit)? = null
    var onMove: ((Int, Int) -> Unit)? = null
    var onUp: ((Int, Int) -> Unit)? = null

    init {
        // A plain View skips onDraw unless it has a background; we draw our own content.
        setWillNotDraw(false)
    }

    val density: Float get() = resources.displayMetrics.density

    /** Convert an sp font size to px against the current display metrics. */
    fun sp(value: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), resources.displayMetrics)

    fun add(op: DrawOp) {
        ops.add(op)
        invalidate()
    }

    fun clearOps() {
        ops.clear()
        invalidate()
    }

    /** Re-render after a [Shape] mutated an op's paint. */
    fun refresh() = invalidate()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (op in ops) op.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = (event.x / density).toInt()
        val y = (event.y / density).toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown?.invoke(x, y)
            MotionEvent.ACTION_MOVE -> onMove?.invoke(x, y)
            MotionEvent.ACTION_UP -> {
                onUp?.invoke(x, y)
                onTap?.invoke(x, y)
            }
        }
        return true
    }
}

/**
 * One entry in a canvas display list: a [Paint] (mutated by the [Shape] handle the draw
 * call returns) and a [render] closure that draws fixed, already-px geometry with that
 * paint. Keeping geometry in the closure and paint mutable is what lets a `Shape` restyle
 * a shape after it was added without re-specifying its coordinates.
 */
internal class DrawOp(
    val paint: Paint,
    private val render: (Canvas, Paint) -> Unit,
) {
    fun draw(canvas: Canvas) = render(canvas, paint)
}

/** A fresh paint for a shape: antialiased, filled, in the theme's primary colour. */
internal fun defaultPaint(view: View): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = resolveColor(view, "primary")
}

internal fun strokeCapOf(spec: String): Paint.Cap = when (spec) {
    "round" -> Paint.Cap.ROUND
    "square" -> Paint.Cap.SQUARE
    else -> Paint.Cap.BUTT
}

internal fun strokeJoinOf(spec: String): Paint.Join = when (spec) {
    "round" -> Paint.Join.ROUND
    "bevel" -> Paint.Join.BEVEL
    else -> Paint.Join.MITER
}

private val SVG_TOKEN = Regex("[a-zA-Z]|-?\\d*\\.?\\d+")

/**
 * Parse an SVG-style path (`M`/`L`/`Q`/`C`/`Z`, absolute or relative lowercase) into an
 * Android [Path] already scaled to px by [d]. Unsupported commands are skipped rather than
 * failing, matching the lenient spirit of the rest of the UI natives.
 */
internal fun parseSvgPath(spec: String, d: Float): Path {
    val path = Path()
    val toks = SVG_TOKEN.findAll(spec).map { it.value }.toList()
    var i = 0
    var cx = 0f
    var cy = 0f
    var cmd = ""
    fun next(): Float = toks[i++].toFloat() * d
    fun relX(v: Float) = if (cmd.isNotEmpty() && cmd[0].isLowerCase()) cx + v else v
    fun relY(v: Float) = if (cmd.isNotEmpty() && cmd[0].isLowerCase()) cy + v else v
    while (i < toks.size) {
        val t = toks[i]
        if (t.length == 1 && t[0].isLetter()) {
            cmd = t
            i++
            if (cmd.equals("z", ignoreCase = true)) path.close()
            continue
        }
        when (cmd.uppercase()) {
            "M" -> {
                val x = relX(next()); val y = relY(next())
                path.moveTo(x, y); cx = x; cy = y
                // Subsequent coordinate pairs after a moveto are implicit linetos.
                cmd = if (cmd == "m") "l" else "L"
            }
            "L" -> {
                val x = relX(next()); val y = relY(next())
                path.lineTo(x, y); cx = x; cy = y
            }
            "Q" -> {
                val x1 = relX(next()); val y1 = relY(next())
                val x = relX(next()); val y = relY(next())
                path.quadTo(x1, y1, x, y); cx = x; cy = y
            }
            "C" -> {
                val x1 = relX(next()); val y1 = relY(next())
                val x2 = relX(next()); val y2 = relY(next())
                val x = relX(next()); val y = relY(next())
                path.cubicTo(x1, y1, x2, y2, x, y); cx = x; cy = y
            }
            else -> i++ // unknown number with no active command — skip
        }
    }
    return path
}

/** Parse `"x,y x,y …"` into a flat px point array `[x0,y0,x1,y1,…]` scaled by [d]. */
internal fun parsePoints(spec: String, d: Float): FloatArray {
    val nums = ArrayList<Float>()
    for (pair in spec.trim().split(Regex("\\s+"))) {
        if (pair.isBlank()) continue
        val xy = pair.split(",")
        if (xy.size == 2) {
            val x = xy[0].toFloatOrNull() ?: continue
            val y = xy[1].toFloatOrNull() ?: continue
            nums.add(x * d); nums.add(y * d)
        }
    }
    return nums.toFloatArray()
}
