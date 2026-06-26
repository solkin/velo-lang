package org.velo.android.engine.ui.view

import android.view.Gravity
import android.widget.LinearLayout

/**
 * Container and sizing operations for a [ViewState]: appending children, inner padding,
 * inter-child spacing, centring, and the fill/size/weight intents that are composed into
 * LayoutParams (here and at [ViewState.add] time).
 */

/** Append [child] to this container. */
internal fun ViewState.add(child: ViewState) {
    val group = content ?: return
    ui {
        val v = child.av ?: return@ui
        val lp = child.layoutParamsFor(group)
        if (gapDp > 0 && group.childCount > 0 && lp is LinearLayout.LayoutParams) {
            if (group is LinearLayout && group.orientation == LinearLayout.VERTICAL) lp.topMargin = px(gapDp)
            else lp.marginStart = px(gapDp)
        }
        v.layoutParams = lp
        group.addView(v)
    }
    children.add(child)
}

/** Inner spacing of this container, in dp. */
internal fun ViewState.padding(dp: Int) {
    val p = px(dp)
    ui { (content ?: av)?.setPadding(p, p, p, p) }
}

/** Spacing between children of this linear container, in dp (applied as child margins on add). */
internal fun ViewState.gap(dp: Int) {
    gapDp = dp
    // A wrapping container carries the gap itself (margins-on-add can't space wrapped lines).
    (content as? FlowLayout)?.let { flow ->
        ui { flow.hgap = px(dp); flow.vgap = px(dp); flow.requestLayout() }
    }
}

/** Center this container's children. */
internal fun ViewState.center() {
    ui { (content as? LinearLayout)?.gravity = Gravity.CENTER }
}

internal fun ViewState.fillWidth() {
    fillW = true
    applyLayoutNow()
}

internal fun ViewState.fillHeight() {
    fillH = true
    applyLayoutNow()
}

internal fun ViewState.width(dp: Int) {
    widthDp = dp
    applyLayoutNow()
}

internal fun ViewState.height(dp: Int) {
    heightDp = dp
    applyLayoutNow()
}

/** Take a share of the parent's free space along its main axis (like Swing weight). */
internal fun ViewState.weight(w: Int) {
    grow = w
    applyLayoutNow()
}
