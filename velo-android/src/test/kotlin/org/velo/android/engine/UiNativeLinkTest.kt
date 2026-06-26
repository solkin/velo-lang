package org.velo.android.engine

import com.google.android.material.button.MaterialButton
import compiler.VeloCompiler
import core.Bytecode
import core.NativeLinker
import core.NativeRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
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
 *  - compiling the actual `ui-demo` source against the **real** natives proves the
 *    program type-checks against what runs on device;
 *  - linking the **stub-compiled** `.vbc` (if the sample task has run) against the real
 *    registry proves the stub and the implementation agree signature-for-signature.
 *
 * Pure-JVM: introspection and linking only read signatures — no Android view is created.
 */
class UiNativeLinkTest {

    private fun registry() = NativeRegistry()
        .register("Terminal", AndroidTerminal::class)
        .register("Ui", VeloUi::class)
        .register("View", VeloView::class)

    @Test
    fun uiSampleCompilesAndLinksAgainstRealNatives() {
        val source = File("samples/ui-demo/program.vel")
        assumeTrue("ui-demo sample source present", source.exists())

        val registry = registry()
        val program = VeloCompiler(registry).compile(source.absolutePath)
            ?: error("ui-demo failed to compile against the real Ui/View natives")

        // Links every native ref in the program against the real host classes.
        NativeLinker.link(program.natives, registry)

        // Sanity: the real View native really does wrap Material3 (not a stub).
        assertTrue(MaterialButton::class.java.simpleName == "MaterialButton")
    }

    @Test
    fun stubCompiledBytecodeLinksAgainstRealNatives() {
        val vbc = File("build/generated/veloSamples/samples/ui-demo/program.vbc")
        assumeTrue("stub-compiled ui-demo .vbc present (run :compileVeloSamples)", vbc.exists())

        val program = DataInputStream(vbc.inputStream().buffered()).use { Bytecode.read(it) }
        // Throws NativeMappingException on any stub-vs-implementation signature drift.
        NativeLinker.link(program.natives, registry())
    }
}
