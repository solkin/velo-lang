package org.velo.android.engine.ui.view

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.textfield.TextInputLayout
import org.velo.android.R

/**
 * List-item operations for a [ViewState] whose view is the `ui.listItem` layout: the headline
 * is set via [text]; these add the supporting line and the leading/trailing icons, revealing
 * each sub-view as it is populated. [supporting]/[leading] double as a text field's helper text
 * and start icon when the handle wraps a field.
 */

/** Set the secondary supporting line (list item) or a field's helper text. */
internal fun ViewState.supporting(s: String) {
    ui {
        when (val v = av) {
            is TextInputLayout -> v.helperText = s
            else -> v?.findViewById<TextView>(R.id.velo_li_supporting)?.apply {
                text = s
                visibility = View.VISIBLE
            }
        }
    }
}

/** Set the leading icon (list item) or a field's start icon, from the built-in set. */
internal fun ViewState.leading(name: String) {
    ui {
        when (val v = av) {
            is TextInputLayout -> v.setStartIconDrawable(iconRes(name))
            else -> v?.findViewById<ImageView>(R.id.velo_li_leading)?.apply {
                setImageDrawable(loadIcon(context, name))
                visibility = View.VISIBLE
            }
        }
    }
}

/** Set the trailing icon from the built-in set. */
internal fun ViewState.trailing(name: String) {
    ui {
        av?.findViewById<ImageView>(R.id.velo_li_trailing)?.apply {
            setImageDrawable(loadIcon(context, name))
            visibility = View.VISIBLE
        }
    }
}
