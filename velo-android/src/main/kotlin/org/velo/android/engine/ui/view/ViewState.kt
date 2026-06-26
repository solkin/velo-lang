package org.velo.android.engine.ui.view

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import core.VeloFunction
import org.velo.android.R
import org.velo.android.engine.ui.Kind
import org.velo.android.engine.ui.UiBinding

/**
 * The mutable Android-side state behind a [VeloView] handle: the wrapped Android view,
 * its container slot for children, the active [UiBinding], retained callbacks and the
 * layout intents composed into LayoutParams when the view is `add`ed to a parent.
 *
 * The public [VeloView] facade is a thin dispatch surface over this — every Velo-callable
 * method there is a one-liner delegating here. The per-concern operations themselves live
 * in extension functions (ViewLayout / ViewText / ViewControls / ViewCollections /
 * ViewAppBar) so the broad widget API stays readable while remaining a single Velo native
 * type (Velo matches native handles by exact class, with no inheritance — so one container
 * can hold any widget only if every widget shares one type).
 */
internal class ViewState {

    var kind: Kind = Kind.NONE
    var av: View? = null
    var content: ViewGroup? = null
    var binding: UiBinding? = null

    val children = ArrayList<ViewState>()
    private val retained = ArrayList<VeloFunction>()

    // Layout intentions, composed into LayoutParams when this view is add()ed to a parent.
    var fillW = false
    var fillH = false
    var widthDp: Int? = null
    var heightDp: Int? = null
    var grow = 0
    var gapDp = 0

    /** Run [block] on the Android main thread and return its result, blocking the worker. */
    fun <T> ui(block: () -> T): T =
        (binding ?: error("View used outside a Ui session")).onUi(block)

    fun px(dp: Int): Int {
        val ctx = binding?.host?.context ?: av?.context ?: return dp
        return (dp * ctx.resources.displayMetrics.density).toInt()
    }

    fun retain(cb: VeloFunction) {
        cb.retain()
        retained.add(cb)
    }

    /** Release every callback held by this view tree — called when its screen is popped. */
    fun releaseCallbacks() {
        for (cb in retained) cb.release()
        retained.clear()
        for (child in children) child.releaseCallbacks()
    }

    /** The MaterialToolbar an app-bar view wraps (it sits inside an AppBarLayout). */
    fun toolbar(): MaterialToolbar? = when (val v = av) {
        is MaterialToolbar -> v
        is AppBarLayout -> v.findViewById(R.id.velo_toolbar)
        else -> null
    }

    fun listAdapter(): VeloListAdapter? = (av as? RecyclerView)?.adapter as? VeloListAdapter

    fun applyLayoutNow() {
        val v = av ?: return
        val parent = v.parent as? ViewGroup ?: return
        ui { v.layoutParams = layoutParamsFor(parent) }
    }

    /** Build LayoutParams for placing this view inside [parent], honouring fill/size/weight intents. */
    fun layoutParamsFor(parent: ViewGroup): ViewGroup.LayoutParams {
        val vertical = (parent as? LinearLayout)?.orientation == LinearLayout.VERTICAL
        var w = when {
            widthDp != null -> px(widthDp!!)
            fillW -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> ViewGroup.LayoutParams.WRAP_CONTENT
        }
        var h = when {
            heightDp != null -> px(heightDp!!)
            fillH -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> ViewGroup.LayoutParams.WRAP_CONTENT
        }
        if (parent is LinearLayout) {
            // Weight fills the free space along the parent's main axis (zero that
            // dimension) and, by convention, spans the cross axis unless sized explicitly —
            // a weighted row in a column is full width, a weighted column in a row full height.
            if (grow > 0) {
                if (vertical) {
                    h = 0
                    if (widthDp == null && !fillW) w = ViewGroup.LayoutParams.MATCH_PARENT
                } else {
                    w = 0
                    if (heightDp == null && !fillH) h = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
            return LinearLayout.LayoutParams(w, h, grow.toFloat())
        }
        return ViewGroup.LayoutParams(w, h)
    }
}
