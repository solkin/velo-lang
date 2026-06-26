package org.velo.android.engine.ui.view

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
