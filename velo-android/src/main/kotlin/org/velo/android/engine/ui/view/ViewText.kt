package org.velo.android.engine.ui.view

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.TextView
import com.google.android.material.textfield.TextInputLayout
import core.VeloFunction
import org.velo.android.R
import org.velo.android.engine.ui.Kind

/**
 * Text and text-field operations for a [ViewState]: setting the text/label/app-bar title,
 * the field hint, reading a field's value, type styling, enablement, and the field-change
 * event.
 */

/** Set the text, label, or app-bar title of a text/button/field/app-bar widget. */
internal fun ViewState.text(s: String) {
    ui {
        val bar = toolbar()
        if (bar != null) {
            bar.title = s
            return@ui
        }
        if (kind == Kind.LIST_ITEM) {
            av?.findViewById<TextView>(R.id.velo_li_headline)?.text = s
            return@ui
        }
        when (val v = av) {
            is TextInputLayout -> v.editText?.setText(s)
            is TextView -> v.text = s
            else -> {}
        }
    }
}

/** Set the floating hint of a text field. */
internal fun ViewState.hint(s: String) {
    ui { (av as? TextInputLayout)?.hint = s }
}

/** Current text of a field (empty for other widgets). */
internal fun ViewState.value(): String = ui {
    (av as? TextInputLayout)?.editText?.text?.toString() ?: ""
}

internal fun ViewState.textSize(sp: Int) {
    ui { (av as? TextView)?.textSize = sp.toFloat() }
}

internal fun ViewState.bold() {
    ui { (av as? TextView)?.setTypeface(null, Typeface.BOLD) }
}

/** Set a label's text color from a Velo color spec (hex or Material role token). */
internal fun ViewState.color(spec: String) {
    ui { (av as? TextView)?.let { it.setTextColor(resolveColor(it, spec)) } }
}

/** Apply a Material3 text style (e.g. "titleLarge", "bodyMedium") to a label. */
internal fun ViewState.style(token: String) {
    ui { (av as? TextView)?.setTextAppearance(textAppearanceRes(token)) }
}

/** Horizontal text alignment of a label: "start", "center" or "end". */
internal fun ViewState.textAlign(spec: String) {
    ui {
        (av as? TextView)?.gravity = when (spec) {
            "center" -> Gravity.CENTER_HORIZONTAL
            "end" -> Gravity.END
            else -> Gravity.START
        }
    }
}

/** Limit a label to [n] lines, ellipsising the overflow. */
internal fun ViewState.maxLines(n: Int) {
    ui {
        (av as? TextView)?.let {
            it.maxLines = n
            it.ellipsize = android.text.TextUtils.TruncateAt.END
        }
    }
}

/** Strike a line through a label's text. */
internal fun ViewState.strikethrough() {
    ui { (av as? TextView)?.let { it.paintFlags = it.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG } }
}

/** Enable or disable interaction with this widget. */
internal fun ViewState.enabled(on: Boolean) {
    ui { av?.isEnabled = on }
}

/** Fire [cb] with the new text whenever a field changes. */
internal fun ViewState.onChange(cb: VeloFunction) {
    retain(cb)
    ui {
        (av as? TextInputLayout)?.editText?.addTextChangedListener(
            object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { cb.post(s?.toString() ?: "") }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            },
        )
    }
}
