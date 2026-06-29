package org.velo.android.engine.ui.view

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.google.android.material.textfield.TextInputLayout
import core.VeloFunction

/**
 * Extended text-field operations for a [ViewState] wrapping a [TextInputLayout]: placeholder,
 * error state, keyboard type, and the submit/focus events. (The leading icon and supporting
 * line are handled by [leading]/[supporting], shared with the list item.)
 */

/** Set the placeholder shown inside an empty field. */
internal fun ViewState.placeholder(s: String) {
    ui { (av as? TextInputLayout)?.placeholderText = s }
}

/**
 * Set the field's error message; an empty string clears it. Named `errorText`, not `error`,
 * so it doesn't shadow `kotlin.error` inside other [ViewState] members — the Velo-facing name
 * is `error`, mapped on the [Widget] facade.
 */
internal fun ViewState.errorText(message: String) {
    ui { (av as? TextInputLayout)?.error = message.ifEmpty { null } }
}

/** Set the soft-keyboard type: "text", "number", "decimal", "password", "email" or "phone". */
internal fun ViewState.keyboardType(type: String) {
    ui {
        val et = (av as? TextInputLayout)?.editText ?: return@ui
        et.inputType = when (type) {
            "number" -> InputType.TYPE_CLASS_NUMBER
            "decimal" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            "phone" -> InputType.TYPE_CLASS_PHONE
            else -> InputType.TYPE_CLASS_TEXT
        }
    }
}

/** Fire [cb] with the field's text when the user presses the keyboard's done/enter action. */
internal fun ViewState.onSubmit(cb: VeloFunction) {
    retain(cb)
    ui {
        (av as? TextInputLayout)?.editText?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEND
            ) {
                cb.post(v.text?.toString() ?: "")
            }
            false
        }
    }
}

/** Fire [cb] with the new focus state whenever the field gains or loses focus. */
internal fun ViewState.onFocusChange(cb: VeloFunction) {
    retain(cb)
    ui {
        (av as? TextInputLayout)?.editText?.setOnFocusChangeListener { _, hasFocus -> cb.post(hasFocus) }
    }
}
