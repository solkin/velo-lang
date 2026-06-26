package org.velo.android.engine.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
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

    // The retained display list. Draw ops are plain data, so they are built on the Velo
    // worker thread (no main-thread hop per primitive) and read on the main thread in
    // [onDraw]; every access is guarded by [opsLock]. Mutations request a single coalesced
    // frame via postInvalidateOnAnimation instead of invalidating per op, so a whole render()
    // pass (hundreds of ops) costs one redraw on the next vsync, not one redraw per primitive.
    private val opsLock = Any()
    private val ops = ArrayList<DrawOp>()
    private var stagedOps: ArrayList<DrawOp>? = null
    private var lastStagedMutationMs = 0L
    private var commitScheduled = false

    // Pointer callbacks (dp coordinates), installed by the canvas onTap/onPointer* ops.
    var onTap: ((Int, Int) -> Unit)? = null
    var onDown: ((Int, Int) -> Unit)? = null
    var onMove: ((Int, Int) -> Unit)? = null
    var onUp: ((Int, Int) -> Unit)? = null

    // Optional aspect lock: when both are > 0, onMeasure fits the largest aspectW:aspectH box
    // inside the space the layout offers, so the canvas keeps its proportions as it stretches.
    private var aspectW = 0
    private var aspectH = 0

    init {
        // A plain View skips onDraw unless it has a background; we draw our own content.
        setWillNotDraw(false)
    }

    val density: Float get() = resources.displayMetrics.density

    /** Lock the canvas to a [w]:[h] aspect ratio (0,0 clears it), re-measuring on the next pass. */
    fun setAspect(w: Int, h: Int) {
        aspectW = w
        aspectH = h
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        if (aspectW <= 0 || aspectH <= 0 || wSize <= 0 || hSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // Largest aspectW:aspectH box that fits within the offered width x height.
        var w = wSize
        var h = w * aspectH / aspectW
        if (h > hSize) {
            h = hSize
            w = h * aspectW / aspectH
        }
        setMeasuredDimension(w, h)
    }

    /** Convert an sp font size to px against the current display metrics. */
    fun sp(value: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), resources.displayMetrics)

    fun add(op: DrawOp) {
        var repaintNow = false
        synchronized(opsLock) {
            val staging = stagedOps
            if (staging != null) {
                staging.add(op)
                markStagedMutationLocked()
                scheduleStagedCommitLocked()
            } else {
                ops.add(op)
                repaintNow = true
            }
        }
        if (repaintNow) postInvalidateOnAnimation()
    }

    fun clearOps() {
        synchronized(opsLock) {
            stagedOps = ArrayList()
            markStagedMutationLocked()
            scheduleStagedCommitLocked()
        }
    }

    /** Re-render after a [Shape] mutated an op's paint (coalesced to one frame). */
    fun refresh() {
        var repaintNow = false
        synchronized(opsLock) {
            if (stagedOps != null) {
                markStagedMutationLocked()
                scheduleStagedCommitLocked()
            } else {
                repaintNow = true
            }
        }
        if (repaintNow) postInvalidateOnAnimation()
    }

    private fun markStagedMutationLocked() {
        lastStagedMutationMs = SystemClock.uptimeMillis()
    }

    private fun scheduleStagedCommitLocked() {
        if (commitScheduled) return
        commitScheduled = true
        postDelayed(::commitStagedFrameIfQuiet, STAGED_COMMIT_DELAY_MS)
    }

    private fun commitStagedFrameIfQuiet() {
        var repaintNow = false
        var rescheduleDelay = 0L
        synchronized(opsLock) {
            val staging = stagedOps
            if (staging == null) {
                commitScheduled = false
                return
            }
            val quietForMs = SystemClock.uptimeMillis() - lastStagedMutationMs
            if (quietForMs < STAGED_COMMIT_DELAY_MS) {
                rescheduleDelay = STAGED_COMMIT_DELAY_MS - quietForMs
            } else {
                ops.clear()
                ops.addAll(staging)
                stagedOps = null
                commitScheduled = false
                repaintNow = true
            }
        }
        if (rescheduleDelay > 0) postDelayed(::commitStagedFrameIfQuiet, rescheduleDelay)
        if (repaintNow) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Snapshot the op references under the lock, then rasterise outside it so the worker
        // can keep building the next frame without contending on the whole draw pass.
        val frame = synchronized(opsLock) { ops.toList() }
        for (op in frame) op.draw(canvas)
    }

    /** True once the program has wired any pointer/tap callback — i.e. the canvas wants gestures. */
    private fun wantsGestures(): Boolean =
        onTap != null || onDown != null || onMove != null || onUp != null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = (event.x / density).toInt()
        val y = (event.y / density).toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Claim the gesture from any enclosing scroll view so a vertical drag draws on
                // the canvas instead of scrolling the screen. Only when the canvas actually
                // listens — a passive canvas still lets the page scroll over it. The request
                // propagates up the whole ancestor chain, so it reaches an outer NestedScrollView.
                if (wantsGestures()) parent?.requestDisallowInterceptTouchEvent(true)
                onDown?.invoke(x, y)
            }
            MotionEvent.ACTION_MOVE -> onMove?.invoke(x, y)
            MotionEvent.ACTION_UP -> {
                onUp?.invoke(x, y)
                onTap?.invoke(x, y)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private companion object {
        const val STAGED_COMMIT_DELAY_MS = 4L
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
