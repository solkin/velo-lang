package org.velo.android.engine.ui

import android.view.View
import android.view.ViewGroup
import core.NativeRegistry
import core.VeloFunction
import org.velo.android.engine.ui.view.ViewState
// The per-concern ViewState operations the facades delegate to live in the `view` subpackage.
import org.velo.android.engine.ui.view.*

/** What kind of Material3 widget a widget wraps — drives which ViewState branch runs. */
internal enum class Kind {
    NONE, TEXT, BUTTON, FIELD, SWITCH, CHECKBOX, SLIDER, CARD,
    COLUMN, ROW, BOX, SCROLL, PROGRESS, SPINNER, LIST, TABS, APPBAR, CANVAS,
    ICON, ICON_BUTTON, FAB, CHIP, DIVIDER, SPACER, RADIO, RADIO_GROUP,
    SURFACE, FLOW_ROW, FLOW_COLUMN, LAZY_ROW, LIST_ITEM,
    NAV_BAR, NAV_RAIL, DRAWER,
}

/**
 * Base of every on-screen widget: the universal layout, visual and event modifiers,
 * over the shared [ViewState] that holds all Android state. Each concrete widget below
 * is a thin typed facade that adds only the methods meaningful for it — so a container
 * accepts any widget while `text.progress()` is a compile error.
 *
 * Velo's registry exposes inherited methods and maps a modifier returning [Widget] to
 * the concrete subclass, so `column.padding(8)` stays a `Column` and a chain keeps its
 * type. Programs never construct these directly: real instances come from [VeloUi]'s
 * factories via [make]; the public no-arg constructor exists only so the native binder
 * can introspect each class.
 */
abstract class Widget {

    // All Android state and the per-concern operations live here; injected by [make].
    // @get:JvmSynthetic keeps the internal getter off the Velo-visible native surface.
    @get:JvmSynthetic internal val state = ViewState()

    fun padding(dp: Int): Widget = apply { state.padding(dp) }
    fun paddingXY(h: Int, v: Int): Widget = apply { state.paddingXY(h, v) }
    fun fillWidth(): Widget = apply { state.fillWidth() }
    fun fillHeight(): Widget = apply { state.fillHeight() }
    fun width(dp: Int): Widget = apply { state.width(dp) }
    fun height(dp: Int): Widget = apply { state.height(dp) }
    fun weight(w: Int): Widget = apply { state.weight(w) }
    fun background(color: String): Widget = apply { state.background(color) }
    fun corner(dp: Int): Widget = apply { state.corner(dp) }
    fun align(spec: String): Widget = apply { state.align(spec) }
    fun visible(on: Boolean): Widget = apply { state.visible(on) }
    fun enabled(on: Boolean): Widget = apply { state.enabled(on) }
    fun badge(text: String): Widget = apply { state.badge(text) }
    fun badgeDot(): Widget = apply { state.badgeDot() }
    fun onClick(cb: VeloFunction): Widget = apply { state.onClick(cb) }
    fun onLongClick(cb: VeloFunction): Widget = apply { state.onLongClick(cb) }
    fun onPress(down: VeloFunction, up: VeloFunction): Widget = apply { state.onPress(down, up) }
    fun onResize(cb: VeloFunction): Widget = apply { state.onResize(cb) }

    // ---- host-side plumbing (synthetic, so not part of the Velo native surface) ----

    @JvmSynthetic internal fun androidView(): View? = state.av

    @JvmSynthetic internal fun releaseCallbacks() = state.releaseCallbacks()

    @JvmSynthetic
    internal fun injectState(kind: Kind, view: View, content: ViewGroup?, binding: UiBinding) {
        state.kind = kind
        state.av = view
        state.content = content
        state.binding = binding
        if (kind == Kind.FIELD || kind == Kind.SLIDER || kind == Kind.PROGRESS ||
            kind == Kind.LIST || kind == Kind.APPBAR || kind == Kind.SCROLL || kind == Kind.TABS ||
            kind == Kind.DIVIDER || kind == Kind.LAZY_ROW || kind == Kind.NAV_BAR
        ) {
            state.fillW = true
        }
    }

    companion object {
        /** Build [kind] over an already-created Android [view]; [content] is where children go. */
        @JvmSynthetic
        internal fun <T : Widget> make(create: () -> T, kind: Kind, view: View, content: ViewGroup?, binding: UiBinding): T =
            create().apply { injectState(kind, view, content, binding) }
    }
}

/** Anything that bears text: a label, button, field, chip, list item or app bar. */
abstract class TextWidget : Widget() {
    fun text(s: String): TextWidget = apply { state.text(s) }
    fun color(spec: String): TextWidget = apply { state.color(spec) }
    fun textSize(sp: Int): TextWidget = apply { state.textSize(sp) }
    fun bold(): TextWidget = apply { state.bold() }
    fun style(token: String): TextWidget = apply { state.style(token) }
    fun textAlign(spec: String): TextWidget = apply { state.textAlign(spec) }
    fun maxLines(n: Int): TextWidget = apply { state.maxLines(n) }
    fun strikethrough(): TextWidget = apply { state.strikethrough() }
}

class Text : TextWidget()

class Button : TextWidget() {
    fun icon(name: String): Button = apply { state.icon(name) }
    fun iconOnly(): Button = apply { state.iconOnly() }
    fun tint(color: String): Button = apply { state.tint(color) }
}

class Field : TextWidget() {
    fun value(): String = state.value()
    fun hint(s: String): Field = apply { state.hint(s) }
    fun placeholder(s: String): Field = apply { state.placeholder(s) }
    fun error(message: String): Field = apply { state.errorText(message) }
    fun keyboardType(type: String): Field = apply { state.keyboardType(type) }
    fun onSubmit(cb: VeloFunction): Field = apply { state.onSubmit(cb) }
    fun onFocusChange(cb: VeloFunction): Field = apply { state.onFocusChange(cb) }
    fun onChange(cb: VeloFunction): Field = apply { state.onChange(cb) }
    fun leading(name: String): Field = apply { state.leading(name) }
    fun supporting(s: String): Field = apply { state.supporting(s) }
}

class Chip : TextWidget() {
    fun icon(name: String): Chip = apply { state.icon(name) }
    fun tint(color: String): Chip = apply { state.tint(color) }
    fun checkable(on: Boolean): Chip = apply { state.checkable(on) }
    fun checked(on: Boolean): Chip = apply { state.checked(on) }
    fun isChecked(): Boolean = state.isChecked()
    fun onToggle(cb: VeloFunction): Chip = apply { state.onToggle(cb) }
}

/** A switch, checkbox or radio button: a labelled on/off control. */
class Toggle : TextWidget() {
    fun checked(on: Boolean): Toggle = apply { state.checked(on) }
    fun isChecked(): Boolean = state.isChecked()
    fun onToggle(cb: VeloFunction): Toggle = apply { state.onToggle(cb) }
}

class ListItem : TextWidget() {
    fun supporting(s: String): ListItem = apply { state.supporting(s) }
    fun leading(name: String): ListItem = apply { state.leading(name) }
    fun trailing(name: String): ListItem = apply { state.trailing(name) }
}

class AppBar : TextWidget() {
    fun onNav(cb: VeloFunction): AppBar = apply { state.onNav(cb) }
    fun action(title: String, icon: String, cb: VeloFunction): AppBar = apply { state.appBarAction(title, icon, cb) }
    fun actionIcon(title: String, icon: String): AppBar = apply { state.appBarActionIcon(title, icon) }
}

class Icon : Widget() {
    fun icon(name: String): Icon = apply { state.icon(name) }
    fun tint(color: String): Icon = apply { state.tint(color) }
}

/** A container: holds children added with [add]. */
open class Container : Widget() {
    fun add(child: Any): Container = apply { state.add((child as Widget).state) }
    fun gap(dp: Int): Container = apply { state.gap(dp) }
    fun center(): Container = apply { state.center() }
    fun onSelect(cb: VeloFunction): Container = apply { state.onSelect(cb) }
    fun select(index: Int): Container = apply { state.select(index) }
}

class Surface : Container() {
    fun elevation(dp: Int): Surface = apply { state.elevation(dp) }
    fun border(width: Int, color: String): Surface = apply { state.border(width, color) }
}

class Drawer : Container() {
    fun drawerContent(panel: Any): Drawer = apply { state.drawerContent((panel as Widget).state) }
    fun openDrawer(on: Boolean): Drawer = apply { state.openDrawer(on) }
    fun isDrawerOpen(): Boolean = state.isDrawerOpen()
}

class Slider : Widget() {
    fun range(min: Int, max: Int): Slider = apply { state.range(min, max) }
    fun slide(v: Int): Slider = apply { state.slide(v) }
    fun position(): Int = state.position()
    fun onSlide(cb: VeloFunction): Slider = apply { state.onSlide(cb) }
}

class Progress : Widget() {
    fun progress(pct: Int): Progress = apply { state.progress(pct) }
    fun indeterminate(on: Boolean): Progress = apply { state.indeterminate(on) }
}

class ListView : Widget() {
    fun items(rows: List<String>): ListView = apply { state.items(rows) }
    fun onItemClick(cb: VeloFunction): ListView = apply { state.onItemClick(cb) }
}

class Tabs : Widget() {
    fun tab(label: String): Tabs = apply { state.tab(label) }
    fun onSelect(cb: VeloFunction): Tabs = apply { state.onSelect(cb) }
    fun select(index: Int): Tabs = apply { state.select(index) }
}

class Nav : Widget() {
    fun item(label: String, icon: String): Nav = apply { state.item(label, icon) }
    fun onSelect(cb: VeloFunction): Nav = apply { state.onSelect(cb) }
    fun select(index: Int): Nav = apply { state.select(index) }
}

class Canvas : Widget() {
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
    fun clear(): Canvas = apply { state.clearCanvas() }
    fun aspectRatio(w: Int, h: Int): Canvas = apply { state.aspectRatio(w, h) }
    fun onTap(cb: VeloFunction): Canvas = apply { state.onTap(cb) }
    fun onPointerDown(cb: VeloFunction): Canvas = apply { state.onPointerDown(cb) }
    fun onPointerMove(cb: VeloFunction): Canvas = apply { state.onPointerMove(cb) }
    fun onPointerUp(cb: VeloFunction): Canvas = apply { state.onPointerUp(cb) }
}

class Divider : Widget() {
    fun thickness(dp: Int): Divider = apply { state.thickness(dp) }
}

class Spacer : Widget()

/** Register the `Ui` factory, `Shape`, and every widget type (abstract bases are not registered). */
fun NativeRegistry.registerUiNatives(): NativeRegistry = this
    .register("Ui", VeloUi::class)
    .register("Shape", Shape::class)
    .register(Text::class).register(Button::class).register(Field::class)
    .register(Chip::class).register(Toggle::class).register(ListItem::class)
    .register(AppBar::class).register(Icon::class).register(Container::class)
    .register(Surface::class).register(Drawer::class).register(Slider::class)
    .register(Progress::class).register(ListView::class).register(Tabs::class)
    .register(Nav::class).register(Canvas::class).register(Divider::class)
    .register(Spacer::class)

/** The Velo names every UI widget type registers under — for contract tests. */
val uiWidgetNames: List<String> = listOf(
    "Text", "Button", "Field", "Chip", "Toggle", "ListItem", "AppBar", "Icon",
    "Container", "Surface", "Drawer", "Slider", "Progress", "ListView", "Tabs",
    "Nav", "Canvas", "Divider", "Spacer",
)
