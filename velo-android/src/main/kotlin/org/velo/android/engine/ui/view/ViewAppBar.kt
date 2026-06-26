package org.velo.android.engine.ui.view

import androidx.core.content.ContextCompat
import core.VeloFunction
import org.velo.android.R

/** App-bar operations for a [ViewState]. */

/** Show a navigation (back) icon on an app bar and fire [cb] when it is tapped. */
internal fun ViewState.onNav(cb: VeloFunction) {
    retain(cb)
    ui {
        toolbar()?.apply {
            navigationIcon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_back)
            setNavigationOnClickListener { cb.post() }
        }
    }
}
