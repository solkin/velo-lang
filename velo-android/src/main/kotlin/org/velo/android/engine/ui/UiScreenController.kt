package org.velo.android.engine.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as M
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import vm2.LoopHandle

/**
 * Hosts the Velo program's Material3 screens as a back-stack laid over the terminal.
 *
 * A full-screen overlay is added to the Activity's content frame; while it holds at
 * least one screen it covers the terminal, and when the last screen pops it goes away,
 * revealing the terminal again (the Swing-style "return to terminal" behaviour).
 *
 * A pushed screen is **blank** — just the program's root view, edge-to-edge with system
 * insets applied. No toolbar is added: the program builds its own app bar with `ui.appBar`
 * and places it, exactly like a native Activity. While any screen is shown the program's
 * event loop is pinned alive via [LoopHandle], so even a callback-less screen stays live.
 * Every method runs on the Android main thread (the `Ui` native marshals onto it first).
 */
class UiScreenController(private val activity: AppCompatActivity) : UiHost {

    private val overlay = FrameLayout(activity).apply {
        // Opaque so the terminal underneath neither shows through nor receives touches.
        setBackgroundColor(MaterialColors.getColor(this, M.attr.colorSurface, 0))
        isClickable = true
        visibility = View.GONE
    }
    private val stack = ArrayList<View>()
    private var backHandler: (() -> Boolean)? = null
    private var sheet: BottomSheetDialog? = null

    @Volatile
    private var loop: LoopHandle? = null

    init {
        activity.addContentView(
            overlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    override val context: Context get() = activity

    override fun attachLoop(loop: LoopHandle) {
        this.loop = loop
    }

    override fun pushScreen(root: View) {
        val screen = FrameLayout(activity).apply {
            setBackgroundColor(MaterialColors.getColor(this, M.attr.colorSurface, 0))
            isClickable = true
            addView(
                root,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
        applyInsets(screen)

        stack.lastOrNull()?.visibility = View.GONE
        overlay.addView(screen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        stack.add(screen)
        overlay.visibility = View.VISIBLE
        // The overlay is added late, so kick off an inset pass for the new screen.
        ViewCompat.requestApplyInsets(overlay)
        loop?.retain() // keep the program alive while this screen is shown
    }

    override fun popScreen() {
        val top = stack.removeLastOrNull() ?: return
        overlay.removeView(top)
        backHandler = null
        loop?.release()
        if (stack.isEmpty()) {
            overlay.visibility = View.GONE
        } else {
            stack.last().visibility = View.VISIBLE
        }
    }

    override fun depth(): Int = stack.size

    override fun setBackHandler(handler: (() -> Boolean)?) {
        backHandler = handler
    }

    override fun showDialog(build: (Context) -> AlertDialog) {
        build(activity).show()
    }

    override fun showSnackbar(message: String, actionLabel: String?, action: (() -> Unit)?) {
        val anchor: View = if (overlay.visibility == View.VISIBLE) overlay
        else activity.findViewById(android.R.id.content)
        val bar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
        if (actionLabel != null && action != null) bar.setAction(actionLabel) { action() }
        bar.show()
    }

    override fun showBottomSheet(content: View, onDismiss: () -> Unit) {
        dismissBottomSheet()
        (content.parent as? ViewGroup)?.removeView(content)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(content)
        dialog.setOnDismissListener {
            if (sheet === dialog) sheet = null
            onDismiss()
        }
        sheet = dialog
        dialog.show()
    }

    override fun dismissBottomSheet() {
        sheet?.dismiss()
        sheet = null
    }

    /** Called by the Activity's back gesture; true when consumed by the screen stack. */
    fun onBackPressed(): Boolean {
        if (stack.isEmpty()) return false
        val handled = backHandler?.invoke() ?: false
        if (!handled) popScreen()
        return true
    }

    /** Tear down every screen and the bottom sheet — used when (re)starting a program. */
    fun reset() {
        dismissBottomSheet()
        while (stack.isNotEmpty()) {
            overlay.removeView(stack.removeAt(stack.size - 1))
            loop?.release()
        }
        backHandler = null
        loop = null
        overlay.visibility = View.GONE
    }

    private fun applyInsets(screen: View) {
        // Edge-to-edge: the window draws behind the system bars. The top inset is owned by the
        // program's app bar (when it has one): the bar extends under the status bar and its
        // background colours that strip, so the status bar matches the toolbar — exactly like a
        // native Activity (see MainActivity/TerminalActivity). Only when there's no app bar does
        // the screen itself absorb the top inset.
        //
        // If the program's root ends in a bottom navigation bar, that bar spans under the system
        // navigation (Material style) and pads its own content up instead.
        //
        // The keyboard is handled the native soft-input way: its inset is applied only to the
        // scrollable region, so the focused field scrolls up above the keyboard while the app
        // bar and bottom bar stay put — the screen is never compressed.
        ViewCompat.setOnApplyWindowInsetsListener(screen) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomBar = bottomBarOf(screen)
            val screenBottom = if (bottomBar != null) {
                bottomBar.updatePadding(bottom = bars.bottom)
                0
            } else {
                bars.bottom
            }
            // App bar absorbs the top inset itself, so its colour reaches under the status bar.
            val appBar = appBarOf(screen)
            appBar?.updatePadding(top = bars.top)
            val screenTop = if (appBar != null) 0 else bars.top
            val scroller = scrollableOf(screen)
            if (scroller != null) {
                scroller.clipToPadding = false
                scroller.updatePadding(bottom = ime.bottom)
                screen.updatePadding(top = screenTop, left = bars.left, right = bars.right, bottom = screenBottom)
            } else {
                // No scrollable region to absorb the keyboard — keep the field visible by
                // insetting the screen itself.
                screen.updatePadding(
                    top = screenTop,
                    left = bars.left,
                    right = bars.right,
                    bottom = maxOf(screenBottom, ime.bottom),
                )
            }
            insets
        }
    }

    /** The program's top app bar, if it placed one (the first AppBarLayout in the screen tree). */
    private fun appBarOf(view: View): AppBarLayout? {
        if (view is AppBarLayout) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                appBarOf(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** The screen's bottom navigation bar, if the program placed one as the last root child. */
    private fun bottomBarOf(screen: View): View? {
        val root = (screen as? ViewGroup)?.getChildAt(0) as? ViewGroup ?: return null
        val last = root.getChildAt(root.childCount - 1)
        return if (last is NavigationBarView) last else null
    }

    /** The first scrollable container in the screen tree (where the keyboard inset is applied). */
    private fun scrollableOf(view: View): ViewGroup? {
        if (view is NestedScrollView || view is ScrollView || view is RecyclerView) return view as ViewGroup
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                scrollableOf(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
