package org.velo.android.engine.ui

import android.view.View
import android.view.ViewGroup
import core.VeloFunction
import org.velo.android.engine.ui.view.ViewState
// The per-concern ViewState operations this facade delegates to live in the `view` subpackage.
import org.velo.android.engine.ui.view.*

/** What kind of Material3 widget a [VeloView] wraps — drives which methods are meaningful. */
internal enum class Kind {
    NONE, TEXT, BUTTON, FIELD, SWITCH, CHECKBOX, SLIDER, CARD,
    COLUMN, ROW, BOX, SCROLL, PROGRESS, SPINNER, LIST, TABS, APPBAR, CANVAS,
    ICON, ICON_BUTTON, FAB, CHIP, DIVIDER, SPACER, RADIO, RADIO_GROUP,
    SURFACE, FLOW_ROW, FLOW_COLUMN, LAZY_ROW, LIST_ITEM,
    NAV_BAR, NAV_RAIL, DRAWER,
}

/**
 * The single Velo native handle for every on-screen widget — registered as `View`.
 *
 * Velo's native type system matches handles by exact class with no inheritance, so a
 * container that accepts "any widget" needs all widgets to share one Velo type. Hence
 * one wrapper class with a [Kind] tag instead of a class per component: a column and a
 * button are both `View`, so `column.add(button)` type-checks. Methods that don't fit a
 * widget's kind are no-ops.
 *
 * This class is only a thin dispatch facade: each method delegates to a [ViewState]
 * operation (defined in the ViewLayout / ViewText / ViewControls / ViewCollections /
 * ViewAppBar extension files) so the broad widget API stays readable. [ViewState] holds
 * all Android state and marshals each call onto the main thread; event setters also retain
 * their callback so the program's event loop stays alive while the screen is shown.
 *
 * Programs never construct this directly — the public no-arg constructor exists only so the
 * native binder (which requires exactly one public constructor of mappable types) can
 * introspect the class. Real instances come from [VeloUi]'s factories via [make].
 */
class VeloView {

    // All Android state and the per-concern operations live here; the public methods below
    // are one-liners over it. Injected by [bind] right after construction (see [make]); a
    // view built outside the `Ui` factories (never bound) is inert. Private, so it is not
    // part of the Velo-visible native surface.
    private val state = ViewState()

    // --- containers / layout ---

    fun add(child: VeloView): VeloView = apply { state.add(child.state) }
    fun padding(dp: Int): VeloView = apply { state.padding(dp) }
    fun gap(dp: Int): VeloView = apply { state.gap(dp) }
    fun center(): VeloView = apply { state.center() }
    fun fillWidth(): VeloView = apply { state.fillWidth() }
    fun fillHeight(): VeloView = apply { state.fillHeight() }
    fun width(dp: Int): VeloView = apply { state.width(dp) }
    fun height(dp: Int): VeloView = apply { state.height(dp) }
    fun weight(w: Int): VeloView = apply { state.weight(w) }

    // --- common visual modifiers (any kind) ---

    fun background(color: String): VeloView = apply { state.background(color) }
    fun corner(dp: Int): VeloView = apply { state.corner(dp) }
    fun paddingXY(h: Int, v: Int): VeloView = apply { state.paddingXY(h, v) }
    fun align(spec: String): VeloView = apply { state.align(spec) }

    // --- content / text ---

    fun text(s: String): VeloView = apply { state.text(s) }
    fun color(spec: String): VeloView = apply { state.color(spec) }
    fun hint(s: String): VeloView = apply { state.hint(s) }
    fun value(): String = state.value()
    fun textSize(sp: Int): VeloView = apply { state.textSize(sp) }
    fun bold(): VeloView = apply { state.bold() }
    fun style(token: String): VeloView = apply { state.style(token) }
    fun textAlign(spec: String): VeloView = apply { state.textAlign(spec) }
    fun maxLines(n: Int): VeloView = apply { state.maxLines(n) }
    fun strikethrough(): VeloView = apply { state.strikethrough() }
    fun enabled(on: Boolean): VeloView = apply { state.enabled(on) }

    // --- text field extras ---

    fun placeholder(s: String): VeloView = apply { state.placeholder(s) }
    fun error(message: String): VeloView = apply { state.errorText(message) }
    fun keyboardType(type: String): VeloView = apply { state.keyboardType(type) }
    fun onSubmit(cb: VeloFunction): VeloView = apply { state.onSubmit(cb) }
    fun onFocusChange(cb: VeloFunction): VeloView = apply { state.onFocusChange(cb) }

    // --- icons & small widgets ---

    fun icon(name: String): VeloView = apply { state.icon(name) }
    fun iconOnly(): VeloView = apply { state.iconOnly() }
    fun tint(color: String): VeloView = apply { state.tint(color) }
    fun checkable(on: Boolean): VeloView = apply { state.checkable(on) }
    fun thickness(dp: Int): VeloView = apply { state.thickness(dp) }

    // --- surface & list item ---

    fun elevation(dp: Int): VeloView = apply { state.elevation(dp) }
    fun border(width: Int, color: String): VeloView = apply { state.border(width, color) }
    fun supporting(s: String): VeloView = apply { state.supporting(s) }
    fun leading(name: String): VeloView = apply { state.leading(name) }
    fun trailing(name: String): VeloView = apply { state.trailing(name) }

    // --- navigation components ---

    fun item(label: String, icon: String): VeloView = apply { state.item(label, icon) }
    fun drawerContent(panel: VeloView): VeloView = apply { state.drawerContent(panel.state) }
    fun openDrawer(on: Boolean): VeloView = apply { state.openDrawer(on) }
    fun isDrawerOpen(): Boolean = state.isDrawerOpen()
    fun badge(text: String): VeloView = apply { state.badge(text) }
    fun badgeDot(): VeloView = apply { state.badgeDot() }

    // --- toggles ---

    fun checked(on: Boolean): VeloView = apply { state.checked(on) }
    fun isChecked(): Boolean = state.isChecked()

    // --- slider ---

    fun range(min: Int, max: Int): VeloView = apply { state.range(min, max) }
    fun slide(v: Int): VeloView = apply { state.slide(v) }
    fun position(): Int = state.position()

    // --- progress ---

    fun progress(pct: Int): VeloView = apply { state.progress(pct) }
    fun indeterminate(on: Boolean): VeloView = apply { state.indeterminate(on) }

    // --- events ---

    fun onClick(cb: VeloFunction): VeloView = apply { state.onClick(cb) }
    fun onLongClick(cb: VeloFunction): VeloView = apply { state.onLongClick(cb) }
    fun onResize(cb: VeloFunction): VeloView = apply { state.onResize(cb) }
    fun onChange(cb: VeloFunction): VeloView = apply { state.onChange(cb) }
    fun onToggle(cb: VeloFunction): VeloView = apply { state.onToggle(cb) }
    fun onSlide(cb: VeloFunction): VeloView = apply { state.onSlide(cb) }

    // --- list (RecyclerView) ---

    fun items(rows: List<String>): VeloView = apply { state.items(rows) }
    fun onItemClick(cb: VeloFunction): VeloView = apply { state.onItemClick(cb) }

    // --- tabs (TabLayout) ---

    fun tab(label: String): VeloView = apply { state.tab(label) }
    fun onSelect(cb: VeloFunction): VeloView = apply { state.onSelect(cb) }
    fun select(index: Int): VeloView = apply { state.select(index) }

    // --- app bar ---

    fun onNav(cb: VeloFunction): VeloView = apply { state.onNav(cb) }
    fun action(title: String, icon: String, cb: VeloFunction): VeloView = apply { state.appBarAction(title, icon, cb) }

    // --- canvas (drawing) ---

    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int): Shape = state.drawLine(x1, y1, x2, y2)
    fun drawRect(x: Int, y: Int, w: Int, h: Int): Shape = state.drawRect(x, y, w, h)
    fun drawRoundRect(x: Int, y: Int, w: Int, h: Int, r: Int): Shape = state.drawRoundRect(x, y, w, h, r)
    fun drawCircle(cx: Int, cy: Int, r: Int): Shape = state.drawCircle(cx, cy, r)
    fun drawOval(x: Int, y: Int, w: Int, h: Int): Shape = state.drawOval(x, y, w, h)
    fun drawArc(x: Int, y: Int, w: Int, h: Int, start: Int, sweep: Int): Shape = state.drawArc(x, y, w, h, start, sweep)
    fun drawPie(x: Int, y: Int, w: Int, h: Int, start: Int, sweep: Int): Shape = state.drawPie(x, y, w, h, start, sweep)
    fun drawPath(spec: String): Shape = state.drawPath(spec)
    fun drawPoints(spec: String, mode: String): Shape = state.drawPoints(spec, mode)
    fun drawText(x: Int, y: Int, s: String, size: Int): Shape = state.drawText(x, y, s, size)
    fun aspectRatio(w: Int, h: Int): VeloView = apply { state.aspectRatio(w, h) }
    fun clear(): VeloView = apply { state.clearCanvas() }
    fun onTap(cb: VeloFunction): VeloView = apply { state.onTap(cb) }
    fun onPointerDown(cb: VeloFunction): VeloView = apply { state.onPointerDown(cb) }
    fun onPointerMove(cb: VeloFunction): VeloView = apply { state.onPointerMove(cb) }
    fun onPointerUp(cb: VeloFunction): VeloView = apply { state.onPointerUp(cb) }

    // --- host-side plumbing (synthetic, so not part of the Velo native surface) ---

    /** Release every callback held by this view tree — called by [VeloUi] when its screen is popped. */
    @JvmSynthetic
    internal fun releaseCallbacks() = state.releaseCallbacks()

    @JvmSynthetic
    internal fun androidView(): View? = state.av

    /** Inject the Android state right after construction. Synthetic, so it isn't a Velo method. */
    @JvmSynthetic
    internal fun bind(kind: Kind, view: View, content: ViewGroup?, binding: UiBinding): VeloView = apply {
        state.kind = kind
        state.av = view
        state.content = content
        state.binding = binding
        // Widgets meant to span their row default to filling width (overridable per call).
        if (kind == Kind.FIELD || kind == Kind.SLIDER || kind == Kind.PROGRESS ||
            kind == Kind.LIST || kind == Kind.APPBAR || kind == Kind.SCROLL || kind == Kind.TABS ||
            kind == Kind.DIVIDER || kind == Kind.LAZY_ROW || kind == Kind.NAV_BAR
        ) {
            state.fillW = true
        }
    }

    companion object {
        /** Build a wrapper around an already-created Android [view]; [content] is where children go. */
        @JvmSynthetic
        internal fun make(kind: Kind, view: View, content: ViewGroup?, binding: UiBinding): VeloView =
            VeloView().bind(kind, view, content, binding)
    }
}
