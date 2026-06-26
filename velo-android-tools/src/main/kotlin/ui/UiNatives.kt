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

    // content / text
    fun text(s: String): View = nope()
    fun hint(s: String): View = nope()
    fun value(): String = nope()
    fun textSize(sp: Int): View = nope()
    fun bold(): View = nope()
    fun enabled(on: Boolean): View = nope()

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
    fun tabs(): View = nope()
    fun appBar(title: String): View = nope()

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
