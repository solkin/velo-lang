package org.velo.android.engine.ui

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import org.velo.android.engine.ui.view.DrawOp
import org.velo.android.engine.ui.view.ViewState
import org.velo.android.engine.ui.view.VeloCanvasView
import org.velo.android.engine.ui.view.resolveColor
import org.velo.android.engine.ui.view.strokeCapOf
import org.velo.android.engine.ui.view.strokeJoinOf

/**
 * A handle to one drawing primitive on a `canvas`, returned by the canvas `draw*` methods —
 * registered as the Velo native `Shape`.
 *
 * Shapes are deliberately a separate native type from [VeloView]: a shape is never added to
 * a layout container (only ever drawn onto a canvas), so it doesn't need to share `View`'s
 * type, and keeping it apart stops the canvas paint API from crowding every widget handle.
 * Each method mutates the underlying [DrawOp]'s paint on the worker thread and requests a
 * coalesced re-render, so styling can be chained fluently after the shape is drawn.
 *
 * Programs never construct this directly — the public no-arg constructor exists only for the
 * native binder; real instances come from a canvas via [make].
 */
class Shape {

    private var state: ViewState? = null
    private var op: DrawOp? = null

    /** Paint the shape with a Velo color spec (hex or Material role token). */
    fun color(spec: String): Shape = apply { mutate { paint, view -> paint.color = resolveColor(view, spec) } }

    /** Fill the shape's interior (the default for everything but lines and points). */
    fun fill(): Shape = apply { mutate { paint, _ -> paint.style = Paint.Style.FILL } }

    /** Outline the shape with a [widthDp]-thick stroke instead of filling it. */
    fun stroke(widthDp: Int): Shape = apply {
        mutate { paint, view ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = widthDp * view.resources.displayMetrics.density
        }
    }

    /** Set opacity as a percentage 0..100. */
    fun alpha(percent: Int): Shape = apply {
        mutate { paint, _ -> paint.alpha = (percent.coerceIn(0, 100) * 255) / 100 }
    }

    /** Stroke cap style: "butt", "round" or "square". */
    fun cap(spec: String): Shape = apply { mutate { paint, _ -> paint.strokeCap = strokeCapOf(spec) } }

    /** Stroke join style: "miter", "round" or "bevel". */
    fun join(spec: String): Shape = apply { mutate { paint, _ -> paint.strokeJoin = strokeJoinOf(spec) } }

    /** Fill with a two-stop linear gradient from ([x0],[y0]) to ([x1],[y1]) in canvas dp. */
    fun gradient(x0: Int, y0: Int, x1: Int, y1: Int, from: String, to: String): Shape = apply {
        mutate { paint, view ->
            val d = view.resources.displayMetrics.density
            paint.shader = LinearGradient(
                x0 * d, y0 * d, x1 * d, y1 * d,
                resolveColor(view, from), resolveColor(view, to),
                Shader.TileMode.CLAMP,
            )
        }
    }

    private fun mutate(block: (Paint, View) -> Unit) {
        val o = op ?: return
        val v = state?.av ?: return
        // Runs on the Velo worker thread: the paint is the op's own, mutated before the
        // frame's coalesced repaint fires, so no main-thread hop is needed to restyle.
        block(o.paint, v)
        (v as? VeloCanvasView)?.refresh()
    }

    @JvmSynthetic
    internal fun bind(state: ViewState, op: DrawOp?): Shape = apply {
        this.state = state
        this.op = op
    }

    companion object {
        /** A shape handle over [op] on the canvas owning [state]; [op] is null for an inert shape. */
        @JvmSynthetic
        internal fun make(state: ViewState, op: DrawOp?): Shape = Shape().bind(state, op)
    }
}
