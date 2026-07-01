package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import vm2.VeloRuntime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The 64-bit `long` type. The motivating use case is faithful 16.16 fixed-point
 * arithmetic (a Gravity Defied physics port): products of two 16.16 values need
 * a 64-bit intermediate before the `>> 16` renormalisation, which a 32-bit int
 * cannot hold. These tests pin the literal rules, the int<->long coercions and
 * the wrapping/overflow behaviour the port relies on.
 */
class LongTypeTest {

    private fun compile(source: String): core.SerializedProgram? {
        val registry = NativeRegistry().register(Terminal::class)
        val velFile = File.createTempFile("long", ".vel").apply { writeText(source); deleteOnExit() }
        return try {
            VeloCompiler(registry).compile(velFile.path)
        } catch (e: Throwable) {
            null
        } finally {
            velFile.delete()
        }
    }

    private fun run(source: String): String {
        val registry = NativeRegistry().register(Terminal::class)
        val velFile = File.createTempFile("long", ".vel").apply { writeText(source); deleteOnExit() }
        val program = VeloCompiler(registry).compile(velFile.path) ?: error("compile failed")
        velFile.delete()
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try {
            VeloRuntime(registry).run(program)
        } finally {
            System.setOut(old)
        }
        return baos.toString().trimEnd('\n')
    }

    @Test
    fun `a 16 dot 16 product needs a 64-bit intermediate`() {
        // 0x40000 == 4.0 in 16.16; 4.0 * 4.0 = 16.0 == 0x100000 (1048576).
        // The product 0x40000 * 0x40000 == 2^36 overflows a 32-bit int, so this
        // is only correct because the multiply happens in long.
        assertEquals(
            "1048576",
            run(
                """
                Terminal term = new Terminal();
                long a = 262144;
                long b = 262144;
                long p = (a * b).shr(16);
                term.println(p.str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `an int widens into a long slot implicitly`() {
        assertEquals(
            "300000000000",
            run(
                """
                Terminal term = new Terminal();
                int n = 3;
                long big = n;
                long r = big * 100000000000;
                term.println(r.str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `a decimal literal past the int range is a long`() {
        // 2^40; must not be silently truncated to a 32-bit int.
        assertEquals(
            "1099511627776",
            run(
                """
                Terminal term = new Terminal();
                long x = 1099511627776;
                term.println(x.str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `a wide hex literal is a long while a 32-bit one stays int`() {
        // 0x1000000000000 == 2^48 (long); 0xFFFFFFFF keeps the Java int bit
        // pattern (-1) and stringifies as an int.
        assertEquals(
            "281474976710656\n-1",
            run(
                """
                Terminal term = new Terminal();
                long wide = 0x1000000000000;
                term.println(wide.str());
                int mask = 0xFFFFFFFF;
                term.println(mask.str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `long shifts left in 64 bits`() {
        // 1 << 40 stays a long; a 32-bit int shift would give 0.
        assertEquals(
            "1099511627776",
            run(
                """
                Terminal term = new Terminal();
                long one = 1;
                long shifted = one.shl(40);
                term.println(shifted.str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `explicit narrowing takes the low 32 bits`() {
        // 2^32 + 7 narrowed to int wraps to 7.
        assertEquals(
            "7",
            run(
                """
                Terminal term = new Terminal();
                long v = 4294967303;
                int low = v.int();
                term.println(low.str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `assigning a long to an int without conversion is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                long v = 5;
                int n = v;
                term.println(n.str());
                """.trimIndent()
            )
        )
    }
}
