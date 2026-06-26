package org.velo.android.engine.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as M
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import core.VeloFunction
import org.velo.android.R

/**
 * The Velo `Ui` native: the program's entry point to Material3 UI, in the spirit of
 * Java Swing. A program runs in the terminal and may, at any moment, build widgets,
 * `open` a screen, show a `dialog`, or never touch the UI at all.
 *
 * Construct it with `new Ui()`; like `Terminal`, it reads the active [UiBinding] from a
 * thread-local the session installs for the run. Every method here is called on the Velo
 * worker thread and hops onto the Android main thread via the binding.
 *
 * Widgets are created by the factory methods (`column`, `button`, `field`, …), all
 * returning the single [VeloView] handle type so containers can hold any of them.
 * Navigation (`open`/`close`/`title`/`onBack`) maintains a screen back-stack over the
 * terminal; closing the last screen reveals the terminal again.
 */
class VeloUi {

    private class Screen(val root: VeloView, var onBack: VeloFunction?)

    // Touched only on the main thread (all mutations run inside binding.onUi).
    private val screens = ArrayList<Screen>()

    // Captured at construction (on the worker thread, mid-run) so navigation methods work
    // even when invoked from a host main-thread callback — e.g. the system back gesture —
    // where the per-thread binding isn't installed.
    private val capturedBinding: UiBinding? = UiBinding.current.get()

    private fun binding(): UiBinding =
        capturedBinding ?: error("Ui native used outside a Velo run")

    // --- widget factories ---

    /** A read-only text label. */
    fun text(s: String): VeloView = build(Kind.TEXT) { ctx ->
        MaterialTextView(ctx).apply {
            text = s
            setTextAppearance(M.style.TextAppearance_Material3_BodyLarge)
        } to null
    }

    /** A filled Material3 button. */
    fun button(s: String): VeloView = button(s, null)

    /** A tonal (secondary-container) Material3 button. */
    fun tonalButton(s: String): VeloView = button(s, M.style.Widget_Material3_Button_TonalButton)

    /** An outlined Material3 button. */
    fun outlinedButton(s: String): VeloView = button(s, M.style.Widget_Material3_Button_OutlinedButton)

    /** A low-emphasis text-only Material3 button. */
    fun textButton(s: String): VeloView = button(s, M.style.Widget_Material3_Button_TextButton)

    private fun button(s: String, styleRes: Int?): VeloView = build(Kind.BUTTON) { ctx ->
        val themed = if (styleRes != null) ContextThemeWrapper(ctx, styleRes) else ctx
        MaterialButton(themed).apply { text = s } to null
    }

    /** An outlined text input field with a floating [hint]. */
    fun field(hint: String): VeloView = build(Kind.FIELD) { ctx ->
        val til = TextInputLayout(ContextThemeWrapper(ctx, M.style.Widget_Material3_TextInputLayout_OutlinedBox))
        til.hint = hint
        til.addView(TextInputEditText(til.context))
        til to null
    }

    /** A Material3 switch with a trailing [label]. */
    fun toggle(label: String): VeloView = build(Kind.SWITCH) { ctx ->
        MaterialSwitch(ctx).apply { text = label } to null
    }

    /** A Material3 checkbox with a trailing [label]. */
    fun check(label: String): VeloView = build(Kind.CHECKBOX) { ctx ->
        MaterialCheckBox(ctx).apply { text = label } to null
    }

    /** A Material3 slider over the inclusive range [min]..[max]. */
    fun slider(min: Int, max: Int): VeloView = build(Kind.SLIDER) { ctx ->
        Slider(ctx).apply {
            valueFrom = min.toFloat()
            valueTo = max.toFloat()
            value = min.toFloat()
        } to null
    }

    /** A Material3 card container; add children to it. */
    fun card(): VeloView = build(Kind.CARD) { ctx ->
        val card = MaterialCardView(ctx)
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * ctx.resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        card.addView(inner)
        card to inner
    }

    /** A vertical container. */
    fun column(): VeloView = build(Kind.COLUMN) { ctx ->
        val ll = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        ll to ll
    }

    /** A horizontal container. */
    fun row(): VeloView = build(Kind.ROW) { ctx ->
        val ll = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        ll to ll
    }

    /** A stacking (frame) container; children overlap. */
    fun box(): VeloView = build(Kind.BOX) { ctx ->
        val fl = FrameLayout(ctx)
        fl to fl
    }

    /** A vertically scrolling container. */
    fun scroll(): VeloView = build(Kind.SCROLL) { ctx ->
        val sv = NestedScrollView(ctx)
        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(
            inner,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        sv to inner
    }

    /** A linear (horizontal bar) progress indicator. */
    fun progress(): VeloView = build(Kind.PROGRESS) { ctx ->
        LinearProgressIndicator(ctx).apply { isIndeterminate = true } to null
    }

    /** A circular (spinning) progress indicator. */
    fun spinner(): VeloView = build(Kind.SPINNER) { ctx ->
        CircularProgressIndicator(ctx).apply { isIndeterminate = true } to null
    }

    /** A recycling list of text rows; fill it with [VeloView.items] and react with [VeloView.onItemClick]. */
    fun list(): VeloView = build(Kind.LIST) { ctx ->
        RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = VeloListAdapter()
        } to null
    }

    /** A Material3 tab row; add tabs with [VeloView.tab] and react with [VeloView.onSelect]. */
    fun tabs(): VeloView = build(Kind.TABS) { ctx ->
        TabLayout(ctx).apply {
            // Spread tabs evenly across the full width, Material-style.
            tabMode = TabLayout.MODE_FIXED
            tabGravity = TabLayout.GRAVITY_FILL
        } to null
    }

    /**
     * A Material3 top app bar with [title]. Nothing is added to a screen automatically —
     * place this at the top of your layout yourself, native-style. Use [VeloView.text] to
     * change the title and [VeloView.onNav] for the navigation (back) button.
     */
    fun appBar(title: String): VeloView {
        val b = binding()
        return b.onUi {
            val bar = LayoutInflater.from(b.host.context).inflate(R.layout.velo_app_bar, null)
            bar.findViewById<MaterialToolbar>(R.id.velo_toolbar).title = title
            VeloView.make(Kind.APPBAR, bar, null, b)
        }
    }

    // --- navigation ---

    /** Push [root] as a new full-screen screen over the terminal and return it. */
    fun open(root: VeloView): VeloView {
        val b = binding()
        b.onUi {
            screens.add(Screen(root, null))
            b.host.pushScreen(root.androidView()!!)
            refreshBack(b)
        }
        return root
    }

    /** Pop the current screen; the terminal reappears once the last one closes. */
    fun close() {
        val b = binding()
        b.onUi {
            val screen = screens.removeLastOrNull() ?: return@onUi
            b.host.popScreen()
            screen.onBack?.release()
            screen.root.releaseCallbacks()
            refreshBack(b)
        }
    }

    /** Handle the back gesture on the current screen yourself; call [close] to dismiss it. */
    fun onBack(cb: VeloFunction) {
        val b = binding()
        b.onUi {
            val top = screens.lastOrNull() ?: return@onUi
            top.onBack?.release()
            cb.retain()
            top.onBack = cb
        }
    }

    // --- dialogs & snackbars ---

    /** A simple alert with one dismissing button that fires [onClick]. */
    fun dialog(title: String, message: String, button: String, onClick: VeloFunction) {
        val b = binding()
        onClick.retain()
        b.onUi { b.host.showDialog { ctx ->
            MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(button) { _, _ -> onClick.post() }
                .setOnDismissListener { onClick.release() }
                .create()
        } }
    }

    /** A confirm dialog: [yes]/[no] buttons firing [onYes]/[onNo]. */
    fun confirm(
        title: String,
        message: String,
        yes: String,
        onYes: VeloFunction,
        no: String,
        onNo: VeloFunction,
    ) {
        val b = binding()
        onYes.retain()
        onNo.retain()
        b.onUi { b.host.showDialog { ctx ->
            MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(yes) { _, _ -> onYes.post() }
                .setNegativeButton(no) { _, _ -> onNo.post() }
                .setOnDismissListener { onYes.release(); onNo.release() }
                .create()
        } }
    }

    /** Show a transient snackbar message. */
    fun snackbar(message: String) {
        val b = binding()
        b.onUi { b.host.showSnackbar(message, null, null) }
    }

    /** Show a snackbar with a trailing action that fires [onAction]. */
    fun snackAction(message: String, action: String, onAction: VeloFunction) {
        val b = binding()
        b.onUi { b.host.showSnackbar(message, action) { onAction.post() } }
    }

    /** Present [content] in a modal bottom sheet; its callbacks live until it is dismissed. */
    fun sheet(content: VeloView) {
        val b = binding()
        b.onUi {
            b.host.showBottomSheet(content.androidView()!!) { content.releaseCallbacks() }
        }
    }

    /** Dismiss the current bottom sheet, if any. */
    fun dismissSheet() {
        val b = binding()
        b.onUi { b.host.dismissBottomSheet() }
    }

    // --- internals ---

    private fun refreshBack(b: UiBinding) {
        if (screens.isEmpty()) {
            b.host.setBackHandler(null)
            return
        }
        b.host.setBackHandler {
            val top = screens.lastOrNull()
            val handler = top?.onBack
            if (handler != null) handler.post() else close()
            true
        }
    }

    private fun build(kind: Kind, factory: (Context) -> Pair<View, ViewGroup?>): VeloView {
        val b = binding()
        return b.onUi {
            val (view, content) = factory(b.host.context)
            VeloView.make(kind, view, content, b)
        }
    }
}
