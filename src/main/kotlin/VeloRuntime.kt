import compiler.CompilerFrame
import compiler.Context
import compiler.parser.FileInput
import compiler.parser.Parser
import compiler.parser.TokenStream
import utils.SerializedFrame
import vm.NativeRegistry
import vm.SimpleParser
import vm.VM
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * VeloRuntime - Public API for running Velo Lang programs.
 * 
 * Example usage:
 * ```kotlin
 * val runtime = VeloRuntime()
 *     .register(MyNativeClass::class)
 *     .register("CustomName", AnotherClass::class)
 * 
 * runtime.runFile("path/to/script.vel")
 * ```
 */
class VeloRuntime {
    
    private val nativeRegistry = NativeRegistry()
    
    init {
        // Register standard native classes by default
        registerDefaults()
    }
    
    /**
     * Register default native classes that come with Velo Lang
     */
    private fun registerDefaults() {
        nativeRegistry
            .register(Terminal::class)
            .register(Time::class)
            .register(FileSystem::class)
            .register(Http::class)
    }
    
    /**
     * Register a native class using Kotlin KClass.
     * The Velo name will be the same as the JVM class name.
     */
    fun register(jvmClass: KClass<*>): VeloRuntime {
        nativeRegistry.register(jvmClass)
        return this
    }
    
    /**
     * Register a native class using Java Class.
     * The Velo name will be the same as the JVM class name.
     */
    fun register(jvmClass: Class<*>): VeloRuntime {
        nativeRegistry.register(jvmClass)
        return this
    }
    
    /**
     * Register a native class with a custom Velo name.
     */
    fun register(veloName: String, jvmClass: KClass<*>): VeloRuntime {
        nativeRegistry.register(veloName, jvmClass)
        return this
    }
    
    /**
     * Register a native class with a custom Velo name using Java Class.
     */
    fun register(veloName: String, jvmClass: Class<*>): VeloRuntime {
        nativeRegistry.register(veloName, jvmClass)
        return this
    }
    
    /**
     * Check if a class is registered by Velo name.
     */
    fun isRegistered(veloName: String): Boolean = nativeRegistry.isRegistered(veloName)
    
    /**
     * Check if a JVM class is registered.
     */
    fun isRegistered(jvmClass: Class<*>): Boolean = nativeRegistry.isRegistered(jvmClass)
    
    /**
     * Get all registered class names.
     */
    fun getRegisteredClasses(): Set<String> = nativeRegistry.getAllNames()
    
    /**
     * Compile a Velo source file.
     * 
     * @param path Path to the .vel file
     * @return Compiled frames or null if compilation failed
     */
    fun compile(path: String): List<SerializedFrame>? {
        val file = File(path)
        return compile(FileInput(dir = file.parent).apply {
            load(name = file.name)
        })
    }
    
    /**
     * Compile from a FileInput source.
     */
    fun compile(input: FileInput): List<SerializedFrame>? {
        val stream = TokenStream(input)
        val parser = Parser(stream, depLoader = input)

        var time = System.currentTimeMillis()
        val node = parser.parse()
        var elapsed = System.currentTimeMillis() - time
        println("Parsed in $elapsed ms")

        val ctx = Context(
            parent = null,
            frame = CompilerFrame(num = 0, ops = mutableListOf(), vars = mutableMapOf(), varCounter = AtomicInteger()),
            frameCounter = AtomicInteger(),
        )
        try {
            time = System.currentTimeMillis()
            node.compile(ctx)
            elapsed = System.currentTimeMillis() - time
            val opsCount = ctx.frames().map { it.ops.size }.reduce { acc, element ->
                acc + element
            }
            println("Compiled in $elapsed ms [${ctx.frames().size} frames, $opsCount ops]")
            return ctx.frames().map {
                SerializedFrame(
                    num = it.num,
                    ops = it.ops,
                    vars = it.vars.map { i -> i.value.index }
                )
            }
        } catch (ex: Throwable) {
            println("!! Compilation failed: ${ex.message}")
        }
        println()
        return null
    }
    
    /**
     * Run compiled frames.
     */
    fun run(frames: List<SerializedFrame>) {
        val vm = VM(nativeRegistry)
        vm.load(SimpleParser(frames))
        vm.run()
    }
    
    /**
     * Compile and run a Velo source file.
     * 
     * @param path Path to the .vel file
     */
    fun runFile(path: String) {
        val frames = compile(path)
        if (frames != null) {
            println()
            run(frames)
        }
    }
    
    /**
     * Get the native registry for advanced usage.
     */
    fun getNativeRegistry(): NativeRegistry = nativeRegistry
}

