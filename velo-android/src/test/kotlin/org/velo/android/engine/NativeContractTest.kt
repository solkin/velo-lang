package org.velo.android.engine

import core.NativeRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test

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

    @Test
    fun allDefaultNativesAreMappable() {
        val registry = registry()
        for (name in listOf("Terminal", "Time", "FileSystem", "Http", "Socket")) {
            assertNotNull("native '$name' must build a descriptor", registry.descriptor(name))
        }
    }
}
