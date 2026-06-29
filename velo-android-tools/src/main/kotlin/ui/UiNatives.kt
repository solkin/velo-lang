package ui

import core.NativeRegistry
import core.VeloFunction

/** Register the `Ui` factory and every widget type (abstract bases are not registered). */
fun NativeRegistry.registerUiStubs(): NativeRegistry = this
    .register(Ui::class).register(Shape::class)
    .register(Text::class).register(Button::class).register(Field::class)
    .register(Chip::class).register(Toggle::class).register(ListItem::class)
    .register(AppBar::class).register(Icon::class).register(Container::class)
    .register(Surface::class).register(Drawer::class).register(Slider::class)
    .register(Progress::class).register(ListView::class).register(Tabs::class)
    .register(Nav::class).register(Canvas::class).register(Divider::class)
    .register(Spacer::class)

/**
 * Compile-time signature mirrors of the Android UI widget natives
 * (`org.velo.android.engine.ui.*`).
 *
 * The sample compiler resolves and type-checks native calls against registered host
 * classes, but the real Material3 implementations live in the Android app and pull in
 * `android.jar`, which a pure-JVM build can't load. These stubs carry the exact same
 * Velo-facing signatures so a `.vel` UI program compiles to `.vbc` here, then links
 * against the real implementations at runtime on Android (the linker matches by Velo
 * name + signature). The bodies are never executed.
 *
 * Widgets are split into typed classes that share modifiers through abstract bases:
 * the registry exposes inherited public methods, and a modifier returning its base is
 * exposed as returning the concrete widget — so a fluent chain keeps its type and a
 * container accepts any widget while `text.progress()` is a compile error. Keep these
 * in lockstep with the Android classes — a signature drift surfaces as a link error.
 */
private fun nope(): Nothing =
    throw UnsupportedOperationException("Velo UI natives are only available on the Android host")

/** Every widget: universal layout, visual and event modifiers. */
@Suppress("unused")
abstract class Widget {
    fun padding(dp: Int): Widget = nope()
    fun paddingXY(h: Int, v: Int): Widget = nope()
    fun fillWidth(): Widget = nope()
    fun fillHeight(): Widget = nope()
    fun width(dp: Int): Widget = nope()
    fun height(dp: Int): Widget = nope()
    fun weight(w: Int): Widget = nope()
    fun background(color: String): Widget = nope()
    fun corner(dp: Int): Widget = nope()
    fun align(spec: String): Widget = nope()
    fun visible(on: Boolean): Widget = nope()
    fun enabled(on: Boolean): Widget = nope()
    fun badge(text: String): Widget = nope()
    fun badgeDot(): Widget = nope()
    fun onClick(cb: VeloFunction): Widget = nope()
    fun onLongClick(cb: VeloFunction): Widget = nope()
    fun onPress(down: VeloFunction, up: VeloFunction): Widget = nope()
    fun onResize(cb: VeloFunction): Widget = nope()
}

/** Anything that bears text: a label, button, field, chip, list item or app bar. */
@Suppress("unused")
abstract class TextWidget : Widget() {
    fun text(s: String): TextWidget = nope()
    fun color(spec: String): TextWidget = nope()
    fun textSize(sp: Int): TextWidget = nope()
    fun bold(): TextWidget = nope()
    fun style(token: String): TextWidget = nope()
    fun textAlign(spec: String): TextWidget = nope()
    fun maxLines(n: Int): TextWidget = nope()
    fun strikethrough(): TextWidget = nope()
}

@Suppress("unused")
class Text : TextWidget()

@Suppress("unused")
class Button : TextWidget() {
    fun icon(name: String): Button = nope()
    fun iconOnly(): Button = nope()
    fun tint(color: String): Button = nope()
}

@Suppress("unused")
class Field : TextWidget() {
    fun value(): String = nope()
    fun hint(s: String): Field = nope()
    fun placeholder(s: String): Field = nope()
    fun error(message: String): Field = nope()
    fun keyboardType(type: String): Field = nope()
    fun onSubmit(cb: VeloFunction): Field = nope()
    fun onFocusChange(cb: VeloFunction): Field = nope()
    fun onChange(cb: VeloFunction): Field = nope()
    fun leading(name: String): Field = nope()
    fun supporting(s: String): Field = nope()
}

@Suppress("unused")
class Chip : TextWidget() {
    fun icon(name: String): Chip = nope()
    fun tint(color: String): Chip = nope()
    fun checkable(on: Boolean): Chip = nope()
    fun checked(on: Boolean): Chip = nope()
    fun isChecked(): Boolean = nope()
    fun onToggle(cb: VeloFunction): Chip = nope()
}

/** A switch, checkbox or radio button: a labelled on/off control. */
@Suppress("unused")
class Toggle : TextWidget() {
    fun checked(on: Boolean): Toggle = nope()
    fun isChecked(): Boolean = nope()
    fun onToggle(cb: VeloFunction): Toggle = nope()
}

@Suppress("unused")
class ListItem : TextWidget() {
    fun supporting(s: String): ListItem = nope()
    fun leading(name: String): ListItem = nope()
    fun trailing(name: String): ListItem = nope()
}

@Suppress("unused")
class AppBar : TextWidget() {
    fun onNav(cb: VeloFunction): AppBar = nope()
    fun action(title: String, icon: String, cb: VeloFunction): AppBar = nope()
    fun actionIcon(title: String, icon: String): AppBar = nope()
}

@Suppress("unused")
class Icon : Widget() {
    fun icon(name: String): Icon = nope()
    fun tint(color: String): Icon = nope()
}

/** A container: holds children added with [add]. */
@Suppress("unused")
open class Container : Widget() {
    fun add(child: Any): Container = nope()
    fun gap(dp: Int): Container = nope()
    fun center(): Container = nope()
    fun onSelect(cb: VeloFunction): Container = nope()
    fun select(index: Int): Container = nope()
}

@Suppress("unused")
class Surface : Container() {
    fun elevation(dp: Int): Surface = nope()
    fun border(width: Int, color: String): Surface = nope()
}

@Suppress("unused")
class Drawer : Container() {
    fun drawerContent(panel: Any): Drawer = nope()
    fun openDrawer(on: Boolean): Drawer = nope()
    fun isDrawerOpen(): Boolean = nope()
}

@Suppress("unused")
class Slider : Widget() {
    fun range(min: Int, max: Int): Slider = nope()
    fun slide(v: Int): Slider = nope()
    fun position(): Int = nope()
    fun onSlide(cb: VeloFunction): Slider = nope()
}

@Suppress("unused")
class Progress : Widget() {
    fun progress(pct: Int): Progress = nope()
    fun indeterminate(on: Boolean): Progress = nope()
}

@Suppress("unused")
class ListView : Widget() {
    fun items(rows: List<String>): ListView = nope()
    fun onItemClick(cb: VeloFunction): ListView = nope()
}

@Suppress("unused")
class Tabs : Widget() {
    fun tab(label: String): Tabs = nope()
    fun onSelect(cb: VeloFunction): Tabs = nope()
    fun select(index: Int): Tabs = nope()
}

@Suppress("unused")
class Nav : Widget() {
    fun item(label: String, icon: String): Nav = nope()
    fun onSelect(cb: VeloFunction): Nav = nope()
    fun select(index: Int): Nav = nope()
}

@Suppress("unused")
class Canvas : Widget() {
    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int): Shape = nope()
    fun drawRect(x: Int, y: Int, w: Int, h: Int): Shape = nope()
    fun drawRoundRect(x: Int, y: Int, w: Int, h: Int, r: Int): Shape = nope()
    fun drawCircle(cx: Int, cy: Int, r: Int): Shape = nope()
    fun drawOval(x: Int, y: Int, w: Int, h: Int): Shape = nope()
    fun drawArc(x: Int, y: Int, w: Int, h: Int, start: Int, sweep: Int): Shape = nope()
    fun drawPie(x: Int, y: Int, w: Int, h: Int, start: Int, sweep: Int): Shape = nope()
    fun drawPath(spec: String): Shape = nope()
    fun drawPoints(spec: String, mode: String): Shape = nope()
    fun drawText(x: Int, y: Int, s: String, size: Int): Shape = nope()
    fun clear(): Canvas = nope()
    fun aspectRatio(w: Int, h: Int): Canvas = nope()
    fun onTap(cb: VeloFunction): Canvas = nope()
    fun onPointerDown(cb: VeloFunction): Canvas = nope()
    fun onPointerMove(cb: VeloFunction): Canvas = nope()
    fun onPointerUp(cb: VeloFunction): Canvas = nope()
}

@Suppress("unused")
class Divider : Widget() {
    fun thickness(dp: Int): Divider = nope()
}

@Suppress("unused")
class Spacer : Widget()

/**
 * Compile-time mirror of the Android `Shape` native — the handle a canvas `draw*` call
 * returns, for styling that primitive's paint.
 */
@Suppress("unused")
class Shape {
    fun color(spec: String): Shape = nope()
    fun fill(): Shape = nope()
    fun stroke(widthDp: Int): Shape = nope()
    fun alpha(percent: Int): Shape = nope()
    fun cap(spec: String): Shape = nope()
    fun join(spec: String): Shape = nope()
    fun gradient(x0: Int, y0: Int, x1: Int, y1: Int, from: String, to: String): Shape = nope()
}

@Suppress("unused")
class Ui {
    // text & buttons
    fun text(s: String): Text = nope()
    fun button(s: String): Button = nope()
    fun tonalButton(s: String): Button = nope()
    fun outlinedButton(s: String): Button = nope()
    fun textButton(s: String): Button = nope()

    // inputs & selection
    fun field(hint: String): Field = nope()
    fun toggle(label: String): Toggle = nope()
    fun check(label: String): Toggle = nope()
    fun radio(label: String): Toggle = nope()
    fun slider(min: Int, max: Int): Slider = nope()
    fun chip(label: String): Chip = nope()

    // containers
    fun card(): Surface = nope()
    fun surface(): Surface = nope()
    fun column(): Container = nope()
    fun row(): Container = nope()
    fun box(): Container = nope()
    fun scroll(): Container = nope()
    fun flowRow(): Container = nope()
    fun flowColumn(): Container = nope()
    fun radioGroup(): Container = nope()
    fun drawer(): Drawer = nope()

    // feedback & indicators
    fun progress(): Progress = nope()
    fun spinner(): Progress = nope()
    fun list(): ListView = nope()
    fun lazyRow(): ListView = nope()
    fun canvas(): Canvas = nope()
    fun tabs(): Tabs = nope()
    fun appBar(title: String): AppBar = nope()
    fun listItem(headline: String): ListItem = nope()

    // icons & small widgets
    fun icon(name: String): Icon = nope()
    fun iconButton(name: String): Button = nope()
    fun fab(name: String): Button = nope()
    fun smallFab(name: String): Button = nope()
    fun extendedFab(label: String, name: String): Button = nope()
    fun divider(): Divider = nope()
    fun spacer(): Spacer = nope()

    // navigation components
    fun navBar(): Nav = nope()
    fun navRail(): Nav = nope()
    fun menu(anchor: Any, items: List<String>, onSelect: VeloFunction) { nope() }

    // navigation (screen stack)
    fun open(root: Any): Container = nope()
    fun close() { nope() }
    fun onBack(cb: VeloFunction) { nope() }

    // dialogs & snackbars
    fun dialog(title: String, message: String, button: String, onClick: VeloFunction) { nope() }
    fun confirm(title: String, message: String, yes: String, onYes: VeloFunction, no: String, onNo: VeloFunction) { nope() }
    fun snackbar(message: String) { nope() }
    fun snackAction(message: String, action: String, onAction: VeloFunction) { nope() }

    // bottom sheet
    fun sheet(content: Any) { nope() }
    fun dismissSheet() { nope() }
}
