package org.velo.android.engine.ui.view

import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import com.google.android.material.card.MaterialCardView

/**
 * Common visual modifiers usable on any [ViewState], regardless of kind: background fill,
 * rounded corners, asymmetric padding and child alignment. These mirror the modifier set
 * the NanoVM components expose; they compose with the kind-specific operations elsewhere.
 */

/** Fill this view's background with a Velo color spec (hex or Material role token). */
internal fun ViewState.background(spec: String) {
    ui {
        val v = av ?: return@ui
        // A card/surface paints through its own background setter, not the view's.
        if (v is MaterialCardView) v.setCardBackgroundColor(resolveColor(v, spec))
        else v.setBackgroundColor(resolveColor(v, spec))
    }
}

/** Round this view's corners to [dp], clipping its content to match. */
internal fun ViewState.corner(dp: Int) {
    val r = px(dp).toFloat()
    ui {
        val v = av ?: return@ui
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        v.clipToOutline = true
    }
}

/** Inner padding with separate horizontal [h] and vertical [v] amounts, in dp. */
internal fun ViewState.paddingXY(h: Int, v: Int) {
    val ph = px(h)
    val pv = px(v)
    ui { (content ?: av)?.setPadding(ph, pv, ph, pv) }
}

/** Align this container's children: start / center / end / top / bottom (or combos like "topEnd"). */
internal fun ViewState.align(spec: String) {
    val g = gravityOf(spec)
    ui { (content as? LinearLayout)?.gravity = g }
}

private fun gravityOf(spec: String): Int = when (spec) {
    "start" -> Gravity.START
    "end" -> Gravity.END
    "center" -> Gravity.CENTER
    "top" -> Gravity.TOP
    "bottom" -> Gravity.BOTTOM
    "centerHorizontal" -> Gravity.CENTER_HORIZONTAL
    "centerVertical" -> Gravity.CENTER_VERTICAL
    "topStart" -> Gravity.TOP or Gravity.START
    "topCenter" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
    "topEnd" -> Gravity.TOP or Gravity.END
    "bottomStart" -> Gravity.BOTTOM or Gravity.START
    "bottomCenter" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    "bottomEnd" -> Gravity.BOTTOM or Gravity.END
    else -> Gravity.START
}
