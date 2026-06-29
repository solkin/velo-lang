package org.velo.android.engine

import org.velo.android.engine.ui.registerUiNatives
import org.velo.android.engine.ui.uiWidgetNames

import core.NativeRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.velo.android.engine.ui.Colors
import org.velo.android.engine.ui.Icons
import org.velo.android.engine.ui.Shape
import org.velo.android.engine.ui.TextStyles
import org.velo.android.engine.ui.VeloUi

/**
 * Validates that the app's host natives map cleanly onto the Velo native contract —
 * the same descriptor introspection the VM runs when linking a `.vbc`. If a method
 * signature were unmappable, `descriptor()` would throw, so a `.vbc` written against
 * the standard natives (Terminal/Time/FileSystem/Http/Socket) links against these.
 *
 * Pure-JVM: the native classes touch no Android APIs, so this runs as a host unit test.
 */
class NativeContractTest {

    private fun registry() = NativeRegistry()
        .register("Terminal", AndroidTerminal::class)
        .register("Time", VeloTime::class)
        .register("FileSystem", VeloFileSystem::class)
        .register("Http", VeloHttp::class)
        .register("Socket", VeloSocket::class)
        .registerUiNatives()
        .register("Colors", Colors::class)
        .register("Icons", Icons::class)
        .register("TextStyles", TextStyles::class)

    @Test
    fun allDefaultNativesAreMappable() {
        val registry = registry()
        for (name in listOf("Terminal", "Time", "FileSystem", "Http", "Socket", "Ui", "Shape", "Colors", "Icons", "TextStyles") + uiWidgetNames) {
            assertNotNull("native '$name' must build a descriptor", registry.descriptor(name))
        }
    }
}
