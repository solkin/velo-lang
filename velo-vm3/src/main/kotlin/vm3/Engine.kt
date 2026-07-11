package vm3

import core.BoundNative
import core.DataClassInfo
import core.Dispatcher
import core.DispatcherFactory
import core.NativeLinker
import core.NativeRegistry
import core.NullPtr
import core.Op
import core.SerializedProgram
import core.VmType
import java.lang.reflect.Array as JArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Compact interpreter state. Guest calls share one contiguous, tagged operand
 * stack; activations only retain pc, base and their lexical environment.
 *
 * Values on the operand stack and in every [Env] slot are stored as
 * (tag, rawLong) pairs — primitives never box on the hot path. Only genuine
 * references occupy the parallel object array. Boxing happens lazily and only
 * on cold boundaries (native calls, actor transfer, pointers).
 */
internal class Engine(
    program: SerializedProgram,
    private val registry: NativeRegistry,
    private val actorFactory: DispatcherFactory?,
    private val mainDispatcher: Dispatcher?,
) {
    private val frames: Map<Int, FrameSpec>
    private val natives: Array<BoundNative>
    private val dataClasses: Map<Int, DataClassInfo>
    private val dataClassesByName: Map<String, DataClassInfo>
    private val methods: Map<Int, Map<String, Int>>
    private val pins = AtomicInteger()
    private val instructions = AtomicLong()
    private val mainQueue = LinkedBlockingQueue<Runnable>()
    private val actorDispatchers = ConcurrentLinkedQueue<Dispatcher>()
    val stopped = AtomicBoolean()
    private val cleaned = AtomicBoolean()
    private val mainCompleted = CountDownLatch(1)
    @Volatile var failure: Throwable? = null
        private set
    private val mainOwner = Owner(this, mainDispatcher, true)

    val loopHandle: LoopHandle = object : LoopHandle {
        override fun retain() { pins.incrementAndGet() }
        override fun release() {
            while (true) {
                val current = pins.get()
                if (current == 0 || pins.compareAndSet(current, current - 1)) return
            }
        }
    }

    init {
        require(program.frames.map { it.num }.toSet().size == program.frames.size) { "Duplicate frame number" }
        require(program.dataClasses.map { it.frameNum }.toSet().size == program.dataClasses.size) {
            "Duplicate data-class frame"
        }
        require(program.dataClasses.map { it.name }.toSet().size == program.dataClasses.size) {
            "Duplicate data-class name"
        }
        require(program.classMethods.map { it.frameNum }.toSet().size == program.classMethods.size) {
            "Duplicate class method table"
        }
        program.classMethods.forEach { table ->
            require(table.methods.map { it.name }.toSet().size == table.methods.size) {
                "Duplicate method in class frame ${table.frameNum}"
            }
        }
        frames = program.frames.associate { it.num to FrameSpec(it) }
        require(frames.containsKey(ENTRY_FRAME)) { "Program has no entry frame 0" }
        natives = NativeLinker.link(program.natives, registry)
        dataClasses = program.dataClasses.associateBy { it.frameNum }
        dataClassesByName = program.dataClasses.associateBy { it.name }
        methods = program.classMethods.associate { info ->
            info.frameNum to info.methods.associate { it.name to it.index }
        }
        verify()
    }

    fun runBlocking(): RunStats {
        val start = System.nanoTime()
        val fiber = Fiber(this, frames.getValue(ENTRY_FRAME), null, mainOwner)
        mainOwner.bindCurrentThread()
        try {
            fiber.run()
            pumpMainQueue()
            failure?.let { throw it }
        } finally {
            shutdown()
        }
        return RunStats(instructions.get(), (System.nanoTime() - start) / 1_000_000)
    }

    fun start(): ProgramHandle {
        lateinit var thread: Thread
        thread = Thread({
            try {
                val task = Runnable {
                    mainOwner.bindCurrentThread()
                    val fiber = Fiber(this, frames.getValue(ENTRY_FRAME), null, mainOwner)
                    drive(fiber, mainOwner) { _, error ->
                        if (error == null) mainCompleted.countDown() else fail(error)
                    }
                }
                val dispatcher = mainDispatcher
                if (dispatcher == null) task.run() else dispatcher.execute(task)
                mainCompleted.await()
                while (!stopped.get() && pins.get() > 0) Thread.sleep(10)
            } catch (t: Throwable) {
                fail(t)
            } finally {
                shutdown()
            }
        }, "velo-vm3").apply { isDaemon = true; start() }
        return ProgramHandle(this, thread)
    }

    fun stop() {
        stopped.set(true)
        mainCompleted.countDown()
        shutdown()
    }

    private fun shutdown() {
        if (!cleaned.compareAndSet(false, true)) return
        actorDispatchers.forEach { it.close() }
        mainDispatcher?.close()
        actorFactory?.shutdown()
    }

    private fun fail(error: Throwable) {
        synchronized(this) {
            if (failure == null) failure = error
        }
        stop()
    }

    private fun track(dispatcher: Dispatcher?): Dispatcher? {
        if (dispatcher != null) actorDispatchers += dispatcher
        return dispatcher
    }

    private fun recordInstructions(count: Long) {
        if (count != 0L) instructions.addAndGet(count)
    }

    private fun enqueueMain(task: Runnable) {
        val dispatcher = mainDispatcher
        if (dispatcher == null) mainQueue.put(task) else dispatcher.execute(task)
    }

    private fun pumpMainQueue() {
        while (!stopped.get()) {
            val task = if (pins.get() > 0) mainQueue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                else mainQueue.poll()
            if (task == null) {
                if (pins.get() == 0) return
                continue
            }
            task.run()
        }
    }

    private fun pumpOne(): Boolean {
        val task = mainQueue.poll() ?: return false
        task.run()
        return true
    }

    private fun verify() {
        for (frame in frames.values) {
            require(frame.vars.toSet().size == frame.vars.size) { "Frame ${frame.num} has duplicate variables" }
            for ((index, op) in frame.ops.withIndex()) when (op) {
                is Op.Frame -> require(frames.containsKey(op.num)) { "Frame ${frame.num} references missing frame ${op.num}" }
                is Op.NativeCall -> {
                    require(op.poolIndex in natives.indices) {
                        "Frame ${frame.num} references missing native ${op.poolIndex}"
                    }
                    require(op.args.size == natives[op.poolIndex].jvmParams.size) {
                        "Frame ${frame.num} passes ${op.args.size} arguments to native ${op.poolIndex}, " +
                            "expected ${natives[op.poolIndex].jvmParams.size}"
                    }
                }
                is Op.ActorSpawn -> {
                    require(frames.containsKey(op.classFrameNum)) {
                        "Frame ${frame.num} references missing actor frame ${op.classFrameNum}"
                    }
                    require(op.args >= 0) { "Frame ${frame.num} has negative actor constructor arity" }
                }
                is Op.If -> requireJump(frame, index, op.elseSkip)
                is Op.Move -> requireJump(frame, index, op.count)
                is Op.Call -> require(op.args != Int.MIN_VALUE) { "Frame ${frame.num} has invalid call arity" }
                is Op.InterfaceCall -> require(op.args >= 0) { "Frame ${frame.num} has negative interface arity" }
                is Op.ActorCall -> require(op.args >= 0) { "Frame ${frame.num} has negative actor arity" }
                is Op.ScopeEnter -> require(op.count >= 0) { "Frame ${frame.num} has negative scope size" }
                else -> Unit
            }
        }
    }

    private fun requireJump(frame: FrameSpec, index: Int, offset: Int) {
        val target = index.toLong() + 1L + offset.toLong()
        require(target in 0L..frame.ops.size.toLong()) {
            "Frame ${frame.num} jumps outside its code: $index -> $target"
        }
    }

    private class Activation(
        val spec: FrameSpec,
        var env: Env,
        val base: Int,
        var pc: Int = 0,
    )

    private class Owner(
        private val engine: Engine,
        private val dispatcher: Dispatcher?,
        override val isMain: Boolean,
    ) : TaskOwner {
        override val suspends: Boolean = dispatcher != null || !isMain
        @Volatile private var thread: Thread? = null

        fun bindCurrentThread() { thread = Thread.currentThread() }

        override fun submit(task: Runnable) {
            val wrapped = Runnable { bindCurrentThread(); task.run() }
            when {
                dispatcher != null -> dispatcher.execute(wrapped)
                isMain -> engine.enqueueMain(wrapped)
                else -> wrapped.run()
            }
        }

        override fun isCurrentThread(): Boolean = Thread.currentThread() === thread
    }

    private class Fiber(
        private val engine: Engine,
        entry: FrameSpec,
        parent: Env?,
        private val owner: TaskOwner,
        private val suspends: Boolean = owner.suspends,
    ) {
        // Tagged operand stack: three parallel arrays sharing `sp`.
        private var sTag = ByteArray(64)
        private var sPrim = LongArray(64)
        private var sRef = arrayOfNulls<Any?>(64)
        private var sp = 0
        private val calls = ArrayList<Activation>(16)

        init {
            val env = if (entry.escapes) Env.boxed(entry.vars, parent) else Env.tagged(entry.vars, parent)
            calls += Activation(entry, env, 0)
        }

        fun run(): Any? {
            var ins = 0L
            try {
                val calls = this.calls
                while (calls.isNotEmpty()) {
                    val frame = calls[calls.size - 1]
                    val code = frame.spec.code
                    if (frame.pc >= code.size) { finishFrame(frame); continue }
                    // Observe a cooperative stop without paying a volatile read
                    // every instruction.
                    if ((ins and CHECK_MASK) == 0L && engine.stopped.get()) break
                    val pc = frame.pc++
                    ins++
                    execute(pc, frame)
                }
                engine.failure?.let { throw it }
                return if (sp == 0) null else popAny()
            } finally {
                engine.recordInstructions(ins)
            }
        }

        private fun execute(pc: Int, frame: Activation) {
            // Dispatch on the flat opcode stream (an IntArray), reading operands
            // from the frame's precomputed opA/consts tables — no Op-object
            // chase, no cast on the hot path. Fused superinstructions (OP_*)
            // ride the same switch.
            val spec = frame.spec
            when (spec.code[pc]) {
                0x29 -> push(spec.consts[pc])
                0x0c -> drop()
                0x0d -> dup()
                0x34 -> swap()
                0x2c -> rot()
                0x26 -> arith('+')
                0x1a -> arith('-')
                0x1e -> arith('*')
                0x0b -> arith('/')
                0x2a -> arith('%')
                0x02 -> bitwise('&')
                0x21 -> bitwise('|')
                0x35 -> bitwise('^')
                0x46 -> shift(left = true)
                0x47 -> shift(left = false)
                0x1b -> { val bi = sp - 1; val ai = sp - 2; val r = compareSlots(ai, bi) > 0; sp = ai; sRef[ai] = null; pushBool(r) }
                OP_GT_IF -> {
                    val bi = sp - 1; val ai = sp - 2
                    val taken = compareSlots(ai, bi) > 0
                    sp = ai; sRef[ai] = null
                    frame.pc = pc + 2
                    if (!taken) frame.pc += spec.opA[pc + 1]
                }
                OP_EQ_IF -> {
                    val taken = equalsTop()
                    frame.pc = pc + 2
                    if (!taken) frame.pc += spec.opA[pc + 1]
                }
                0x0e -> equals()
                0x14 -> pushRef(String(Character.toChars(popInt())))
                0x15 -> pushRef(popInt().toString())
                0x4e -> pushRef(popLong().toString())
                0x49 -> pushRef(popFloat().toString())
                0x16 -> pushFloat(popFloat())
                0x17 -> pushInt(popFloat().toInt())
                0x1f -> pushByte(popInt().toByte())
                0x4a -> pushLong(popLong())
                0x4b -> pushInt(popLong().toInt())
                0x4c -> pushFloat(popLong().toFloat())
                0x4d -> pushLong(popFloat().toLong())
                0x40 -> pushInt(popString().toInt())
                0x48 -> pushInt(hashValue(popAny()))
                0x2e -> { val b = popString(); val a = popString(); pushRef(a + b) }
                0x30 -> { val s = popString(); pushInt(s.codePointCount(0, s.length)) }
                0x2f -> {
                    val index = popInt(); val s = popString()
                    pushInt(s.codePointAt(s.offsetByCodePoints(0, index)))
                }
                0x33 -> {
                    val start = popInt(); val end = popInt(); val s = popString()
                    pushRef(s.substring(s.offsetByCodePoints(0, start), s.offsetByCodePoints(0, end)))
                }
                0x03 -> pushRef(VArray(popInt()))
                0x05 -> pushInt(popArray().size)
                0x04 -> {
                    val count = popInt(); val index = popInt(); val a = popArray()
                    repeat(count) { i -> pushArrayElement(a, index + i) }
                }
                0x08 -> {
                    val count = popInt(); val index = popInt(); val a = popArray()
                    for (i in count - 1 downTo 0) storeArrayElement(a, index + i)
                    pushRef(a)
                }
                0x32 -> {
                    val srcPos = popInt(); val dstPos = popInt(); val length = popInt()
                    val src = popArray(); val dst = popArray()
                    src.copyInto(dst, srcPos, dstPos, length)
                }
                0x0f -> loadVar(frame.env, spec.opA[pc])
                0x2d -> storeVar(frame.env, spec.opA[pc])
                0x12 -> if (!popBool()) frame.pc += spec.opA[pc]
                0x1d -> frame.pc += spec.opA[pc]
                0x22 -> frame.env = makeEnv(spec.scopeKeys[pc]!!, frame.env, spec.escapes)
                0x23 -> {
                    val leaving = frame.env
                    val parent = leaving.parent ?: throw VeloError("No scope to leave")
                    if (!spec.escapes) recycle(leaving)
                    frame.env = parent
                }
                0x11 -> engine.stop()
                0x2b -> finishFrame(frame)
                0x18 -> pushRef(FuncValue(engine.frames.getValue(spec.opA[pc]), frame.env, owner))
                0x42 -> {
                    frame.env.classFrame = spec.num
                    pushRef(InstanceValue(spec.num, frame.env))
                }
                0x19 -> {
                    val op = spec.ops[pc] as Op.MethodLoad
                    val receiver = frame.env.parent ?: throw VeloError("Method receiver is missing")
                    val classFrame = findInstanceFrame(receiver)
                    val slot = engine.methods[classFrame]?.get(op.name)
                        ?: throw VeloError("Class frame $classFrame has no method '${op.name}'")
                    pushRef(receiver.get(slot))
                }
                0x09 -> {
                    val op = spec.ops[pc] as Op.Call
                    call(op.args, op.classParent, op.callbackResult)
                }
                0x1c -> interfaceCall(spec.ops[pc] as Op.InterfaceCall)
                0x50 -> pushRef(BoxPointer(popAny()))
                0x51 -> push((popRef() as VPointer).get())
                0x52 -> { val ptr = popRef() as VPointer; ptr.set(popAny()) }
                0x53 -> pushRef(EnvPointer(frame.env, spec.opA[pc]))
                0x54 -> { val index = popInt(); pushRef(ArrayPointer(popArray(), index)) }
                0x43 -> nativeCall(spec.ops[pc] as Op.NativeCall)
                0x60 -> actorSpawn(spec.ops[pc] as Op.ActorSpawn)
                0x61 -> actorCall(spec.ops[pc] as Op.ActorCall)
                0x62 -> futureAwait()
                else -> throw VeloError("Unknown opcode 0x${spec.code[pc].toString(16)}")
            }
        }

        private fun call(rawArgs: Int, classParent: Boolean, callbackResult: Boolean) {
            val argc = abs(rawArgs)
            val callable = popRef()
            val foreign = callable is CallbackValue
            val fn = when (callable) {
                is FuncValue -> callable
                is CallbackValue -> callable.function
                else -> throw VeloError("Value is not callable")
            }
            // Method wrappers receive their arguments in property-evaluation
            // order. A negative arity is the bytecode marker that restores the
            // ordinary function-call order before entering the actual method.
            if (rawArgs < 0) reverse(sp - argc, sp)
            if (foreign) {
                val base = sp - argc
                val args = ArrayList<Any?>(argc)
                for (k in 0 until argc) args.add(transfer(boxSlot(sTag[base + k], sPrim[base + k], sRef[base + k])))
                for (k in base until sp) sRef[k] = null
                sp = base
                val result = VFuture()
                fn.owner.submit(Runnable {
                    try {
                        val callback = Fiber(engine, fn.frame, fn.captured, fn.owner)
                        args.forEach { callback.push(it) }
                        result.complete(callback.transfer(callback.run()))
                    } catch (t: Throwable) {
                        result.fail(t)
                    }
                })
                if (callbackResult) {
                    awaitFuture(result)
                } else {
                    result.onComplete {
                        try {
                            result.result()
                        } catch (t: Throwable) {
                            engine.fail(t)
                        }
                    }
                }
                return
            }
            val parent = if (classParent) {
                val receiverAt = sp - argc - 1
                val receiver = sRef[receiverAt] as? InstanceValue ?: throw VeloError("Method receiver is not an instance")
                removeAt(receiverAt)
                receiver.env
            } else fn.captured
            calls += Activation(fn.frame, makeEnv(fn.frame.vars, parent, fn.frame.escapes), sp - argc)
        }

        private fun interfaceCall(op: Op.InterfaceCall) {
            val wrapper = popRef() as? FuncValue ?: throw VeloError("Interface wrapper is not callable")
            val receiverAt = sp - op.args - 1
            when (val receiver = boxSlot(sTag[receiverAt], sPrim[receiverAt], sRef[receiverAt])) {
                is InstanceValue -> {
                    removeAt(receiverAt)
                    calls += Activation(wrapper.frame, makeEnv(wrapper.frame.vars, receiver.env, wrapper.frame.escapes), sp - op.args)
                }
                is NativeValue -> {
                    removeAt(receiverAt)
                    val descriptor = engine.registry.descriptor(receiver.veloName)
                        ?: throw VeloError("Native class '${receiver.veloName}' is not registered")
                    val method = descriptor.methods[op.method]
                        ?: throw VeloError("Native class '${receiver.veloName}' has no method '${op.method}'")
                    val args = ArrayList<Any?>(op.args + 1)
                    args += receiver.value
                    repeat(op.args) { i -> args += toHost(popAny(), method.params[i], method.jvmParams[i]) }
                    val result = method.handle.invokeWithArguments(args)
                    if (method.returns !is VmType.Void) push(fromHost(result, method.returns))
                }
                else -> throw VeloError("Interface receiver has unsupported runtime type")
            }
        }

        private fun nativeCall(op: Op.NativeCall) {
            val bound = engine.natives[op.poolIndex]
            val converted = nativeConverted; converted.clear()
            val invoke = nativeInvoke; invoke.clear()
            repeat(op.args.size) { i -> converted.add(toHost(popAny(), op.args[i], bound.jvmParams[i])) }
            if (!bound.isConstructor) {
                val receiver = popAny() as? NativeValue ?: throw VeloError("Native receiver is missing")
                invoke.add(receiver.value)
            }
            invoke.addAll(converted)
            val result = try {
                bound.handle.invokeWithArguments(invoke)
            } catch (t: Throwable) {
                throw VeloError("Native call ${bound.ref} failed: ${t.message ?: t}", t)
            }
            if (bound.isConstructor) push(NativeValue(result, bound.ref.className))
            else if (bound.ref.returns !is VmType.Void) push(fromHost(result, bound.ref.returns))
        }

        private fun toHost(value: Any?, type: VmType, jvmType: Class<*>): Any? = when (type) {
            VmType.Void -> Unit
            VmType.Any -> unwrapHost(value)
            VmType.Byte -> (value as Number).toByte()
            VmType.Int -> (value as Number).toInt()
            VmType.Long -> (value as Number).toLong()
            VmType.Float -> (value as Number).toFloat()
            VmType.Str -> value as String
            VmType.Bool -> value as Boolean
            is VmType.Array -> {
                val src = value as VArray
                if (jvmType.isArray) {
                    JArray.newInstance(jvmType.componentType, src.size).also { dst ->
                        for (i in 0 until src.size) JArray.set(dst, i, toHost(src.get(i), type.elementType, jvmType.componentType))
                    }
                } else (0 until src.size).map { toHost(src.get(it), type.elementType, Any::class.java) }
            }
            is VmType.Tuple -> (value as VArray).let { arr ->
                (0 until arr.size).map { i -> toHost(arr.get(i), type.elementTypes.getOrElse(i) { VmType.Any }, Any::class.java) }
            }
            is VmType.Class -> when (value) {
                is NativeValue -> value.value
                is InstanceValue -> toHostData(value, type.name)
                else -> value
            }
            is VmType.Func -> {
                val function = when (value) {
                    is FuncValue -> value
                    is CallbackValue -> value.function
                    else -> throw VeloError("Native callback argument is not callable")
                }
                val host = HostFunction(engine, function, type)
                when {
                    jvmType == core.VeloFunction::class.java -> host
                    jvmType.name == "kotlin.jvm.functions.Function0" -> kotlinFunction0(host)
                    jvmType.name == "kotlin.jvm.functions.Function1" -> kotlinFunction1(host)
                    jvmType.name == "kotlin.jvm.functions.Function2" -> kotlinFunction2(host)
                    jvmType.name == "kotlin.jvm.functions.Function3" -> kotlinFunction3(host)
                    jvmType.name == "kotlin.jvm.functions.Function4" -> kotlinFunction4(host)
                    else -> host
                }
            }
            is VmType.Ptr -> value
        }

        private fun fromHost(value: Any?, type: VmType): Any? = when (type) {
            VmType.Void -> null
            VmType.Any -> wrapHost(value)
            VmType.Byte -> (value as Number).toByte()
            VmType.Int -> (value as Number).toInt()
            VmType.Long -> (value as Number).toLong()
            VmType.Float -> (value as Number).toFloat()
            VmType.Str -> value as String
            VmType.Bool -> value as Boolean
            is VmType.Array -> {
                val values: List<Any?> = when {
                    value == null -> emptyList()
                    value.javaClass.isArray -> List(JArray.getLength(value)) { JArray.get(value, it) }
                    value is List<*> -> value
                    else -> throw VeloError("Host value is not an array")
                }
                VArray(values.size).also { a -> values.forEachIndexed { i, v -> a.set(i, fromHost(v, type.elementType)) } }
            }
            is VmType.Tuple -> {
                val values = value as List<*>
                VArray(values.size).also { a -> values.forEachIndexed { i, v -> a.set(i, fromHost(v, type.elementTypes[i])) } }
            }
            is VmType.Class -> {
                val native = engine.registry.getByVeloName(type.name)
                if (native != null) NativeValue(value!!, type.name) else fromHostData(value!!, type.name)
            }
            is VmType.Func -> value
            is VmType.Ptr -> value
        }

        fun decodeCallbackArgs(args: Array<out Any?>, signature: VmType.Func): List<Any?> {
            val types = signature.args ?: return args.map(::wrapHost)
            if (args.size != types.size) {
                throw VeloError("Callback expects ${types.size} arguments, got ${args.size}")
            }
            return args.mapIndexed { index, value -> fromHost(value, types[index]) }
        }

        fun encodeCallbackResult(value: Any?, signature: VmType.Func): Any? {
            val type = signature.ret ?: return unwrapHost(value)
            if (type is VmType.Void) return null
            return toHost(value, type, callbackJvmType(type))
        }

        private fun callbackJvmType(type: VmType): Class<*> = when (type) {
            VmType.Void -> Void.TYPE
            VmType.Any -> Any::class.java
            VmType.Byte -> Byte::class.javaPrimitiveType!!
            VmType.Int -> Int::class.javaPrimitiveType!!
            VmType.Long -> Long::class.javaPrimitiveType!!
            VmType.Float -> Float::class.javaPrimitiveType!!
            VmType.Str -> String::class.java
            VmType.Bool -> Boolean::class.javaPrimitiveType!!
            is VmType.Array, is VmType.Tuple -> List::class.java
            is VmType.Class -> engine.registry.dataBindingByVeloName(type.name)?.jvmClass
                ?: engine.registry.getByVeloName(type.name)?.jvmClass
                ?: Any::class.java
            is VmType.Func -> core.VeloFunction::class.java
            is VmType.Ptr -> Any::class.java
        }

        private fun kotlinFunction0(host: HostFunction): () -> Unit = { host.post() }
        private fun kotlinFunction1(host: HostFunction): (Any?) -> Unit = { a -> host.post(a) }
        private fun kotlinFunction2(host: HostFunction): (Any?, Any?) -> Unit = { a, b -> host.post(a, b) }
        private fun kotlinFunction3(host: HostFunction): (Any?, Any?, Any?) -> Unit = { a, b, c -> host.post(a, b, c) }
        private fun kotlinFunction4(host: HostFunction): (Any?, Any?, Any?, Any?) -> Unit = { a, b, c, d ->
            host.post(a, b, c, d)
        }

        private fun toHostData(value: InstanceValue, name: String): Any {
            val info = engine.dataClasses[value.classFrame]
                ?: throw VeloError("Class '$name' is not a data class")
            val binding = engine.registry.dataBindingByVeloName(name)
                ?: throw VeloError("Data class '$name' has no host binding")
            val args = info.fields.mapIndexed { i, field ->
                toHost(value.env.get(field.index), field.type, binding.ctorJvmParams[i])
            }
            return binding.ctorHandle.invokeWithArguments(args)
        }

        private fun fromHostData(value: Any, name: String): Any {
            val info = engine.dataClassesByName[name]
                ?: throw VeloError("Program has no data class '$name'")
            val binding = engine.registry.dataBindingByVeloName(name)
                ?: throw VeloError("Data class '$name' has no host binding")
            val args = info.fields.map { fromHost(binding.read(value, it.name), it.type) }
            return invokeFrame(engine.frames.getValue(info.frameNum), args, null)
                ?: throw VeloError("Data class '$name' constructor returned void")
        }

        fun invokeFrame(spec: FrameSpec, args: List<Any?>, parent: Env?): Any? {
            val nested = Fiber(engine, spec, parent, owner)
            args.forEach { nested.push(it) }
            return nested.run()
        }

        private fun actorSpawn(op: Op.ActorSpawn) {
            val args = ArrayList<Any?>(op.args)
            repeat(op.args) { args.add(transfer(popAny())) }
            args.reverse()
            val actor = ActorValue(engine, op.className, engine.frames.getValue(op.classFrameNum), args)
            pushRef(actor)
        }

        private fun actorCall(op: Op.ActorCall) {
            val args = ArrayList<Any?>(op.args)
            repeat(op.args) { args.add(transfer(popAny())) }
            args.reverse()
            val actor = popRef() as? ActorValue ?: throw VeloError("Actor receiver is missing")
            pushRef(actor.call(op.methodVarIndex, args))
        }

        private fun futureAwait() {
            val future = popRef() as? VFuture ?: throw VeloError("Value is not a future")
            awaitFuture(future)
        }

        private fun awaitFuture(future: VFuture) {
            when {
                future.isDone() -> {
                    // Inline actor placement can finish the future before the
                    // main fiber reaches await, while callbacks posted by that
                    // actor are already queued. Await is the ordering/yield
                    // boundary: deliver those messages before continuing.
                    if (owner.isMain && !suspends) while (engine.pumpOne()) Unit
                    push(future.result())
                }
                !suspends && owner.isMain -> push(future.await(engine::pumpOne))
                !suspends -> push(future.awaitBlocking())
                else -> throw FiberSuspend(future)
            }
        }

        // Structured transfer across an actor boundary. Mirrors the reference VM
        // (vm2): arrays are deep-copied, functions become owner-pinned callbacks,
        // data classes are re-materialised by value, and everything else — plain
        // class instances, native handles, primitives — is shared by reference.
        fun transfer(value: Any?): Any? = when (value) {
            Uninitialized -> throw VeloError("Uninitialized value cannot cross an actor boundary")
            is FuncValue -> CallbackValue(value)
            is VArray -> VArray(value.size).also { copy ->
                when (value.kind) {
                    TAG_UNINIT -> {}
                    TAG_REF -> for (i in 0 until value.size) copy.set(i, transfer(value.get(i)))
                    else -> { copy.kind = value.kind; copy.prim = value.prim!!.copyOf() }
                }
            }
            is InstanceValue -> {
                val info = engine.dataClasses[value.classFrame]
                if (info != null) {
                    val args = info.fields.map { transfer(value.env.get(it.index)) }
                    invokeFrame(engine.frames.getValue(info.frameNum), args, null)
                } else {
                    value
                }
            }
            else -> value
        }

        private fun finishFrame(frame: Activation) {
            if (sp > frame.base) {
                val i = sp - 1
                val t = sTag[i]; val p = sPrim[i]; val r = sRef[i]
                sp = frame.base
                pushSlot(t, p, r)
            } else {
                sp = frame.base
            }
            if (!frame.spec.escapes) recycle(frame.env)
            calls.removeAt(calls.lastIndex)
        }

        private fun findInstanceFrame(env: Env): Int {
            var current: Env? = env
            while (current != null) {
                if (current.classFrame >= 0) return current.classFrame
                current = current.parent
            }
            throw VeloError("Receiver class cannot be identified")
        }

        // -- arithmetic on tagged slots (no boxing) -----------------------------

        private fun arith(opc: Char) {
            val bi = sp - 1; val ai = sp - 2
            if (ai < 0) throw VeloError("Operand stack underflow")
            when (maxOf(kindRank(sTag[ai]), kindRank(sTag[bi]))) {
                1 -> setInt(ai, intOp(slotInt(ai), slotInt(bi), opc))
                2 -> setLong(ai, longOp(slotLong(ai), slotLong(bi), opc))
                else -> setFloat(ai, floatOp(slotFloat(ai), slotFloat(bi), opc))
            }
            sp = ai + 1
        }

        private fun bitwise(opc: Char) {
            val bi = sp - 1; val ai = sp - 2
            if (ai < 0) throw VeloError("Operand stack underflow")
            if (sTag[ai] == TAG_LONG || sTag[bi] == TAG_LONG) {
                val x = slotLong(ai); val y = slotLong(bi)
                setLong(ai, when (opc) { '&' -> x and y; '|' -> x or y; else -> x xor y })
            } else {
                val x = slotInt(ai); val y = slotInt(bi)
                setInt(ai, when (opc) { '&' -> x and y; '|' -> x or y; else -> x xor y })
            }
            sp = ai + 1
        }

        private fun shift(left: Boolean) {
            val bits = popInt()
            val ai = sp - 1
            if (ai < 0) throw VeloError("Operand stack underflow")
            if (sTag[ai] == TAG_LONG) {
                val v = slotLong(ai); setLong(ai, if (left) v shl bits else v shr bits)
            } else {
                val v = slotInt(ai); setInt(ai, if (left) v shl bits else v shr bits)
            }
        }

        private fun equals() = pushBool(equalsTop())

        private fun equalsTop(): Boolean {
            val bi = sp - 1; val ai = sp - 2
            if (ai < 0) throw VeloError("Operand stack underflow")
            val ta = sTag[ai]; val tb = sTag[bi]
            val res = if (isNumericTag(ta) && isNumericTag(tb)) {
                compareSlots(ai, bi) == 0
            } else {
                equalsValue(boxSlot(ta, sPrim[ai], sRef[ai]), boxSlot(tb, sPrim[bi], sRef[bi]))
            }
            sp = ai; sRef[ai] = null; sRef[bi] = null
            return res
        }

        private fun compareSlots(ai: Int, bi: Int): Int = when (maxOf(kindRank(sTag[ai]), kindRank(sTag[bi]))) {
            1 -> slotInt(ai).compareTo(slotInt(bi))
            2 -> slotLong(ai).compareTo(slotLong(bi))
            else -> slotFloat(ai).compareTo(slotFloat(bi))
        }

        private fun kindRank(tag: Byte): Int = when (tag) {
            TAG_INT, TAG_BYTE -> 1
            TAG_LONG -> 2
            TAG_FLOAT -> 3
            else -> throw VeloError("Numeric operand required")
        }

        private fun isNumericTag(tag: Byte): Boolean =
            tag == TAG_INT || tag == TAG_LONG || tag == TAG_FLOAT || tag == TAG_BYTE

        private fun intOp(x: Int, y: Int, opc: Char): Int = when (opc) {
            '+' -> x + y; '-' -> x - y; '*' -> x * y; '/' -> x / y; '%' -> x % y; else -> throw VeloError("bad op")
        }

        private fun longOp(x: Long, y: Long, opc: Char): Long = when (opc) {
            '+' -> x + y; '-' -> x - y; '*' -> x * y; '/' -> x / y; '%' -> x % y; else -> throw VeloError("bad op")
        }

        private fun floatOp(x: Float, y: Float, opc: Char): Float = when (opc) {
            '+' -> x + y; '-' -> x - y; '*' -> x * y; '/' -> x / y; '%' -> x % y; else -> throw VeloError("bad op")
        }

        // Values on the operand stack that must fall back to a JVM object.
        private fun equalsValue(a: Any?, b: Any?): Boolean {
            if (a === Uninitialized || b === Uninitialized) throw VeloError("Cannot compare uninitialized values")
            if (a is Number && b is Number) return compareNumbersBoxed(a, b) == 0
            if (a is VArray && b is VArray) return a.size == b.size && (0 until a.size).all {
                equalsValue(a.get(it), b.get(it))
            }
            if (a is InstanceValue && b is InstanceValue) {
                if (a.classFrame != b.classFrame) return false
                val info = engine.dataClasses[a.classFrame] ?: return a === b
                return info.fields.all { equalsValue(a.env.get(it.index), b.env.get(it.index)) }
            }
            return a == b
        }

        private fun compareNumbersBoxed(a: Number, b: Number): Int = when {
            a is Float || b is Float -> a.toFloat().compareTo(b.toFloat())
            a is Long || b is Long -> a.toLong().compareTo(b.toLong())
            else -> a.toInt().compareTo(b.toInt())
        }

        private fun hashValue(value: Any?): Int = when (value) {
            Uninitialized -> throw VeloError("Cannot hash an uninitialized value")
            null, NullPointerValue -> 0
            is Byte -> value.toInt()
            is Int -> value
            is Long -> value.hashCode()
            is Float -> value.hashCode()
            is VArray -> (0 until value.size).fold(1) { h, i -> 31 * h + hashValue(value.get(i)) }
            is InstanceValue -> engine.dataClasses[value.classFrame]?.fields?.fold(1) { h, f -> 31 * h + hashValue(value.env.get(f.index)) }
                ?: System.identityHashCode(value)
            else -> value.hashCode()
        }

        private fun unwrapHost(value: Any?): Any? = when (value) {
            Uninitialized -> throw VeloError("Uninitialized value cannot cross a native boundary")
            is NativeValue -> value.value
            is VArray -> (0 until value.size).map { unwrapHost(value.get(it)) }
            NullPointerValue -> null
            else -> value
        }

        private fun wrapHost(value: Any?): Any? = when {
            value == null -> null
            value.javaClass.isArray -> VArray(JArray.getLength(value)).also { a ->
                for (i in 0 until a.size) a.set(i, wrapHost(JArray.get(value, i)))
            }
            value is List<*> -> VArray(value.size).also { a -> value.forEachIndexed { i, v -> a.set(i, wrapHost(v)) } }
            engine.registry.getByJvmClass(value.javaClass) != null -> {
                val info = engine.registry.getByJvmClass(value.javaClass)!!
                NativeValue(value, info.veloName)
            }
            else -> value
        }

        // -- lexical variables (tagged, unboxed) --------------------------------

        private fun loadVar(env0: Env, index: Int) {
            var env: Env? = env0
            while (env != null) {
                val at = env.localIndex(index)
                if (at >= 0) {
                    val b = env.boxed
                    if (b != null) {
                        val v = b[at]
                        if (v === Uninitialized) throw VeloError("Variable $index is not initialized")
                        push(v)
                        return
                    }
                    val t = env.tag[at]
                    if (t == TAG_UNINIT) throw VeloError("Variable $index is not initialized")
                    pushSlot(t, env.prim[at], env.ref[at])
                    return
                }
                env = env.parent
            }
            throw VeloError("Variable $index is not in scope")
        }

        private fun storeVar(env0: Env, index: Int) {
            val i = down()
            val t = sTag[i]; val p = sPrim[i]; val r = if (t == TAG_REF) sRef[i] else null
            sRef[i] = null
            var env: Env? = env0
            while (env != null) {
                val at = env.localIndex(index)
                if (at >= 0) {
                    val b = env.boxed
                    if (b != null) b[at] = boxSlot(t, p, r)
                    else { env.tag[at] = t; env.prim[at] = p; env.ref[at] = r }
                    return
                }
                env = env.parent
            }
            throw VeloError("Variable $index is not in scope")
        }

        // -- per-fiber Env pool -------------------------------------------------
        // A non-escaping frame's Env is reused instead of reallocated. Pooled
        // Envs carry the largest slot arrays seen, so a rebind rarely grows.

        private val envPool = ArrayList<Env>(16)

        // Reused argument buffers for the native bridge (cleared per call).
        private val nativeConverted = ArrayList<Any?>(8)
        private val nativeInvoke = ArrayList<Any?>(8)

        private fun makeEnv(keys: IntArray, parent: Env?, escapes: Boolean): Env {
            if (escapes) return Env.boxed(keys, parent)
            if (envPool.isEmpty()) return Env.tagged(keys, parent)
            val e = envPool.removeAt(envPool.size - 1)
            e.rebind(keys, parent)
            return e
        }

        private fun recycle(env: Env) {
            envPool.add(env)
        }

        // -- array elements -----------------------------------------------------
        // A primitive-backed array shares the stack's raw-bit encoding, so an
        // element load/store is a plain long copy with no boxing.

        private fun pushArrayElement(a: VArray, at: Int) {
            if (at < 0 || at >= a.size) throw ArrayIndexOutOfBoundsException("Index $at out of bounds for length ${a.size}")
            when (val k = a.kind) {
                // A reference-backed array may hold boxed primitives (mixed
                // arrays, promoted tuples). Re-tag by the value's actual type via
                // the boxed bridge, so a boxed Int lands in a primitive slot — not
                // a ref slot the consuming op would misread.
                TAG_REF -> push(a.obj!![at])
                TAG_UNINIT -> pushRef(null)
                else -> pushSlot(k, a.prim!![at], null)
            }
        }

        private fun storeArrayElement(a: VArray, at: Int) {
            if (at < 0 || at >= a.size) throw ArrayIndexOutOfBoundsException("Index $at out of bounds for length ${a.size}")
            val i = down()
            val t = sTag[i]
            when {
                a.kind == t && t != TAG_REF -> a.prim!![at] = sPrim[i]
                a.kind == TAG_REF -> a.obj!![at] = boxSlot(t, sPrim[i], sRef[i])
                a.kind == TAG_UNINIT -> {
                    a.specialize(t)
                    if (t == TAG_REF) a.obj!![at] = sRef[i] else a.prim!![at] = sPrim[i]
                }
                t == TAG_REF -> { a.promoteToObj(); a.obj!![at] = sRef[i] }
                else -> { a.promoteToObj(); a.obj!![at] = boxSlot(t, sPrim[i], sRef[i]) }
            }
            sRef[i] = null
        }

        // -- tagged operand stack primitives ------------------------------------

        private fun slotInt(i: Int): Int = if (sTag[i] == TAG_FLOAT) Float.fromBits(sPrim[i].toInt()).toInt() else sPrim[i].toInt()
        private fun slotLong(i: Int): Long = if (sTag[i] == TAG_FLOAT) Float.fromBits(sPrim[i].toInt()).toLong() else sPrim[i]
        private fun slotFloat(i: Int): Float = if (sTag[i] == TAG_FLOAT) Float.fromBits(sPrim[i].toInt()) else sPrim[i].toFloat()

        private fun setInt(i: Int, v: Int) { sTag[i] = TAG_INT; sPrim[i] = v.toLong(); sRef[i] = null }
        private fun setLong(i: Int, v: Long) { sTag[i] = TAG_LONG; sPrim[i] = v; sRef[i] = null }
        private fun setFloat(i: Int, v: Float) { sTag[i] = TAG_FLOAT; sPrim[i] = v.toRawBits().toLong(); sRef[i] = null }

        private fun ensure() {
            if (sp == sTag.size) {
                val n = sTag.size shl 1
                sTag = sTag.copyOf(n); sPrim = sPrim.copyOf(n); sRef = sRef.copyOf(n)
            }
        }

        private fun pushInt(v: Int) { ensure(); sTag[sp] = TAG_INT; sPrim[sp] = v.toLong(); sRef[sp] = null; sp++ }
        private fun pushLong(v: Long) { ensure(); sTag[sp] = TAG_LONG; sPrim[sp] = v; sRef[sp] = null; sp++ }
        private fun pushFloat(v: Float) { ensure(); sTag[sp] = TAG_FLOAT; sPrim[sp] = v.toRawBits().toLong(); sRef[sp] = null; sp++ }
        private fun pushBool(v: Boolean) { ensure(); sTag[sp] = TAG_BOOL; sPrim[sp] = if (v) 1L else 0L; sRef[sp] = null; sp++ }
        private fun pushByte(v: Byte) { ensure(); sTag[sp] = TAG_BYTE; sPrim[sp] = v.toLong(); sRef[sp] = null; sp++ }
        private fun pushRef(v: Any?) { ensure(); sTag[sp] = TAG_REF; sRef[sp] = v; sp++ }
        private fun pushSlot(t: Byte, p: Long, r: Any?) { ensure(); sTag[sp] = t; sPrim[sp] = p; sRef[sp] = r; sp++ }

        /** Boxed bridge used by engine-level callers (args, callback results). */
        fun push(value: Any?) {
            ensure()
            val t = tagOf(value)
            sTag[sp] = t; sPrim[sp] = primBitsOf(value); sRef[sp] = if (t == TAG_REF) value else null; sp++
        }

        private fun down(): Int {
            if (sp == 0) throw VeloError("Operand stack underflow")
            return --sp
        }

        private fun popAny(): Any? { val i = down(); val v = boxSlot(sTag[i], sPrim[i], sRef[i]); sRef[i] = null; return v }
        private fun popRef(): Any? { val i = down(); val v = sRef[i]; sRef[i] = null; return v }
        private fun popInt(): Int { val i = down(); val v = slotInt(i); sRef[i] = null; return v }
        private fun popLong(): Long { val i = down(); val v = slotLong(i); sRef[i] = null; return v }
        private fun popFloat(): Float { val i = down(); val v = slotFloat(i); sRef[i] = null; return v }
        private fun popBool(): Boolean { val i = down(); val v = sPrim[i] != 0L; sRef[i] = null; return v }
        private fun popString(): String = popRef() as? String ?: throw VeloError("Expected a string operand")
        private fun popArray(): VArray = popRef() as? VArray ?: throw VeloError("Expected an array operand")

        private fun drop() { val i = down(); sRef[i] = null }

        private fun dup() {
            val i = sp - 1
            if (i < 0) throw VeloError("Operand stack underflow")
            pushSlot(sTag[i], sPrim[i], sRef[i])
        }

        private fun swap() {
            val a = sp - 1; val b = sp - 2
            if (b < 0) throw VeloError("Operand stack underflow")
            val tt = sTag[a]; sTag[a] = sTag[b]; sTag[b] = tt
            val pp = sPrim[a]; sPrim[a] = sPrim[b]; sPrim[b] = pp
            val rr = sRef[a]; sRef[a] = sRef[b]; sRef[b] = rr
        }

        private fun rot() {
            val ci = sp - 1; val bi = sp - 2; val ai = sp - 3
            if (ai < 0) throw VeloError("Operand stack underflow")
            val ct = sTag[ci]; val cp = sPrim[ci]; val cr = sRef[ci]
            val bt = sTag[bi]; val bp = sPrim[bi]; val br = sRef[bi]
            val at = sTag[ai]; val ap = sPrim[ai]; val ar = sRef[ai]
            sTag[ai] = ct; sPrim[ai] = cp; sRef[ai] = cr
            sTag[bi] = at; sPrim[bi] = ap; sRef[bi] = ar
            sTag[ci] = bt; sPrim[ci] = bp; sRef[ci] = br
        }

        private fun removeAt(index: Int) {
            if (index < 0 || index >= sp) throw VeloError("Stack index out of range")
            val moved = sp - index - 1
            System.arraycopy(sTag, index + 1, sTag, index, moved)
            System.arraycopy(sPrim, index + 1, sPrim, index, moved)
            System.arraycopy(sRef, index + 1, sRef, index, moved)
            sp--
            sRef[sp] = null
        }

        private fun reverse(from: Int, until: Int) {
            var left = from
            var right = until - 1
            while (left < right) {
                val tt = sTag[left]; sTag[left] = sTag[right]; sTag[right] = tt
                val pp = sPrim[left]; sPrim[left] = sPrim[right]; sPrim[right] = pp
                val rr = sRef[left]; sRef[left] = sRef[right]; sRef[right] = rr
                left++
                right--
            }
        }
    }

    private class HostFunction(
        private val engine: Engine,
        private val function: FuncValue,
        private val signature: VmType.Func,
    ) : core.VeloFunction {
        private val retained = AtomicBoolean()

        private fun invoke(args: Array<out Any?>): Any? {
            val callback = Fiber(engine, function.frame, function.captured, function.owner)
            callback.decodeCallbackArgs(args, signature).forEach { callback.push(it) }
            val result = callback.run()
            return callback.encodeCallbackResult(result, signature)
        }

        override fun post(vararg args: Any?) {
            if (engine.stopped.get()) return
            function.owner.submit(Runnable {
                try {
                    invoke(args)
                } catch (t: Throwable) {
                    engine.fail(t)
                }
            })
        }

        override fun call(vararg args: Any?): java.util.concurrent.CompletableFuture<Any?> {
            val future = java.util.concurrent.CompletableFuture<Any?>()
            if (engine.stopped.get()) {
                future.completeExceptionally(VeloError("Program is stopped"))
                return future
            }
            val task = Runnable {
                try {
                    future.complete(invoke(args))
                } catch (t: Throwable) {
                    future.completeExceptionally(t)
                }
            }
            if (function.owner.isCurrentThread()) task.run() else function.owner.submit(task)
            return future
        }

        override fun retain() {
            if (!engine.stopped.get() && retained.compareAndSet(false, true)) engine.pins.incrementAndGet()
        }
        override fun release() { if (retained.compareAndSet(true, false)) engine.pins.decrementAndGet() }
    }

    private class FiberSuspend(val future: VFuture) : RuntimeException(null, null, false, false)

    private class VFuture {
        private val latch = CountDownLatch(1)
        private val lock = Any()
        private var done = false
        private var value: Any? = null
        private var error: Throwable? = null
        private val waiters = ArrayList<() -> Unit>()

        fun complete(value: Any?) = settle(value, null)
        fun fail(t: Throwable) = settle(null, t)

        private fun settle(value: Any?, error: Throwable?) {
            val callbacks: List<() -> Unit>
            synchronized(lock) {
                if (done) return
                this.value = value
                this.error = error
                done = true
                callbacks = waiters.toList()
                waiters.clear()
            }
            latch.countDown()
            callbacks.forEach { it() }
        }

        fun isDone(): Boolean = synchronized(lock) { done }
        fun result(): Any? = synchronized(lock) {
            check(done)
            error?.let { throw it }
            value
        }

        fun onComplete(callback: () -> Unit) {
            val runNow = synchronized(lock) {
                if (done) true else { waiters += callback; false }
            }
            if (runNow) callback()
        }

        fun await(pump: () -> Boolean): Any? {
            while (!isDone()) {
                if (!pump()) Thread.sleep(1)
            }
            while (pump()) Unit
            return result()
        }

        fun awaitBlocking(): Any? {
            latch.await()
            return result()
        }
    }

    private class ActorValue(
        private val engine: Engine,
        private val name: String,
        classFrame: FrameSpec,
        args: List<Any?>,
    ) {
        private val instance: InstanceValue
        private val owner: Owner

        init {
            owner = Owner(engine, engine.track(engine.actorFactory?.create(name)), false)
            // Construction is synchronous and the actor is not published yet.
            // Running it on the spawning thread avoids deadlocking when actors
            // share a single-thread host pool. Await blocks during construction,
            // matching the synchronous ActorSpawn contract.
            owner.bindCurrentThread()
            val construction = Fiber(engine, classFrame, null, owner, suspends = false)
            args.forEach { construction.push(it) }
            instance = try {
                construction.run() as? InstanceValue
                    ?: throw VeloError("Actor '$name' constructor returned no instance")
            } catch (_: FiberSuspend) {
                throw VeloError("Actor '$name' constructor suspended unexpectedly")
            }
        }

        fun call(methodIndex: Int, args: List<Any?>): VFuture {
            val future = VFuture()
            val task = Runnable {
                try {
                    val fn = instance.env.get(methodIndex) as FuncValue
                    val fiber = Fiber(engine, fn.frame, fn.captured, owner)
                    args.forEach { fiber.push(it) }
                    engine.drive(fiber, owner) { result, failure ->
                        if (failure != null) {
                            future.fail(VeloError("actor '$name' failed: ${failure.message ?: failure}", failure))
                        } else {
                            future.complete(when (result) {
                                is FuncValue -> CallbackValue(result)
                                else -> fiber.transfer(result)
                            })
                        }
                    }
                } catch (t: Throwable) {
                    future.fail(VeloError("actor '$name' failed: ${t.message ?: t}", t))
                }
            }
            owner.submit(task)
            return future
        }

    }

    private fun drive(fiber: Fiber, owner: TaskOwner, complete: (Any?, Throwable?) -> Unit) {
        try {
            complete(fiber.run(), null)
        } catch (suspend: FiberSuspend) {
            suspend.future.onComplete {
                owner.submit(Runnable {
                    try {
                        fiber.push(suspend.future.result())
                        drive(fiber, owner, complete)
                    } catch (t: Throwable) {
                        complete(null, t)
                    }
                })
            }
        } catch (t: Throwable) {
            complete(null, t)
        }
    }

    private companion object {
        const val ENTRY_FRAME = 0
        const val CHECK_MASK = 2047L
    }
}
