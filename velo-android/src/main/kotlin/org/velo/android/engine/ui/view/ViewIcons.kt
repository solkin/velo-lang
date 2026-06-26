package org.velo.android.engine.ui.view

import android.content.res.ColorStateList
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Icon-bearing and small-widget operations for a [ViewState]: setting/replacing a glyph,
 * tinting it, chip checkability and divider thickness. These target whichever of the
 * icon-capable widgets ([icon], [iconButton], [fab], [chip], [divider]) the handle wraps.
 */

/** Set or replace the glyph of an icon, icon-button, fab or chip from the built-in icon set. */
internal fun ViewState.icon(name: String) {
    ui {
        val v = av ?: return@ui
        val drawable = loadIcon(v.context, name)
        when (v) {
            is Chip -> v.chipIcon = drawable
            is MaterialButton -> v.icon = drawable // also covers ExtendedFloatingActionButton
            is FloatingActionButton -> v.setImageDrawable(drawable)
            is ImageView -> v.setImageDrawable(drawable)
            else -> {}
        }
    }
}

/** Tint the glyph with a Velo color spec (hex or Material role token). */
internal fun ViewState.tint(spec: String) {
    ui {
        val v = av ?: return@ui
        val tint = ColorStateList.valueOf(resolveColor(v, spec))
        when (v) {
            is Chip -> v.chipIconTint = tint
            is MaterialButton -> v.iconTint = tint
            is FloatingActionButton -> v.imageTintList = tint
            is ImageView -> v.imageTintList = tint
            else -> {}
        }
    }
}

/** Make a chip selectable (filter/suggestion style) or not. */
internal fun ViewState.checkable(on: Boolean) {
    ui { (av as? Chip)?.isCheckable = on }
}

/** Set a divider's thickness, in dp. */
internal fun ViewState.thickness(dp: Int) {
    ui { (av as? MaterialDivider)?.dividerThickness = px(dp) }
}
