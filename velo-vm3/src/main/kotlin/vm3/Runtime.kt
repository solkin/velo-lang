package vm3

import core.Dispatcher
import core.DispatcherFactory
import core.NativeRegistry
import core.SerializedProgram
import kotlin.reflect.KClass

/** JVM/Android embedding facade for the compact Velo VM. */
class VeloRuntime(private val natives: NativeRegistry = NativeRegistry()) {
    private var actorFactory: (() -> DispatcherFactory)? = null

    fun register(jvmClass: KClass<*>): VeloRuntime = apply { natives.register(jvmClass) }
    fun register(jvmClass: Class<*>): VeloRuntime = apply { natives.register(jvmClass) }
    fun register(name: String, jvmClass: KClass<*>): VeloRuntime = apply { natives.register(name, jvmClass) }
    fun register(name: String, jvmClass: Class<*>): VeloRuntime = apply { natives.register(name, jvmClass) }
    fun registerData(jvmClass: KClass<*>): VeloRuntime = apply { natives.registerData(jvmClass) }
    fun registerData(jvmClass: Class<*>): VeloRuntime = apply { natives.registerData(jvmClass) }
    fun registerData(name: String, jvmClass: KClass<*>): VeloRuntime = apply { natives.registerData(name, jvmClass) }
    fun registerData(name: String, jvmClass: Class<*>): VeloRuntime = apply { natives.registerData(name, jvmClass) }
    fun getNativeRegistry(): NativeRegistry = natives

    /** Provider is retained so every program gets a fresh placement backend. */
    fun actorPlacement(factory: () -> DispatcherFactory): VeloRuntime = apply { actorFactory = factory }

    fun run(program: SerializedProgram): RunStats = run(program, null)

    fun run(program: SerializedProgram, onLoop: ((LoopHandle) -> Unit)?): RunStats {
        val engine = engine(program, null)
        return try {
            onLoop?.invoke(engine.loopHandle)
            engine.runBlocking()
        } catch (t: Throwable) {
            engine.stop()
            throw t
        }
    }

    fun start(program: SerializedProgram, mainDispatcher: Dispatcher): ProgramHandle {
        val engine = engine(program, mainDispatcher)
        return try {
            engine.start()
        } catch (t: Throwable) {
            engine.stop()
            throw t
        }
    }

    private fun engine(program: SerializedProgram, mainDispatcher: Dispatcher?): Engine {
        val factory = actorFactory?.invoke()
        return try {
            Engine(program, natives, factory, mainDispatcher)
        } catch (t: Throwable) {
            factory?.shutdown()
            throw t
        }
    }
}

data class RunStats(
    val instructions: Long,
    val wallClockMillis: Long,
) {
    val opsExecuted: Long get() = instructions
}

interface LoopHandle {
    fun retain()
    fun release()
}

class ProgramHandle internal constructor(
    private val engine: Engine,
    private val thread: Thread,
) {
    fun stop() = engine.stop()
    fun isAlive(): Boolean = thread.isAlive
    fun awaitTermination(timeoutMs: Long) = thread.join(timeoutMs)
    fun failure(): Throwable? = engine.failure
    fun error(): Throwable? = failure()
}

class VeloError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
