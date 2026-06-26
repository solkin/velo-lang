package ui

import core.VeloFunction

/**
 * Compile-time signature mirrors of the Android `Ui` / `View` natives
 * (`org.velo.android.engine.ui.VeloUi` / `VeloView`).
 *
 * The sample compiler resolves and type-checks native calls against registered host
 * classes, but the real Material3 implementations live in the Android app and pull in
 * `android.jar`, which a pure-JVM build can't load. These stubs carry the exact same
 * Velo-facing signatures so a `.vel` UI program compiles to `.vbc` here, then links
 * against the real implementations at runtime on Android (the linker matches by Velo
 * name + signature). The bodies are never executed; if a UI program is run headless
 * they fail loudly rather than pretending to draw.
 *
 * Keep these in lockstep with the Android classes — a signature drift surfaces as a
 * link error on device.
 */
private fun nope(): Nothing =
    throw UnsupportedOperationException("Velo UI natives are only available on the Android host")

@Suppress("unused")
class View {
    // containers / layout
    fun add(child: View): View = nope()
    fun padding(dp: Int): View = nope()
    fun gap(dp: Int): View = nope()
    fun center(): View = nope()
    fun fillWidth(): View = nope()
    fun fillHeight(): View = nope()
    fun width(dp: Int): View = nope()
    fun height(dp: Int): View = nope()
    fun weight(w: Int): View = nope()

    // common visual modifiers (any kind)
    fun background(color: String): View = nope()
    fun corner(dp: Int): View = nope()
    fun paddingXY(h: Int, v: Int): View = nope()
    fun align(spec: String): View = nope()

    // content / text
    fun text(s: String): View = nope()
    fun color(spec: String): View = nope()
    fun hint(s: String): View = nope()
    fun value(): String = nope()
    fun textSize(sp: Int): View = nope()
    fun bold(): View = nope()
    fun style(token: String): View = nope()
    fun textAlign(spec: String): View = nope()
    fun maxLines(n: Int): View = nope()
    fun strikethrough(): View = nope()
    fun enabled(on: Boolean): View = nope()
    fun visible(on: Boolean): View = nope()

    // text field extras
    fun placeholder(s: String): View = nope()
    fun error(message: String): View = nope()
    fun keyboardType(type: String): View = nope()
    fun onSubmit(cb: VeloFunction): View = nope()
    fun onFocusChange(cb: VeloFunction): View = nope()

    // icons & small widgets
    fun icon(name: String): View = nope()
    fun iconOnly(): View = nope()
    fun tint(color: String): View = nope()
    fun checkable(on: Boolean): View = nope()
    fun thickness(dp: Int): View = nope()

    // surface & list item
    fun elevation(dp: Int): View = nope()
    fun border(width: Int, color: String): View = nope()
    fun supporting(s: String): View = nope()
    fun leading(name: String): View = nope()
    fun trailing(name: String): View = nope()

    // navigation components
    fun item(label: String, icon: String): View = nope()
    fun drawerContent(panel: View): View = nope()
    fun openDrawer(on: Boolean): View = nope()
    fun isDrawerOpen(): Boolean = nope()
    fun badge(text: String): View = nope()
    fun badgeDot(): View = nope()

    // toggles
    fun checked(on: Boolean): View = nope()
    fun isChecked(): Boolean = nope()

    // slider
    fun range(min: Int, max: Int): View = nope()
    fun slide(v: Int): View = nope()
    fun position(): Int = nope()

    // progress
    fun progress(pct: Int): View = nope()
    fun indeterminate(on: Boolean): View = nope()

    // events
    fun onClick(cb: VeloFunction): View = nope()
    fun onLongClick(cb: VeloFunction): View = nope()
    fun onResize(cb: VeloFunction): View = nope()
    fun onChange(cb: VeloFunction): View = nope()
    fun onToggle(cb: VeloFunction): View = nope()
    fun onSlide(cb: VeloFunction): View = nope()

    // list (RecyclerView)
    fun items(rows: List<String>): View = nope()
    fun onItemClick(cb: VeloFunction): View = nope()

    // tabs (TabLayout)
    fun tab(label: String): View = nope()
    fun onSelect(cb: VeloFunction): View = nope()
    fun select(index: Int): View = nope()

    // app bar
    fun onNav(cb: VeloFunction): View = nope()
    fun action(title: String, icon: String, cb: VeloFunction): View = nope()
    fun actionIcon(title: String, icon: String): View = nope()

    // canvas (drawing)
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
    fun aspectRatio(w: Int, h: Int): View = nope()
    fun clear(): View = nope()
    fun onTap(cb: VeloFunction): View = nope()
    fun onPointerDown(cb: VeloFunction): View = nope()
    fun onPointerMove(cb: VeloFunction): View = nope()
    fun onPointerUp(cb: VeloFunction): View = nope()
}

/**
 * Compile-time mirror of the Android `Shape` native (`org.velo.android.engine.ui.Shape`) —
 * the handle a canvas `draw*` call returns, for styling that primitive's paint.
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
    // widget factories
    fun text(s: String): View = nope()
    fun button(s: String): View = nope()
    fun tonalButton(s: String): View = nope()
    fun outlinedButton(s: String): View = nope()
    fun textButton(s: String): View = nope()
    fun field(hint: String): View = nope()
    fun toggle(label: String): View = nope()
    fun check(label: String): View = nope()
    fun slider(min: Int, max: Int): View = nope()
    fun card(): View = nope()
    fun column(): View = nope()
    fun row(): View = nope()
    fun box(): View = nope()
    fun scroll(): View = nope()
    fun progress(): View = nope()
    fun spinner(): View = nope()
    fun list(): View = nope()
    fun canvas(): View = nope()
    fun tabs(): View = nope()
    fun appBar(title: String): View = nope()

    // icons & small widgets
    fun icon(name: String): View = nope()
    fun iconButton(name: String): View = nope()
    fun fab(name: String): View = nope()
    fun smallFab(name: String): View = nope()
    fun extendedFab(label: String, name: String): View = nope()
    fun chip(label: String): View = nope()
    fun divider(): View = nope()
    fun spacer(): View = nope()
    fun radio(label: String): View = nope()
    fun radioGroup(): View = nope()

    // containers
    fun surface(): View = nope()
    fun flowRow(): View = nope()
    fun flowColumn(): View = nope()
    fun lazyRow(): View = nope()
    fun listItem(headline: String): View = nope()

    // navigation components
    fun navBar(): View = nope()
    fun navRail(): View = nope()
    fun drawer(): View = nope()
    fun menu(anchor: View, items: List<String>, onSelect: VeloFunction) { nope() }

    // navigation
    fun open(root: View): View = nope()
    fun close() { nope() }
    fun onBack(cb: VeloFunction) { nope() }

    // dialogs & snackbars
    fun dialog(title: String, message: String, button: String, onClick: VeloFunction) { nope() }
    fun confirm(title: String, message: String, yes: String, onYes: VeloFunction, no: String, onNo: VeloFunction) { nope() }
    fun snackbar(message: String) { nope() }
    fun snackAction(message: String, action: String, onAction: VeloFunction) { nope() }

    // bottom sheet
    fun sheet(content: View) { nope() }
    fun dismissSheet() { nope() }
}
