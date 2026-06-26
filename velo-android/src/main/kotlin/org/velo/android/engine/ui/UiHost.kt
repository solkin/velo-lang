package org.velo.android.engine.ui

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import vm.LoopHandle

/**
 * The Android-side surface the Velo `Ui` native drives — a screen stack laid over
 * the running terminal, in the spirit of Java Swing: the program runs in the
 * terminal and may, at any point, push a screen, show a dialog, or never touch the
 * UI at all. Implemented by [UiScreenController] and owned by the terminal Activity.
 *
 * **Threading:** every method here runs on the Android main thread. The Velo
 * program executes on its own worker thread and reaches these methods through
 * [UiBinding.onUi], which marshals the call onto the main looper and waits for it.
 */
interface UiHost {

    /** A themed [Context] for inflating Material3 views (the Activity). */
    val context: Context

    /**
     * Hand the host the running program's [LoopHandle] so it can keep the event loop
     * alive while screens are shown — even a screen with no Velo callbacks. Called once
     * at run start.
     */
    fun attachLoop(loop: LoopHandle)

    /** Push [root] as a new full-screen top-of-stack screen; the terminal slides under it. */
    fun pushScreen(root: View)

    /** Pop the top screen. When the last screen goes, the terminal becomes visible again. */
    fun popScreen()

    /** Number of screens currently on the stack (0 = only the terminal is showing). */
    fun depth(): Int

    /**
     * Set the back handler for the current top screen. The handler returns true when
     * it consumed the back gesture; false (or no handler) falls through to a plain pop.
     */
    fun setBackHandler(handler: (() -> Boolean)?)

    /** Build and show a Material3 dialog; [build] is invoked on the main thread. */
    fun showDialog(build: (Context) -> AlertDialog)

    /** Show a Material3 snackbar, optionally with a trailing action. */
    fun showSnackbar(message: String, actionLabel: String?, action: (() -> Unit)?)

    /** Present [content] in a modal bottom sheet; [onDismiss] runs when it closes. */
    fun showBottomSheet(content: View, onDismiss: () -> Unit)

    /** Dismiss the current bottom sheet, if one is showing. */
    fun dismissBottomSheet()
}
