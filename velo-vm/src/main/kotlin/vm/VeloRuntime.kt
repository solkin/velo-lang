package vm

import core.NativeLinker
import core.NativeRegistry
import core.SerializedProgram
import vm.actors.ActorHandle
import vm.actors.ActorRuntime
import vm.actors.Dispatcher
import kotlin.reflect.KClass

/**
 * The embedding API of the Velo VM: register native classes, then run a
 * compiled [SerializedProgram] — no compiler on the classpath.
 *
 * ```kotlin
 * val runtime = VeloRuntime()
 *     .register(MyNativeClass::class)
 *
 * runtime.run(Bytecode.read(File("app.vbc")))          // blocking, CLI-style
 * val program = runtime.start(prog, dispatcher)        // non-blocking, embedded
 * ```
 *
 * Share one [NativeRegistry] between a `VeloCompiler` and a `VeloRuntime`
 * when compiling and running in the same process.
 *
 * Enable profiling via system property `-Dvelo.profile=true` or
 * [enableProfiling].
 */
class VeloRuntime(
    private val nativeRegistry: NativeRegistry = NativeRegistry(),
) {

    private var profilingEnabled = System.getProperty("velo.profile")?.toBoolean() ?: false

    /**
     * Register a native class; the Velo name is the JVM simple class name.
     */
    fun register(jvmClass: KClass<*>): VeloRuntime {
        nativeRegistry.register(jvmClass)
        return this
    }

    /**
     * Register a native class using a Java Class.
     */
    fun register(jvmClass: Class<*>): VeloRuntime {
        nativeRegistry.register(jvmClass)
        return this
    }

    /**
     * Register a native class under a custom Velo name.
     */
    fun register(veloName: String, jvmClass: KClass<*>): VeloRuntime {
        nativeRegistry.register(veloName, jvmClass)
        return this
    }

    /**
     * Register a native class under a custom Velo name using a Java Class.
     */
    fun register(veloName: String, jvmClass: Class<*>): VeloRuntime {
        nativeRegistry.register(veloName, jvmClass)
        return this
    }

    /**
     * Enable VM profiling programmatically.
     */
    fun enableProfiling(): VeloRuntime {
        profilingEnabled = true
        return this
    }

    /**
     * The registry programs are linked against — for advanced usage.
     */
    fun getNativeRegistry(): NativeRegistry = nativeRegistry

    /**
     * Run a compiled program to completion on the calling thread.
     */
    fun run(program: SerializedProgram) {
        val profiler = VMProfiler(enabled = profilingEnabled)
        val vm = VM(nativeRegistry, profiler)
        vm.load(program)
        vm.run()
    }

    /**
     * Start a compiled program on a host-provided serial executor without
     * blocking the calling thread — the embedding entry point.
     *
     * The program's main context (and therefore every callback it hands
     * out) executes on [dispatcher]; pass your platform's main-thread
     * executor (Android Handler, Swing EDT) to pin Velo to the UI thread:
     *
     * ```kotlin
     * val program = runtime.start(prog, Dispatcher.from { handler.post(it) })
     * ...
     * program.stop()
     * ```
     *
     * The program stays serviceable after its main frame completes for as
     * long as callbacks are registered with the host; call [VeloProgram.stop]
     * to tear it down deterministically. Runtime failures stop the program
     * and are reported via [VeloProgram.failure].
     */
    fun start(program: SerializedProgram, dispatcher: Dispatcher): VeloProgram {
        val frameLoader = GeneralFrameLoader(program.frames.associateBy { it.num })
        val natives = NativeLinker.link(program.natives, nativeRegistry)
        val actorRuntime = ActorRuntime()
        val main = ActorHandle.main(
            runtime = actorRuntime,
            sharedFrameLoader = frameLoader,
            sharedNativeRegistry = nativeRegistry,
            sharedNatives = natives,
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
