package org.velo.android.engine.ui.view

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * A wrapping linear container — the View-world equivalent of Compose `FlowRow`/`FlowColumn`,
 * which AndroidX/Material don't ship out of the box. Children flow along the main axis and
 * wrap onto a new line when they run out of room. [hgap] spaces items horizontally and [vgap]
 * vertically; which one separates items vs lines depends on [vertical]. Kept dependency-free
 * (no flexbox artifact) so the demo's build stays self-contained.
 */
internal class FlowLayout(context: Context, private val vertical: Boolean) : ViewGroup(context) {

    var hgap = 0
    var vgap = 0

    private fun laidOutChildren() = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)

        val childWSpec = MeasureSpec.makeMeasureSpec(
            wSize, if (wMode == MeasureSpec.UNSPECIFIED) MeasureSpec.UNSPECIFIED else MeasureSpec.AT_MOST,
        )
        val childHSpec = MeasureSpec.makeMeasureSpec(
            hSize, if (hMode == MeasureSpec.UNSPECIFIED) MeasureSpec.UNSPECIFIED else MeasureSpec.AT_MOST,
        )
        val kids = laidOutChildren()
        for (c in kids) c.measure(childWSpec, childHSpec)

        var contentW = 0
        var contentH = 0
        if (!vertical) {
            val limit = if (wMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else wSize - paddingLeft - paddingRight
            var x = 0; var lineH = 0
            for (c in kids) {
                if (x > 0 && x + c.measuredWidth > limit) {
                    contentW = maxOf(contentW, x - hgap); contentH += lineH + vgap; x = 0; lineH = 0
                }
                x += c.measuredWidth + hgap; lineH = maxOf(lineH, c.measuredHeight)
            }
            contentW = maxOf(contentW, x - hgap); contentH += lineH
        } else {
            val limit = if (hMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else hSize - paddingTop - paddingBottom
            var y = 0; var colW = 0
            for (c in kids) {
                if (y > 0 && y + c.measuredHeight > limit) {
                    contentH = maxOf(contentH, y - vgap); contentW += colW + hgap; y = 0; colW = 0
                }
                y += c.measuredHeight + vgap; colW = maxOf(colW, c.measuredWidth)
            }
            contentH = maxOf(contentH, y - vgap); contentW += colW
        }
        contentW = contentW.coerceAtLeast(0) + paddingLeft + paddingRight
        contentH = contentH.coerceAtLeast(0) + paddingTop + paddingBottom
        setMeasuredDimension(
            if (wMode == MeasureSpec.EXACTLY) wSize else contentW,
            if (hMode == MeasureSpec.EXACTLY) hSize else contentH,
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val kids = laidOutChildren()
        if (!vertical) {
            val limit = (r - l) - paddingLeft - paddingRight
            var x = paddingLeft; var y = paddingTop; var lineH = 0
            for (c in kids) {
                if (x > paddingLeft && x + c.measuredWidth > paddingLeft + limit) {
                    x = paddingLeft; y += lineH + vgap; lineH = 0
                }
                c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
                x += c.measuredWidth + hgap; lineH = maxOf(lineH, c.measuredHeight)
            }
        } else {
            val limit = (b - t) - paddingTop - paddingBottom
            var x = paddingLeft; var y = paddingTop; var colW = 0
            for (c in kids) {
                if (y > paddingTop && y + c.measuredHeight > paddingTop + limit) {
                    y = paddingTop; x += colW + hgap; colW = 0
                }
                c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
                y += c.measuredHeight + vgap; colW = maxOf(colW, c.measuredWidth)
            }
        }
    }
}
