package org.velo.android.engine.ui.view

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.navigation.NavigationBarView

/**
 * Navigation operations for a [ViewState]: destination items for a bottom navigation bar or
 * side rail, the modal drawer's panel and open/close state, and badge overlays. Selection on a
 * nav bar/rail is handled by [onSelect]/[select] alongside tabs and radio groups.
 */

/** Add a destination ([label] + icon) to a navigation bar or rail. */
internal fun ViewState.item(label: String, iconName: String) {
    ui {
        (av as? NavigationBarView)?.let { bar ->
            val id = bar.menu.size()
            bar.menu.add(0, id, id, label).icon = loadIcon(bar.context, iconName)
        }
    }
}

/** Set the side panel shown when a drawer opens. */
internal fun ViewState.drawerContent(panel: ViewState) {
    ui {
        val dl = av as? DrawerLayout ?: return@ui
        val p = panel.av ?: return@ui
        val lp = DrawerLayout.LayoutParams(px(280), DrawerLayout.LayoutParams.MATCH_PARENT)
        lp.gravity = Gravity.START
        dl.addView(p, lp)
    }
    children.add(panel)
}

/** Open or close the drawer's side panel. */
internal fun ViewState.openDrawer(on: Boolean) {
    ui {
        (av as? DrawerLayout)?.let { if (on) it.openDrawer(Gravity.START) else it.closeDrawer(Gravity.START) }
    }
}

/** Whether the drawer's side panel is currently open. */
internal fun ViewState.isDrawerOpen(): Boolean = ui {
    (av as? DrawerLayout)?.isDrawerOpen(Gravity.START) ?: false
}

/** Overlay a numeric/text badge on this view. */
@OptIn(ExperimentalBadgeUtils::class)
internal fun ViewState.badge(text: String) = attachBadge { it.text = text }

/** Overlay a small dot badge (no text) on this view. */
@OptIn(ExperimentalBadgeUtils::class)
internal fun ViewState.badgeDot() = attachBadge { }

@OptIn(ExperimentalBadgeUtils::class)
private fun ViewState.attachBadge(configure: (BadgeDrawable) -> Unit) {
    ui {
        val v = av ?: return@ui
        val b = BadgeDrawable.create(v.context)
        configure(b)
        v.post {
            BadgeUtils.attachBadgeDrawable(b, v)
            // A badge draws past the anchor's bounds (top/end corner); let it escape the
            // clipping of the immediate ancestors so it isn't sliced off.
            unclipAncestors(v)
        }
    }
}

private fun unclipAncestors(view: View) {
    var parent = view.parent
    var hops = 0
    while (parent is ViewGroup && hops < 3) {
        parent.clipChildren = false
        parent.clipToPadding = false
        parent = parent.parent
        hops++
    }
}
