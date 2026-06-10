import compiler.CompilerFrame
import compiler.Context
import compiler.parser.FileInput
import compiler.parser.Parser
import compiler.parser.TokenStream
import utils.SerializedFrame
import vm.GeneralFrameLoader
import vm.HaltException
import vm.NativeRegistry
import vm.SimpleParser
import vm.VM
import vm.VMProfiler
import vm.actors.ActorHandle
import vm.actors.ActorRuntime
import vm.actors.Dispatcher
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
 * 
 * Enable profiling via system property: -Dvelo.profile=true
 * Or programmatically: runtime.enableProfiling()
 */
class VeloRuntime {
    
    private val nativeRegistry = NativeRegistry()
    private var profilingEnabled = System.getProperty("velo.profile")?.toBoolean() ?: false
    
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
            .register(Socket::class)
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
     * Enable VM profiling programmatically.
     */
    fun enableProfiling(): VeloRuntime {
        profilingEnabled = true
        return this
    }
    
    /**
     * Disable VM profiling.
     */
    fun disableProfiling(): VeloRuntime {
        profilingEnabled = false
        return this
    }
    
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
        val node = parser.parse()

        val ctx = Context(
            parent = null,
            frame = CompilerFrame(num = 0, ops = mutableListOf(), vars = mutableMapOf(), varCounter = AtomicInteger()),
            frameCounter = AtomicInteger(),
        )
        try {
            node.compile(ctx)
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
        return null
    }
    
    /**
     * Run compiled frames.
     */
    fun run(frames: List<SerializedFrame>) {
        val profiler = VMProfiler(enabled = profilingEnabled)
        val vm = VM(nativeRegistry, profiler)
        vm.load(SimpleParser(frames))
        vm.run()
    }
    
    /**
     * Compile and run a Velo source file.
     *
     * @param path Path to the .vel file
     */
    fun runFile(path: String) {
        val frames = compile(path) ?: return
        run(frames)
    }

    /**
     * Start compiled frames on a host-provided serial executor without
     * blocking the calling thread — the embedding entry point.
     *
     * The program's main context (and therefore every callback it hands
     * out) executes on [dispatcher]; pass your platform's main-thread
     * executor (Android Handler, Swing EDT) to pin Velo to the UI thread:
     *
     * ```kotlin
     * val program = runtime.start(frames, Dispatcher.from { handler.post(it) })
     * ...
     * program.stop()
     * ```
     *
     * The program stays serviceable after its main frame completes for as
     * long as callbacks are registered with the host; call [VeloProgram.stop]
     * to tear it down deterministically. Runtime failures stop the program
     * and are reported via [VeloProgram.failure].
     */
    fun start(frames: List<SerializedFrame>, dispatcher: Dispatcher): VeloProgram {
        val frameLoader = GeneralFrameLoader(frames.associateBy { it.num })
        val actorRuntime = ActorRuntime()
        val main = ActorHandle.main(
            runtime = actorRuntime,
            sharedFrameLoader = frameLoader,
            sharedNativeRegistry = nativeRegistry,
            dispatcher = dispatcher,
        )
        actorRuntime.onFatal = { ex ->
            System.err.println("!! Velo program failed: ${ex.message ?: ex}")
            actorRuntime.shutdownAll()
        }
        main.requestMain(frameNum = 0, onFailure = { ex ->
            when (ex) {
                is HaltException -> actorRuntime.shutdownAll()
                else -> actorRuntime.raiseFatal(ex)
            }
        })
        return VeloProgram(actorRuntime)
    }

    /**
     * Get the native registry for advanced usage.
     */
    fun getNativeRegistry(): NativeRegistry = nativeRegistry
}

/**
 * Handle to a program started via [VeloRuntime.start].
 */
class VeloProgram internal constructor(
    private val actorRuntime: ActorRuntime,
) {
    /** Cooperatively stop the main context and every live actor. */
    fun stop() {
        actorRuntime.shutdownAll()
    }

    /** The failure that terminated the program, if any. */
    fun failure(): Throwable? = actorRuntime.fatalOrNull()
}
