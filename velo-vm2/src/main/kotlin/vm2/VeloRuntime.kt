package vm2

import core.Dispatcher
import core.DispatcherFactory
import core.NativeLinker
import core.NativeRegistry
import core.SerializedProgram

/**
 * The embedding entry point for velo-vm2: link a [SerializedProgram] against
 * the host's native classes and execute it.
 *
 * ```kotlin
 * VeloRuntime(NativeRegistry().register(Terminal::class))
 *     .actorPlacement { ThreadPerActorFactory }   // optional host threading
 *     .run(program) { loop -> host.keepAlive(loop) }
 * ```
 *
 * With no [actorPlacement] the program runs cooperatively on the calling
 * thread — deterministic and thread-free, the portable default. A
 * [DispatcherFactory] places each spawned actor on its own host dispatcher
 * (thread / pool) for real parallelism. Execution starts at frame 0.
 */
class VeloRuntime(private val natives: NativeRegistry = NativeRegistry()) {

    companion object {
        const val ENTRY_FRAME = 0
    }

    private var factory: DispatcherFactory? = null
    private var heap: Heap = NoHeap

    /** Place spawned actors on host dispatchers produced by [factory]. */
    fun actorPlacement(factory: () -> DispatcherFactory): VeloRuntime {
        this.factory = factory()
        return this
    }

    /**
     * Use the VM-owned [ManagedHeap] with a mark-sweep collector instead of
     * leaning on the host GC — the lifetime model a native (C / WASM) port
     * needs. Collects every [thresholdAllocs] allocations. The returned
     * [RunStats] then carries [MemoryStats].
     */
    fun managedHeap(thresholdAllocs: Int = 100_000): VeloRuntime {
        this.heap = ManagedHeap(thresholdAllocs)
        return this
    }

    /**
     * Plug in a custom [Heap] strategy — a host-specific collector for a native
     * port. The default is [NoHeap] (lean on the host GC); [managedHeap] is the
     * convenience shortcut for the built-in mark-sweep. The VM depends only on
     * the [Heap] interface, so any implementation works.
     */
    fun heap(heap: Heap): VeloRuntime {
        this.heap = heap
        return this
    }

    fun run(program: SerializedProgram): RunStats = run(program, null)

    /**
     * Run [program] to completion, pumping the event loop on the calling
     * thread. [onLoop], if given, receives a [LoopHandle] before the run starts
     * — a host (e.g. a UI showing a screen) can hold the loop open for
     * event-driven callbacks past the main fiber.
     */
    fun run(program: SerializedProgram, onLoop: ((LoopHandle) -> Unit)?): RunStats {
        val interp = interpreter(program, mainDispatcher = null)
        onLoop?.invoke(interp.loopHandle)
        return interp.run(ENTRY_FRAME)
    }

    /**
     * Launch [program] non-blocking with the main actor placed on
     * [mainDispatcher] (e.g. a host UI thread). Returns a [ProgramHandle] for
     * teardown; callbacks owned by the main context run on [mainDispatcher].
     */
    fun start(program: SerializedProgram, mainDispatcher: Dispatcher): ProgramHandle =
        interpreter(program, mainDispatcher).start(ENTRY_FRAME)

    private fun interpreter(program: SerializedProgram, mainDispatcher: Dispatcher?): Interpreter {
        val frames = program.frames.associate { it.num to FrameSpec(it) }
        val dataClasses = program.dataClasses.associateBy { it.frameNum }
        val methodTables = program.classMethods.associate { info ->
            info.frameNum to info.methods.associate { it.name to it.index }
        }
        val bound = NativeLinker.link(program.natives, natives)
        return Interpreter(frames, dataClasses, methodTables, bound, NativeBridge(), factory, natives, mainDispatcher, heap)
    }
}
