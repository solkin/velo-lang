package org.velo.android.engine.ui.view

import com.google.android.material.card.MaterialCardView

/**
 * Surface/card operations for a [ViewState]: shadow elevation and an outline border. The
 * background colour of a surface is handled by [background], which routes to the card's own
 * background setter.
 */

/** Raise this surface with a [dp]-deep shadow. */
internal fun ViewState.elevation(dp: Int) {
    ui { (av as? MaterialCardView)?.cardElevation = px(dp).toFloat() }
}

/** Outline this surface with a [width]-dp border in the given Velo color spec. */
internal fun ViewState.border(width: Int, color: String) {
    ui {
        (av as? MaterialCardView)?.let {
            it.strokeWidth = px(width)
            it.setStrokeColor(resolveColor(it, color))
        }
    }
}
