package compiler

import compiler.parser.FileInput
import compiler.parser.Parser
import compiler.parser.TokenStream
import core.NativeRegistry
import core.SerializedFrame
import core.SerializedProgram
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * The compilation API of Velo: turn `.vel` sources into a
 * [SerializedProgram] (bytecode + native pool) — no VM on the classpath.
 *
 * ```kotlin
 * val compiler = VeloCompiler()
 *     .register(MyNativeClass::class)
 *
 * val program = compiler.compile("app.vel") ?: return
 * Bytecode.write(program, File("app.vbc"))
 * ```
 *
 * Native classes registered here are visible to the program being
 * compiled: their types are synthesized from the registry, and every
 * native entry point used by the code is interned into the program's
 * native pool. Share one [NativeRegistry] with a `VeloRuntime` when
 * compiling and running in the same process.
 */
class VeloCompiler(
    private val nativeRegistry: NativeRegistry = NativeRegistry(),
) {

    /**
     * Register a native class; the Velo name is the JVM simple class name.
     */
    fun register(jvmClass: KClass<*>): VeloCompiler {
        nativeRegistry.register(jvmClass)
        return this
    }

    /**
     * Register a native class using a Java Class.
     */
    fun register(jvmClass: Class<*>): VeloCompiler {
        nativeRegistry.register(jvmClass)
        return this
    }

    /**
     * Register a native class under a custom Velo name.
     */
    fun register(veloName: String, jvmClass: KClass<*>): VeloCompiler {
        nativeRegistry.register(veloName, jvmClass)
        return this
    }

    /**
     * Register a native class under a custom Velo name using a Java Class.
     */
    fun register(veloName: String, jvmClass: Class<*>): VeloCompiler {
        nativeRegistry.register(veloName, jvmClass)
        return this
    }

    /**
     * The registry programs are compiled against — for advanced usage.
     */
    fun getNativeRegistry(): NativeRegistry = nativeRegistry

    /**
     * Compile a Velo source file. Includes are resolved relative to the
     * file's directory.
     *
     * @return the compiled program, or `null` if compilation failed (the
     *   error is printed).
     */
    fun compile(path: String): SerializedProgram? {
        val file = File(path)
        return compile(FileInput(dir = file.parent).apply {
            load(name = file.name)
        })
    }

    /**
     * Compile from a [FileInput] source.
     */
    fun compile(input: FileInput): SerializedProgram? {
        val shared = CompilerShared(nativeRegistry)
        val ctx = Context(
            parent = null,
            frame = CompilerFrame(num = 0, ops = mutableListOf(), vars = mutableMapOf(), varCounter = AtomicInteger()),
            frameCounter = AtomicInteger(),
            shared = shared,
        )
        try {
            compiler.nodes.TypeRegistry.reset()
            // Parse inside the try too, so import/collision errors report the same
            // way as type errors instead of propagating as raw exceptions.
            val stream = TokenStream(input)
            val rootDir = input.dir?.let { java.io.File(it) }
            val parser = Parser(stream, depLoader = input, nativeRegistry = nativeRegistry, rootDir = rootDir)
            val node = parser.parse()
            node.compile(ctx)
            return SerializedProgram(
                natives = shared.nativePool.toList(),
                frames = ctx.frames().map {
                    // A frame's local slots span [varBase, varCounter): counting
                    // from the counter (not the name map) captures slots declared
                    // in inline blocks, whose names live in their own scopes.
                    SerializedFrame(
                        num = it.num,
                        ops = it.ops,
                        vars = (it.varBase until it.varCounter.get()).toList()
                    )
                },
                dataClasses = shared.dataClasses.toList(),
                classMethods = shared.classMethods.toList(),
            )
        } catch (ex: Throwable) {
            println("!! Compilation failed: ${ex.message}")
        }
        return null
    }
}
