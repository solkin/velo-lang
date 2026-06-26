package org.velo.android.engine.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import core.VeloFunction
import org.velo.android.R

/** What kind of Material3 widget a [VeloView] wraps — drives which methods are meaningful. */
internal enum class Kind {
    NONE, TEXT, BUTTON, FIELD, SWITCH, CHECKBOX, SLIDER, CARD,
    COLUMN, ROW, BOX, SCROLL, PROGRESS, SPINNER, LIST, TABS, APPBAR,
}

/**
 * The single Velo native handle for every on-screen widget — registered as `View`.
 *
 * Velo's native type system matches handles by exact class with no inheritance, so a
 * container that accepts "any widget" needs all widgets to share one Velo type. Hence
 * one wrapper class with a [kind] tag instead of a class per component: a column and a
 * button are both `View`, so `column.add(button)` type-checks. Methods that don't fit a
 * widget's kind are no-ops.
 *
 * Programs never construct this directly — the public no-arg constructor exists only so
 * the native binder (which requires exactly one public constructor of mappable types) can
 * introspect the class. Real instances come from [VeloUi]'s factories via [make], which
 * builds the Android view on the main thread first.
 *
 * Every method marshals its Android work onto the main thread through [UiBinding.onUi];
 * event setters additionally [VeloFunction.retain] the callback so the program's event
 * loop stays alive while the screen is shown (released when the screen is popped).
 */
class VeloView {

    // State is injected by [bind] right after construction (see [make]); the public
    // no-arg constructor exists only so the native binder can introspect the class —
    // exactly one public constructor, no Android types in its signature. A view built
    // outside the `Ui` factories (never bound) is inert.
    private var kind: Kind = Kind.NONE
    private var av: View? = null
    private var content: ViewGroup? = null
    private var binding: UiBinding? = null

    private val children = ArrayList<VeloView>()
    private val retained = ArrayList<VeloFunction>()

    // Layout intentions, composed into LayoutParams when this view is add()ed to a parent.
    private var fillW = false
    private var fillH = false
    private var widthDp: Int? = null
    private var heightDp: Int? = null
    private var grow = 0
    private var gapDp = 0

    // --- containers ---

    /** Append [child] to this container. */
    fun add(child: VeloView): VeloView = apply {
        val group = content ?: return@apply
        ui {
            val v = child.av ?: return@ui
            val lp = child.layoutParamsFor(group)
            if (gapDp > 0 && group.childCount > 0 && lp is LinearLayout.LayoutParams) {
                if (group is LinearLayout && group.orientation == LinearLayout.VERTICAL) lp.topMargin = px(gapDp)
                else lp.marginStart = px(gapDp)
            }
            v.layoutParams = lp
            group.addView(v)
        }
        children.add(child)
    }

    /** Inner spacing of this container, in dp. */
    fun padding(dp: Int): VeloView = apply {
        val px = px(dp)
        ui { (content ?: av)?.setPadding(px, px, px, px) }
    }

    /** Spacing between children of this linear container, in dp (applied as child margins on add). */
    fun gap(dp: Int): VeloView = apply { gapDp = dp }

    // gapDp is read by add() when placing each subsequent child.

    /** Center this container's children. */
    fun center(): VeloView = apply {
        ui { (content as? LinearLayout)?.gravity = android.view.Gravity.CENTER }
    }

    // --- sizing (composed at add time) ---

    fun fillWidth(): VeloView = apply { fillW = true; applyLayoutNow() }
    fun fillHeight(): VeloView = apply { fillH = true; applyLayoutNow() }
    fun width(dp: Int): VeloView = apply { widthDp = dp; applyLayoutNow() }
    fun height(dp: Int): VeloView = apply { heightDp = dp; applyLayoutNow() }

    /** Take a share of the parent's free space along its main axis (like Swing weight). */
    fun weight(w: Int): VeloView = apply { grow = w; applyLayoutNow() }

    // --- content / text ---

    /** Set the text, label, or app-bar title of a text/button/field/app-bar widget. */
    fun text(s: String): VeloView = apply {
        ui {
            val bar = toolbar()
            if (bar != null) { bar.title = s; return@ui }
            when (val v = av) {
                is TextInputLayout -> v.editText?.setText(s)
                is TextView -> v.text = s
                else -> {}
            }
        }
    }

    /** Set the floating hint of a text field. */
    fun hint(s: String): VeloView = apply {
        ui { (av as? TextInputLayout)?.hint = s }
    }

    /** Current text of a field (empty for other widgets). */
    fun value(): String = ui {
        (av as? TextInputLayout)?.editText?.text?.toString() ?: ""
    }

    fun textSize(sp: Int): VeloView = apply {
        ui { (av as? TextView)?.textSize = sp.toFloat() }
    }

    fun bold(): VeloView = apply {
        ui { (av as? TextView)?.setTypeface(null, android.graphics.Typeface.BOLD) }
    }

    /** Enable or disable interaction with this widget. */
    fun enabled(on: Boolean): VeloView = apply { ui { av?.isEnabled = on } }

    // --- toggles ---

    /** Set the on/off state of a switch or checkbox. */
    fun checked(on: Boolean): VeloView = apply {
        ui {
            when (val v = av) {
                is MaterialSwitch -> v.isChecked = on
                is MaterialCheckBox -> v.isChecked = on
                else -> {}
            }
        }
    }

    /** Current on/off state of a switch or checkbox. */
    fun isChecked(): Boolean = ui {
        when (val v = av) {
            is MaterialSwitch -> v.isChecked
            is MaterialCheckBox -> v.isChecked
            else -> false
        }
    }

    // --- slider ---

    /** Set the value range of a slider. */
    fun range(min: Int, max: Int): VeloView = apply {
        ui {
            (av as? Slider)?.let {
                it.valueFrom = min.toFloat()
                it.valueTo = max.toFloat()
                if (it.value < min || it.value > max) it.value = min.toFloat()
            }
        }
    }

    /** Set the position of a slider. */
    fun slide(v: Int): VeloView = apply {
        ui { (av as? Slider)?.value = v.toFloat() }
    }

    /** Current position of a slider. */
    fun position(): Int = ui { (av as? Slider)?.value?.toInt() ?: 0 }

    // --- progress ---

    /** Set a determinate progress percentage (0..100). */
    fun progress(pct: Int): VeloView = apply {
        ui {
            when (val v = av) {
                is LinearProgressIndicator -> { v.isIndeterminate = false; v.setProgressCompat(pct, true) }
                is CircularProgressIndicator -> { v.isIndeterminate = false; v.setProgressCompat(pct, true) }
                else -> {}
            }
        }
    }

    /** Switch a progress indicator between indeterminate (spinning) and determinate. */
    fun indeterminate(on: Boolean): VeloView = apply {
        ui {
            when (val v = av) {
                is LinearProgressIndicator -> v.isIndeterminate = on
                is CircularProgressIndicator -> v.isIndeterminate = on
                else -> {}
            }
        }
    }

    // --- events ---

    /** Fire [cb] when a button (or any view) is tapped. */
    fun onClick(cb: VeloFunction): VeloView = apply {
        retain(cb)
        ui { av?.setOnClickListener { cb.post() } }
    }

    /** Fire [cb] with the new text whenever a field changes. */
    fun onChange(cb: VeloFunction): VeloView = apply {
        retain(cb)
        ui {
            (av as? TextInputLayout)?.editText?.addTextChangedListener(
                object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) { cb.post(s?.toString() ?: "") }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                },
            )
        }
    }

    /** Fire [cb] with the new state when a switch or checkbox is toggled. */
    fun onToggle(cb: VeloFunction): VeloView = apply {
        retain(cb)
        ui {
            (av as? android.widget.CompoundButton)?.setOnCheckedChangeListener { _, isOn -> cb.post(isOn) }
        }
    }

    /** Fire [cb] with the new position when a slider is moved by the user. */
    fun onSlide(cb: VeloFunction): VeloView = apply {
        retain(cb)
        ui {
            (av as? Slider)?.addOnChangeListener { _, v, fromUser -> if (fromUser) cb.post(v.toInt()) }
        }
    }

    // --- list (RecyclerView) ---

    /** Replace the rows of a list with these text items. */
    fun items(rows: List<String>): VeloView = apply {
        ui { listAdapter()?.submit(rows) }
    }

    /** Fire [cb] with the row index when a list item is tapped. */
    fun onItemClick(cb: VeloFunction): VeloView = apply {
        retain(cb)
        ui { listAdapter()?.onClick = { index -> cb.post(index) } }
    }

    // --- tabs (TabLayout) ---

    /** Append a tab with the given [label]. */
    fun tab(label: String): VeloView = apply {
        ui { (av as? TabLayout)?.let { it.addTab(it.newTab().setText(label)) } }
    }

    /** Fire [cb] with the tab index whenever the selected tab changes. */
    fun onSelect(cb: VeloFunction): VeloView = apply {
        retain(cb)
        ui {
            (av as? TabLayout)?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) { cb.post(tab.position) }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
    }

    /** Select the tab at [index] programmatically. */
    fun select(index: Int): VeloView = apply {
        ui { (av as? TabLayout)?.getTabAt(index)?.select() }
    }

    // --- app bar ---

    /** Show a navigation (back) icon on an app bar and fire [cb] when it is tapped. */
    fun onNav(cb: VeloFunction): VeloView = apply {
        retain(cb)
        ui {
            toolbar()?.apply {
                navigationIcon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_back)
                setNavigationOnClickListener { cb.post() }
            }
        }
    }

    /** The MaterialToolbar an app-bar view wraps (it sits inside an AppBarLayout). */
    private fun toolbar(): MaterialToolbar? = when (val v = av) {
        is MaterialToolbar -> v
        is AppBarLayout -> v.findViewById(R.id.velo_toolbar)
        else -> null
    }

    // --- internals ---

    private fun listAdapter(): VeloListAdapter? = (av as? RecyclerView)?.adapter as? VeloListAdapter

    private fun retain(cb: VeloFunction) {
        cb.retain()
        retained.add(cb)
    }

    /** Release every callback held by this view tree — called by [VeloUi] when its screen is popped. */
    @JvmSynthetic
    internal fun releaseCallbacks() {
        for (cb in retained) cb.release()
        retained.clear()
        for (child in children) child.releaseCallbacks()
    }

    @JvmSynthetic
    internal fun androidView(): View? = av

    private fun applyLayoutNow() {
        val v = av ?: return
        val parent = v.parent as? ViewGroup ?: return
        ui { v.layoutParams = layoutParamsFor(parent) }
    }

    /** Build LayoutParams for placing this view inside [parent], honouring fill/size/weight intents. */
    private fun layoutParamsFor(parent: ViewGroup): ViewGroup.LayoutParams {
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

    private fun px(dp: Int): Int {
        val ctx = binding?.host?.context ?: av?.context ?: return dp
        return (dp * ctx.resources.displayMetrics.density).toInt()
    }

    private inline fun <T> ui(crossinline block: () -> T): T =
        (binding ?: error("View used outside a Ui session")).onUi { block() }

    /** Inject the Android state right after construction. Synthetic, so it isn't a Velo method. */
    @JvmSynthetic
    internal fun bind(kind: Kind, view: View, content: ViewGroup?, binding: UiBinding): VeloView = apply {
        this.kind = kind
        this.av = view
        this.content = content
        this.binding = binding
        // Widgets meant to span their row default to filling width (overridable per call).
        if (kind == Kind.FIELD || kind == Kind.SLIDER || kind == Kind.PROGRESS ||
            kind == Kind.LIST || kind == Kind.APPBAR || kind == Kind.SCROLL || kind == Kind.TABS
        ) {
            fillW = true
        }
    }

    companion object {
        /** Build a wrapper around an already-created Android [view]; [content] is where children go. */
        @JvmSynthetic
        internal fun make(kind: Kind, view: View, content: ViewGroup?, binding: UiBinding): VeloView =
            VeloView().bind(kind, view, content, binding)
    }
}

/**
 * A minimal text-row [RecyclerView] adapter for the Velo `list` widget: each row is a
 * Material list-item text view, tapping it fires [onClick] with the row index. Real
 * recycling, so large lists stay cheap — richer per-row content is out of scope for v1.
 */
internal class VeloListAdapter : RecyclerView.Adapter<VeloListAdapter.Row>() {

    private val items = ArrayList<String>()
    var onClick: ((Int) -> Unit)? = null

    @SuppressLint("NotifyDataSetChanged")
    fun submit(rows: List<String>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return Row(view as TextView)
    }

    override fun onBindViewHolder(holder: Row, position: Int) {
        holder.label.text = items[position]
        holder.label.setOnClickListener { onClick?.invoke(holder.bindingAdapterPosition) }
    }

    class Row(val label: TextView) : RecyclerView.ViewHolder(label)
}
