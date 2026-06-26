package org.velo.android.engine.ui.view

import android.view.MenuItem
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

/**
 * Add a trailing action to an app bar's toolbar menu: a standard top-app-bar button showing
 * [icon] (from the built-in set), labelled [title] for accessibility, that fires [cb] on tap.
 * Shown as an icon when there's room, folded into the overflow menu otherwise — the
 * conventional Material behaviour. Call once per action; they appear in the order added.
 */
internal fun ViewState.appBarAction(title: String, icon: String, cb: VeloFunction) {
    retain(cb)
    ui {
        toolbar()?.apply {
            menu.add(title).apply {
                setIcon(loadIcon(context, icon))
                setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                setOnMenuItemClickListener {
                    cb.post()
                    true
                }
            }
        }
    }
}

/** Swap the icon of an existing app-bar action (matched by its [title]) — e.g. to toggle play/pause. */
internal fun ViewState.appBarActionIcon(title: String, icon: String) {
    ui {
        val tb = toolbar() ?: return@ui
        for (i in 0 until tb.menu.size()) {
            val item = tb.menu.getItem(i)
            if (item.title == title) item.icon = loadIcon(tb.context, icon)
        }
    }
}
