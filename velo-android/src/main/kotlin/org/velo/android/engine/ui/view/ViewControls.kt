package org.velo.android.engine.ui.view

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.widget.CompoundButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import core.VeloFunction

/**
 * Interactive-control operations for a [ViewState]: the generic tap, switch/checkbox
 * toggles, slider range/position, and progress indicators.
 */

/** Fire [cb] when a button (or any view) is tapped. */
internal fun ViewState.onClick(cb: VeloFunction) {
    retain(cb)
    ui { av?.setOnClickListener { cb.post() } }
}

/** Fire [cb] when a view is long-pressed. */
internal fun ViewState.onLongClick(cb: VeloFunction) {
    retain(cb)
    ui { av?.setOnLongClickListener { cb.post(); true } }
}

/**
 * Fire [down] while the view is held and [up] when released — the hold-to-act hook a game's
 * D-pad needs (press-and-hold to keep moving). A quick tap fires both in sequence.
 */
@SuppressLint("ClickableViewAccessibility")
internal fun ViewState.onPress(down: VeloFunction, up: VeloFunction) {
    retain(down)
    retain(up)
    ui {
        av?.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { down.post(); true }
                MotionEvent.ACTION_UP -> { up.post(); v.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { up.post(); true }
                else -> false
            }
        }
    }
}

/**
 * Fire [cb] with the view's new (width, height) in dp whenever its laid-out size changes —
 * the hook a Canvas (or any view) uses to refit its contents to the space the layout gives it.
 * Fires on first layout and on every later resize (rotation, a weighted box growing, …).
 */
internal fun ViewState.onResize(cb: VeloFunction) {
    retain(cb)
    ui {
        val v = av ?: return@ui
        val density = v.resources.displayMetrics.density
        v.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                cb.post((w / density).toInt(), (h / density).toInt())
            }
        }
    }
}

/** Set the on/off state of a switch, checkbox or (filter/choice) chip. */
internal fun ViewState.checked(on: Boolean) {
    ui {
        when (val v = av) {
            is MaterialSwitch -> v.isChecked = on
            is MaterialCheckBox -> v.isChecked = on
            is Chip -> v.isChecked = on
            else -> {}
        }
    }
}

/** Current on/off state of a switch, checkbox or chip. */
internal fun ViewState.isChecked(): Boolean = ui {
    when (val v = av) {
        is MaterialSwitch -> v.isChecked
        is MaterialCheckBox -> v.isChecked
        is Chip -> v.isChecked
        else -> false
    }
}

/** Fire [cb] with the new state when a switch or checkbox is toggled. */
internal fun ViewState.onToggle(cb: VeloFunction) {
    retain(cb)
    ui { (av as? CompoundButton)?.setOnCheckedChangeListener { _, isOn -> cb.post(isOn) } }
}

/** Set the value range of a slider. */
internal fun ViewState.range(min: Int, max: Int) {
    ui {
        (av as? Slider)?.let {
            it.valueFrom = min.toFloat()
            it.valueTo = max.toFloat()
            if (it.value < min || it.value > max) it.value = min.toFloat()
        }
    }
}

/** Set the position of a slider. */
internal fun ViewState.slide(v: Int) {
    ui { (av as? Slider)?.value = v.toFloat() }
}

/** Current position of a slider. */
internal fun ViewState.position(): Int = ui { (av as? Slider)?.value?.toInt() ?: 0 }

/** Fire [cb] with the new position when a slider is moved by the user. */
internal fun ViewState.onSlide(cb: VeloFunction) {
    retain(cb)
    ui { (av as? Slider)?.addOnChangeListener { _, v, fromUser -> if (fromUser) cb.post(v.toInt()) } }
}

/** Set a determinate progress percentage (0..100). */
internal fun ViewState.progress(pct: Int) {
    ui {
        when (val v = av) {
            is LinearProgressIndicator -> { v.isIndeterminate = false; v.setProgressCompat(pct, true) }
            is CircularProgressIndicator -> { v.isIndeterminate = false; v.setProgressCompat(pct, true) }
            else -> {}
        }
    }
}

/** Switch a progress indicator between indeterminate (spinning) and determinate. */
internal fun ViewState.indeterminate(on: Boolean) {
    ui {
        when (val v = av) {
            is LinearProgressIndicator -> v.isIndeterminate = on
            is CircularProgressIndicator -> v.isIndeterminate = on
            else -> {}
        }
    }
}
