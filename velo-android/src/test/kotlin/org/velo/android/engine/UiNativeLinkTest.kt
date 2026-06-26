package org.velo.android.engine

import com.google.android.material.button.MaterialButton
import compiler.VeloCompiler
import core.Bytecode
import core.NativeLinker
import core.NativeRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.velo.android.engine.ui.Colors
import org.velo.android.engine.ui.Icons
import org.velo.android.engine.ui.Shape
import org.velo.android.engine.ui.TextStyles
import org.velo.android.engine.ui.VeloUi
import org.velo.android.engine.ui.VeloView
import java.io.DataInputStream
import java.io.File

/**
 * Guards the device-side contract of the Material3 UI natives.
 *
 * `.vel` UI samples are compiled at build time against the pure-JVM `Ui`/`View`
 * signature stubs in `velo-cli` (no `android.jar`), but on device they link against the
 * real [VeloUi]/[VeloView]. If those signatures ever drift apart, the `.vbc` would fail
 * to link on the phone. This test catches that here:
 *
 *  - compiling the actual `gallery` source against the **real** natives proves the
 *    program type-checks against what runs on device;
 *  - linking the **stub-compiled** `.vbc` (if the sample task has run) against the real
 *    registry proves the stub and the implementation agree signature-for-signature.
 *
 * Pure-JVM: introspection and linking only read signatures — no Android view is created.
 */
class UiNativeLinkTest {

    private fun registry() = NativeRegistry()
        .register("Terminal", AndroidTerminal::class)
        .register("Time", VeloTime::class)
        .register("FileSystem", VeloFileSystem::class)
        .register("Http", VeloHttp::class)
        .register("Socket", VeloSocket::class)
        .register("Ui", VeloUi::class)
        .register("View", VeloView::class)
        .register("Shape", Shape::class)
        .register("Colors", Colors::class)
        .register("Icons", Icons::class)
        .register("TextStyles", TextStyles::class)

    @Test
    fun uiSampleCompilesAndLinksAgainstRealNatives() {
        val source = File("samples/gallery/program.vel")
        assumeTrue("gallery sample source present", source.exists())

        val registry = registry()
        val program = VeloCompiler(registry).compile(source.absolutePath)
            ?: error("gallery failed to compile against the real Ui/View natives")

        // Links every native ref in the program against the real host classes.
        NativeLinker.link(program.natives, registry)

        // Sanity: the real View native really does wrap Material3 (not a stub).
        assertTrue(MaterialButton::class.java.simpleName == "MaterialButton")
    }

    @Test
    fun stubCompiledBytecodeLinksAgainstRealNatives() {
        val samplesRoot = File("build/generated/veloSamples/samples")
        val vbcs = samplesRoot.listFiles()?.mapNotNull { dir ->
            dir.resolve("program.vbc").takeIf { it.exists() }
        }.orEmpty()
        assumeTrue("stub-compiled sample .vbc present (run :compileVeloSamples)", vbcs.isNotEmpty())

        // Every bundled sample's stub-compiled bytecode must link against the real natives —
        // catches any stub-vs-implementation signature drift (Ui/View/Shape) for all samples.
        for (vbc in vbcs) {
            val program = DataInputStream(vbc.inputStream().buffered()).use { Bytecode.read(it) }
            NativeLinker.link(program.natives, registry())
        }
    }
}
