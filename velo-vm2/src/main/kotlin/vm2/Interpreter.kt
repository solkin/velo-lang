package vm2

import core.BoundNative
import core.DataClassInfo
import core.DispatcherFactory
import core.NullPtr
import core.Op
import core.VeloFunction
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

// Opcode tags mirror core.Op.opcode. Kept as file-level `const` so the
// interpreter's dispatch `when` lowers to a JVM tableswitch rather than a chain
// of `is Op.X` type checks. Never renumber — these are the .vbc instruction tags.
private const val OP_AND = 0x02
private const val OP_ARRNEW = 0x03
private const val OP_ARRLOAD = 0x04
private const val OP_ARRLEN = 0x05
private const val OP_ARRSTORE = 0x08
private const val OP_CALL = 0x09
private const val OP_DIV = 0x0b
private const val OP_POP = 0x0c
private const val OP_DUP = 0x0d
private const val OP_EQUALS = 0x0e
private const val OP_LOAD = 0x0f
private const val OP_HALT = 0x11
private const val OP_IF = 0x12
private const val OP_INTCHAR = 0x14
private const val OP_NUMCONV = 0x63
private const val OP_NUMSTR = 0x64
private const val OP_STRNUM = 0x65
private const val OP_FRAME = 0x18
private const val OP_METHODLOAD = 0x19
private const val OP_INTERFACECALL = 0x1c
private const val OP_SUB = 0x1a
private const val OP_MORE = 0x1b
private const val OP_MOVE = 0x1d
private const val OP_SCOPE_ENTER = 0x22
private const val OP_SCOPE_LEAVE = 0x23
private const val OP_MUL = 0x1e
private const val OP_OR = 0x21
private const val OP_ADD = 0x26
private const val OP_PUSH = 0x29
private const val OP_REM = 0x2a
private const val OP_RET = 0x2b
private const val OP_STORE = 0x2d
private const val OP_STRCON = 0x2e
private const val OP_STRINDEX = 0x2f
private const val OP_STRLEN = 0x30
private const val OP_ARRCOPY = 0x32
private const val OP_STRSUB = 0x33
private const val OP_SWAP = 0x34
private const val OP_XOR = 0x35
private const val OP_INSTANCE = 0x42
private const val OP_NATIVECALL = 0x43
private const val OP_SHL = 0x46
private const val OP_SHR = 0x47
private const val OP_USHR = 0x66
private const val OP_HASH = 0x48
private const val OP_CLASSID = 0x67
private const val OP_PTRNEW = 0x50
private const val OP_PTRLOAD = 0x51
private const val OP_PTRSTORE = 0x52
private const val OP_PTRREF = 0x53
private const val OP_PTRREFINDEX = 0x54
private const val OP_ACTORSPAWN = 0x60
private const val OP_ACTORCALL = 0x61
private const val OP_FUTUREAWAIT = 0x62
private const val OP_TRY_ENTER = 0x24
private const val OP_TRY_LEAVE = 0x25
private const val OP_THROW = 0x27

/**
 * The execution engine: a stack machine over the 53-op instruction set, driven
 * by a cooperative **fiber scheduler**.
 *
 * Each in-flight actor message is a [Fiber] whose call stack is held as data
 * (an [Activation] deque), not on the host stack — so a fiber can suspend at an
 * `await` yield point and resume later without unwinding native frames. That is
 * what makes `await` reentrant and keeps the core portable.
 *
 * Actor placement is pluggable ([factory]); values crossing a boundary are
 * structurally transferred ([transfer]); a function handed to a native is
 * wrapped as a host [VeloFunction]. Failures inside an actor surface as a
 * [VeloError] at the `await` that drains the call; an unhandled failure in the
 * main fiber is rethrown from [run]. The event loop stays alive while the main
 * fiber runs, any callback is retained, or actor work is still in flight.
 */
class Interpreter(
    private val frames: Map<Int, FrameSpec>,
    private val dataClasses: Map<Int, DataClassInfo>,
    private val methodTables: Map<Int, Map<String, Int>>,
    private val bound: Array<BoundNative>,
    private val bridge: NativeBridge,
    private val factory: DispatcherFactory?,
    private val registry: core.NativeRegistry,
    private val mainDispatcher: core.Dispatcher? = null,
    private val heap: Heap = NoHeap,
) : NativeSupport {

    private val managed = heap !== NoHeap

    /** The fiber currently driving on this thread. */
    @Volatile private var driving: Fiber? = null

    // Managed-heap GC roots, maintained only in the cooperative single-threaded
    // mode ([trackRoots]) where a stop-the-world mark-sweep is safe: every actor
    // and fiber lives on the one loop thread, so a collect at an allocation
    // safepoint sees a consistent set of roots. Threaded dispatch and NoHeap
    // never touch these sets — they stay empty at zero cost, and the host GC (or
    // nothing) handles reclamation there.
    private val trackRoots = managed && mainDispatcher == null && factory == null
    private val liveFibers = HashSet<Fiber>()
    private val liveActors = ArrayList<ActorRef>()
    private val pendingRoots = HashSet<Any?>()

    /** Velo `data class` metadata keyed by name, for host → Velo re-materialisation. */
    private val dataByName: Map<String, DataClassInfo> = dataClasses.values.associateBy { it.name }

    /**
     * Frame of the stdlib `Error` data class (VEL-9), resolved by its unique name.
     * A runtime failure is rebuilt into an `Error(kind, message)` from this frame;
     * null when the program never imported `std/error` (so nothing can be caught).
     */
    private val errorClassFrameNum: Int? = dataByName["Error"]?.frameNum

    /** A neutral owner for data instances materialised from host values (data is immutable). */
    private val systemActor by lazy { ActorRef(Instance(Frame(FrameSpec.EMPTY, null), -1), LoopDispatcher(loop), "system") }

    companion object {
        /** Marker for "no value produced" — e.g. a void native or an empty return. */
        val NO_VALUE = Any()
    }

    val loop = EventLoop()

    private var opCount = 0L
    @Volatile private var mainDone = false
    @Volatile private var mainError: Throwable? = null
    private val retain = AtomicInteger(0)
    private val awaiting = AtomicInteger(0) // fibers currently parked on an `await`

    /** The actor whose fiber is executing on the current host thread (inline-callback detection). */
    private val runningActor = ThreadLocal<ActorRef?>()

    /**
     * One in-flight unit of execution: its call stack of [Frame]s and the shared
     * operand [ValueStack] those frames window into. [onSettled] is invoked once
     * with either the return value or the failure that escaped. Each fiber owns
     * its own value stack, so a suspended fiber keeps its operands and nested
     * drives never interfere.
     */
    private class Fiber(val actor: ActorRef, val onSettled: (Any?, Throwable?) -> Unit) {
        val callStack = ArrayDeque<Frame>()
        val vs = ValueStack()
        var suspended = false
    }

    /**
     * Create a fiber, registering it as a live GC root for its lifetime and
     * dropping it when it settles. In non-tracking mode (threaded / NoHeap) this
     * is a plain constructor and the registry stays empty.
     */
    private fun newFiber(actor: ActorRef, onSettled: (Any?, Throwable?) -> Unit): Fiber {
        if (!trackRoots) return Fiber(actor, onSettled)
        lateinit var f: Fiber
        f = Fiber(actor) { v, e -> liveFibers.remove(f); onSettled(v, e) }
        liveFibers.add(f)
        return f
    }

    /** Seed and dispatch the main fiber onto the main dispatcher (host-injected or the cooperative loop). */
    private fun launchMain(entryFrameNum: Int) {
        val entry = frames[entryFrameNum] ?: error("No entry frame #$entryFrameNum")
        val entryFrame = Frame(entry, null)
        val main = ActorRef(Instance(entryFrame, entry.num), mainDispatcher ?: LoopDispatcher(loop), "main")
        if (trackRoots) liveActors.add(main)
        val fiber = newFiber(main) { _, err -> mainError = err; mainDone = true; checkStop() }
        // The entry frame takes no arguments: its operand window starts at the
        // base of the (empty) value stack.
        entryFrame.opBase = 0
        entryFrame.retBase = 0
        fiber.callStack.addLast(entryFrame)
        main.dispatcher.execute { drive(fiber) }
    }

    private fun cleanup() {
        factory?.shutdown()
        mainDispatcher?.close()
    }

    /** Run to completion, pumping the event loop on the calling thread. Rethrows a main failure. */
    fun run(entryFrameNum: Int): RunStats {
        val t0 = System.nanoTime()
        launchMain(entryFrameNum)
        loop.run()
        cleanup()
        mainError?.let { throw it }
        return RunStats(opsExecuted = opCount, wallClockMillis = (System.nanoTime() - t0) / 1_000_000, memory = heap.stats())
    }

    /** Launch non-blocking, pumping the event loop on a runtime-owned thread. */
    fun start(entryFrameNum: Int): ProgramHandle {
        launchMain(entryFrameNum)
        val thread = Thread({ loop.run(); cleanup() }, "velo-vm2-loop").apply { isDaemon = true; start() }
        return ProgramHandle(loop, mainDispatcher, factory, thread) { mainError }
    }

    /**
     * The program is finished once the main fiber has returned, nothing is
     * retained by the host, and no fiber is parked awaiting a result. Unawaited
     * "fire-and-forget" actor work is *not* kept alive — matching daemon-style
     * semantics: a self-rescheduling `async` loop a UI never cancels must not
     * pin the program open after its screen (the retain) is gone.
     */
    private fun checkStop() {
        if (mainDone && retain.get() <= 0 && awaiting.get() <= 0) loop.stop()
    }

    private fun retainInc() { retain.incrementAndGet() }
    private fun retainDec() { if (retain.decrementAndGet() <= 0) loop.post { checkStop() } }

    /** A keepalive handle the host can hold to pin the loop open (see [LoopHandle]). */
    val loopHandle: LoopHandle = object : LoopHandle {
        override fun retain() = retainInc()
        override fun release() = retainDec()
    }


    // ---- Fiber driver ----

    private fun drive(fiber: Fiber) {
        val prev = runningActor.get()
        val prevDriving = driving
        runningActor.set(fiber.actor)
        driving = fiber
        // Accumulate the op count in a local so the hot loop never writes the
        // field; folded back in `finally` (additively, so nested drives on this
        // thread don't lose each other's counts).
        var localOps = 0L
        try {
            val cs = fiber.callStack
            while (cs.isNotEmpty()) {
                val act = cs.last()
                val spec = act.spec
                val ops = spec.ops
                val i = act.ip
                if (i >= ops.size) {
                    if (returnFrom(fiber, act)) return
                    continue
                }
                act.ip = i + 1
                localOps++
                // `s` is the fiber's shared operand stack; `act` is the current
                // frame (locals + lexical scope). Operands go through `s`, locals
                // through `act`.
                val s = fiber.vs
                try {
                when (spec.tags[i]) {
                    // ---- hottest: locals, arithmetic, branches ----
                    OP_LOAD -> s.push(act.scope.load((ops[i] as Op.Load).index))
                    OP_STORE -> act.scope.store((ops[i] as Op.Store).index, s.pop())
                    OP_PUSH -> { val v = (ops[i] as Op.Push).value; s.push(if (v === NullPtr) NullPtrValue else v) }
                    OP_IF -> if (!(s.pop() as Boolean)) act.ip = i + 1 + (ops[i] as Op.If).elseSkip
                    OP_MOVE -> act.ip = i + 1 + (ops[i] as Op.Move).count
                    OP_SCOPE_ENTER -> {
                        val op = ops[i] as Op.ScopeEnter
                        // Push a fresh per-iteration environment; execution stays on `act`.
                        act.scope = Frame(FrameSpec(-1, emptyList(), op.base, op.count), parent = act.scope)
                    }
                    OP_SCOPE_LEAVE -> { act.scope = act.scope.parent!! }

                    OP_ADD -> binary(s) { a, b -> Numbers.add(a, b) }
                    OP_SUB -> binary(s) { a, b -> Numbers.sub(a, b) }
                    OP_MUL -> binary(s) { a, b -> Numbers.mul(a, b) }
                    OP_DIV -> binary(s) { a, b -> Numbers.div(a, b) }
                    OP_REM -> binary(s) { a, b -> Numbers.rem(a, b) }
                    OP_MORE -> binary(s) { a, b -> Numbers.more(a, b) }
                    OP_EQUALS -> binary(s) { a, b -> Numbers.equals(a, b, dataClasses) }

                    OP_AND -> binary(s) { a, b -> Numbers.and(a, b) }
                    OP_OR -> binary(s) { a, b -> Numbers.or(a, b) }
                    OP_XOR -> binary(s) { a, b -> Numbers.xor(a, b) }
                    OP_SHL -> binary(s) { a, b -> Numbers.shl(a, b) }
                    OP_SHR -> binary(s) { a, b -> Numbers.shr(a, b) }
                    OP_USHR -> binary(s) { a, b -> Numbers.ushr(a, b) }

                    // ---- stack manipulation ----
                    OP_POP -> s.pop()
                    OP_DUP -> s.push(s.peek())
                    OP_SWAP -> { val b = s.pop(); val a = s.pop(); s.push(b); s.push(a) }

                    // ---- conversions / hashing ----
                    OP_INTCHAR -> s.push(core.codePointToString(Numbers.intInt(s.pop())))
                    OP_NUMCONV -> s.push(Numbers.convert(s.pop(), (ops[i] as Op.NumConv).to))
                    OP_NUMSTR -> s.push(Numbers.numStr(s.pop()))
                    OP_STRNUM -> s.push(Numbers.strNum(s.pop() as String, (ops[i] as Op.StrNum).to))
                    OP_HASH -> s.push(Numbers.hash(s.pop(), dataClasses))
                    OP_CLASSID -> s.push((s.pop() as Instance).frameNum)

                    // ---- strings ----
                    OP_STRCON -> binary(s) { a, b -> (a as String) + (b as String) }
                    OP_STRLEN -> s.push((s.pop() as String).length)
                    OP_STRINDEX -> {
                        val index = Numbers.intInt(s.pop()); val str = s.pop() as String
                        s.push(str[index].code)
                    }
                    OP_STRSUB -> {
                        val start = Numbers.intInt(s.pop())
                        val end = Numbers.intInt(s.pop())
                        val str = s.pop() as String
                        s.push(str.substring(start, end))
                    }

                    // ---- arrays ----
                    OP_ARRNEW -> { s.push(heap.track(VArray(arrayOfNulls(Numbers.intInt(s.pop()))))); afterAlloc() }
                    OP_ARRLEN -> s.push((s.pop() as VArray).size)
                    OP_ARRLOAD -> {
                        val count = Numbers.intInt(s.pop())
                        val index = Numbers.intInt(s.pop())
                        val array = s.pop() as VArray
                        for (k in 0 until count) s.push(array.data[index + k])
                    }
                    OP_ARRSTORE -> {
                        val count = Numbers.intInt(s.pop())
                        val index = Numbers.intInt(s.pop())
                        val array = s.pop() as VArray
                        for (k in count - 1 downTo 0) array.data[index + k] = s.pop()
                        s.push(array)
                    }
                    OP_ARRCOPY -> {
                        val srcPos = Numbers.intInt(s.pop())
                        val dstPos = Numbers.intInt(s.pop())
                        val length = Numbers.intInt(s.pop())
                        val src = s.pop() as VArray
                        val dst = s.pop() as VArray
                        if (src === dst && dstPos > srcPos) {
                            for (k in length - 1 downTo 0) dst.data[dstPos + k] = src.data[srcPos + k]
                        } else {
                            for (k in 0 until length) dst.data[dstPos + k] = src.data[srcPos + k]
                        }
                    }

                    // ---- frames / instances (capture the current frame as a scope) ----
                    OP_FRAME -> { val num = (ops[i] as Op.Frame).num; s.push(FuncValue(frames[num] ?: error("No frame #$num"), act.scope)) }
                    OP_INSTANCE -> { s.push(heap.track(Instance(act.scope, spec.num))); afterAlloc() }
                    OP_METHODLOAD -> s.push(methodLoad(act, (ops[i] as Op.MethodLoad).name))

                    // ---- pointers ----
                    OP_PTRNEW -> s.push(BoxPtr(s.pop()))
                    OP_PTRLOAD -> s.push((s.pop() as Ptr).get())
                    OP_PTRSTORE -> { val p = s.pop() as Ptr; p.set(s.pop()) }
                    OP_PTRREF -> s.push(VarPtr(act.scope, (ops[i] as Op.PtrRef).varIndex))
                    OP_PTRREFINDEX -> {
                        val index = Numbers.intInt(s.pop()); val array = s.pop() as VArray
                        s.push(ArrayPtr(array, index))
                    }

                    // ---- control / calls / actors (may suspend or unwind the fiber) ----
                    OP_CALL -> if (callOp(fiber, op = ops[i] as Op.Call)) return
                    OP_INTERFACECALL -> if (interfaceCall(fiber, ops[i] as Op.InterfaceCall)) return
                    OP_RET -> if (returnFrom(fiber, act)) return
                    OP_HALT -> { cs.clear(); fiber.onSettled(NO_VALUE, null); return }
                    OP_NATIVECALL -> nativeCall(ops[i] as Op.NativeCall, fiber)
                    OP_FUTUREAWAIT -> { val f = s.pop() as VFuture; if (awaitFuture(fiber, f)) return }
                    OP_ACTORSPAWN -> actorSpawn(fiber, act, ops[i] as Op.ActorSpawn)
                    OP_ACTORCALL -> actorCall(fiber, act, ops[i] as Op.ActorCall)

                    // VEL-9. TryEnter records a handler on the current frame;
                    // TryLeave drops it on the normal path; Throw raises the Error
                    // on the stack. The unwind itself is in the per-op catch below.
                    OP_TRY_ENTER -> {
                        val h = act.handlers ?: ArrayDeque<Handler>().also { act.handlers = it }
                        h.addLast(Handler(i + 1 + (ops[i] as Op.TryEnter).catchOffset, act.scope, s.top))
                    }
                    OP_TRY_LEAVE -> act.handlers?.removeLast()
                    OP_THROW -> { val e = s.pop(); throw VeloThrow(e, errorText(e)) }

                    else -> error("unhandled op tag 0x${spec.tags[i].toString(16)}")
                }
                } catch (ex: Throwable) {
                    // VEL-9: route a catchable failure to the nearest `try` handler,
                    // else rethrow to the fiber-level handler in drive's outer catch.
                    // (A user `halt` is OP_HALT — inline, never thrown — so it is
                    // never seen here.)
                    if (!unwindToHandler(fiber, ex)) throw ex
                }
            }
        } catch (t: Throwable) {
            fiber.onSettled(null, tag(fiber.actor, t))
        } finally {
            opCount += localOps
            runningActor.set(prev)
            driving = prevDriving
        }
    }

    /** Roots for a stop-the-world collection: the fiber's whole operand stack plus every scope-chain slot. */
    private fun rootsOf(fiber: Fiber): Sequence<Any?> = sequence {
        val vs = fiber.vs
        for (j in 0 until vs.top) yield(vs.a[j])      // every active frame's operands live here
        for (act in fiber.callStack) {
            var s: Frame? = act.scope
            while (s != null) {
                for (v in s.localValues()) yield(v)
                s = s.parent
            }
        }
    }

    /**
     * Every GC root across all live actors: each actor's instance state, each
     * in-flight fiber's operands + scope chains, and message arguments still in
     * transit to a mailbox — those live only inside a queued dispatcher task
     * (invisible to the mark phase) between [transfer] and the receiving fiber's
     * seed, so they are pinned in [pendingRoots] for that window.
     */
    private fun allRoots(): Sequence<Any?> = sequence {
        for (a in liveActors) yield(a.instance)
        for (f in liveFibers) yieldAll(rootsOf(f))
        yieldAll(pendingRoots)
    }

    /** Tag a raw failure with the failing actor once, leaving an already-tagged error intact. */
    private fun tag(actor: ActorRef, t: Throwable): Throwable =
        if (t is VeloError) t else VeloError("actor '${actor.name}' failed: ${t.message}", t)

    /**
     * Pop [act], forwarding its top value to the caller and rewinding the value
     * stack to the caller's window (also dropping the call's args and receiver).
     * @return true once the fiber's call stack empties.
     */
    private fun returnFrom(fiber: Fiber, act: Frame): Boolean {
        val vs = fiber.vs
        val value = if (vs.top > act.opBase) vs.a[vs.top - 1] else NO_VALUE
        vs.top = act.retBase
        fiber.callStack.removeLast()
        if (fiber.callStack.isEmpty()) {
            fiber.onSettled(value, null)
            return true
        }
        if (value !== NO_VALUE) vs.push(value)
        return false
    }

    /** Park [fiber] on [f] unless already settled. @return true if it suspended. */
    private fun awaitFuture(fiber: Fiber, f: VFuture): Boolean {
        if (f.isDone) {
            f.errorNow()?.let { throw it }
            fiber.vs.push(f.getNow())
            return false
        }
        fiber.suspended = true
        awaiting.incrementAndGet()
        f.onComplete { value, err ->
            fiber.actor.dispatcher.execute {
                awaiting.decrementAndGet() // resuming — no longer parked
                fiber.suspended = false
                if (err != null) {
                    // VEL-9: a failed await must reach a `try` around it, exactly
                    // like the synchronous already-done path (which throws into the
                    // drive loop). Route through a handler; only settle as a failure
                    // when nothing catches it.
                    val tagged = tag(fiber.actor, err)
                    if (unwindToHandler(fiber, tagged)) drive(fiber)
                    else fiber.onSettled(null, tagged)
                } else {
                    fiber.vs.push(value)
                    drive(fiber)
                }
                checkStop() // the resumed fiber may have been the last awaiter
            }
        }
        return true
    }

    /**
     * Dynamic method resolution for interface dispatch. The dispatch wrapper
     * frame [act] runs with the receiver instance's scope as its lexical parent
     * (set by the enclosing `classParent` call), so that parent identifies the
     * concrete class ([FrameSpec.num]). Its method table maps the requested
     * [name] to a slot, which is loaded from the instance scope — the same
     * function value a concrete call would `Op.Load` at a statically known slot.
     */
    private fun methodLoad(act: Frame, name: String): Any? {
        val instanceScope = act.parent
            ?: error("interface dispatch wrapper has no receiver scope")
        val table = methodTables[instanceScope.spec.num]
            ?: error("class frame #${instanceScope.spec.num} has no method table")
        val index = table[name]
            ?: error("class frame #${instanceScope.spec.num} has no method '$name'")
        return instanceScope.load(index)
    }

    /**
     * Outer half of interface dispatch. For a Velo class instance this is exactly
     * a `classParent` call — it enters the dispatch wrapper, whose [Op.MethodLoad]
     * resolves the method against the receiver's method table. For a native handle
     * the wrapper is discarded and the method is resolved by name on the host
     * class and invoked across the boundary.
     */
    private fun interfaceCall(fiber: Fiber, op: Op.InterfaceCall): Boolean {
        val vs = fiber.vs
        val callable = vs.pop()                 // the dispatch wrapper
        val n = op.args
        val opBase = vs.top - n
        val receiver = vs.a[opBase - 1]
        if (receiver is Instance) {
            val fn = callable as FuncValue
            val callee = Frame(fn.frame, receiver.scope)
            callee.opBase = opBase
            callee.retBase = opBase - 1
            fiber.callStack.addLast(callee)
            return false
        }
        // Native handle: args sit above the receiver, first parameter on top.
        val args = ArrayList<Any?>(n)
        repeat(n) { args.add(vs.pop()) }
        vs.pop()                                // the receiver handle
        val result = bridge.callByName(receiver!!, op.method, args, registry, fiber.actor, this)
        if (result !== NO_VALUE) vs.push(result)
        return false
    }

    private fun callOp(fiber: Fiber, op: Op.Call): Boolean {
        val vs = fiber.vs
        val callable = vs.pop()
        val n = op.args

        // Foreign-actor callback: arguments are shipped to the owner's
        // dispatcher, not entered locally — the rare path keeps the list build.
        if (callable is CallbackHandle && callable.owner !== fiber.actor) {
            val popped = ArrayList<Any?>(n)
            repeat(n) { popped.add(vs.pop()) }       // popped[0] = top
            if (op.classParent) vs.pop()             // receiver unused for callbacks
            return invokeCallback(fiber, callable, popped.asReversed(), op.callbackResult)
        }
        val fn = (if (callable is CallbackHandle) callable.fn else callable) as FuncValue

        // After popping the callable, the n args already sit at the top of the
        // value stack — they become the callee's operands in place (no copy).
        // The callee's window simply starts there; on return the stack rewinds to
        // `retBase`, which also drops the args and (for a class call) the receiver.
        val opBase = vs.top - n
        if (op.reverseArgs) {                        // method wrapper: callee wants the args reversed
            var lo = opBase
            var hi = vs.top - 1
            while (lo < hi) { val t = vs.a[lo]; vs.a[lo] = vs.a[hi]; vs.a[hi] = t; lo++; hi-- }
        }
        val parent = if (op.classParent) (vs.a[opBase - 1] as Instance).scope else fn.captured
        val callee = Frame(fn.frame, parent)
        callee.opBase = opBase
        callee.retBase = if (op.classParent) opBase - 1 else opBase
        fiber.callStack.addLast(callee)
        return false
    }

    /** Invoke a foreign-actor callback: ship transferred args to the owner's dispatcher. */
    private fun invokeCallback(fiber: Fiber, handle: CallbackHandle, args: List<Any?>, wantResult: Boolean): Boolean {
        val owner = handle.owner
        val transferred = args.map { transfer(it, fiber.actor) }
        if (!wantResult) {
            if (trackRoots) pendingRoots.addAll(transferred)
            owner.dispatcher.execute {
                val msg = newFiber(owner) { _, _ -> }
                seed(msg, handle.fn, transferred)
                if (trackRoots) pendingRoots.removeAll(transferred.toSet())
                drive(msg)
            }
            return false
        }
        val f = VFuture()
        if (trackRoots) pendingRoots.addAll(transferred)
        owner.dispatcher.execute {
            val msg = newFiber(owner) { result, err ->
                if (err != null) f.fail(err) else f.complete(transfer(result, owner))
            }
            seed(msg, handle.fn, transferred)
            if (trackRoots) pendingRoots.removeAll(transferred.toSet())
            drive(msg)
        }
        return awaitFuture(fiber, f)
    }

    /**
     * Collection safepoint after a managed allocation. Active only in the
     * cooperative single-threaded mode ([trackRoots]); [allRoots] then spans
     * every actor and in-flight fiber, so the sweep is safe with actors present.
     */
    private fun afterAlloc() {
        if (trackRoots) heap.maybeCollect { allRoots() }
    }

    private inline fun binary(s: ValueStack, f: (Any?, Any?) -> Any?) {
        val b = s.pop(); val a = s.pop(); s.push(f(a, b))
    }

    private fun nativeCall(op: Op.NativeCall, fiber: Fiber) {
        val s = fiber.vs
        val entry = bound[op.poolIndex]
        val args = ArrayList<Any?>(op.args.size)
        repeat(op.args.size) { args.add(s.pop()) }
        val receiver = if (entry.isConstructor) null else s.pop()
        // Tag a host-call failure so it surfaces as an Error with kind "native"
        // (VEL-9), matching velo-vm. A plain RuntimeException (not VeloError) so an
        // actor still re-tags it with its own context on the way out — `tag()`
        // leaves an existing VeloError untouched.
        val result = try {
            bridge.call(entry, receiver, args, fiber.actor, this)
        } catch (t: Throwable) {
            throw RuntimeException("Native call ${entry.ref} failed: ${t.message ?: t}", t)
        }
        if (result !== NO_VALUE) s.push(result)
    }

    // ---- Actors ----

    private fun actorSpawn(fiber: Fiber, act: Frame, op: Op.ActorSpawn) {
        val args = popDeclaredArgs(fiber.vs, op.args).map { transfer(it, fiber.actor) }
        val classFrame = frames[op.classFrameNum] ?: error("No actor frame #${op.classFrameNum}")
        val dispatcher = factory?.create(op.className) ?: LoopDispatcher(loop)
        // The constructor's lexical parent is the spawning frame [act]; its locals
        // are heap-resident, so the new actor can safely read them across threads.
        val instance = runSync(FuncValue(classFrame, act), args, fiber.actor) as Instance
        val actor = ActorRef(instance, dispatcher, op.className)
        if (trackRoots) liveActors.add(actor)
        fiber.vs.push(actor)
    }

    private fun actorCall(fiber: Fiber, act: Frame, op: Op.ActorCall) {
        val args = popDeclaredArgs(fiber.vs, op.args).map { transfer(it, fiber.actor) }
        val actor = fiber.vs.pop() as ActorRef
        val f = VFuture()
        if (trackRoots) pendingRoots.addAll(args)
        actor.dispatcher.execute {
            val method = actor.instance.scope.load(op.methodVarIndex) as FuncValue
            val msg = newFiber(actor) { result, err ->
                if (err != null) f.fail(err) else f.complete(transfer(result, actor))
            }
            seed(msg, method, args)
            if (trackRoots) pendingRoots.removeAll(args.toSet())
            drive(msg)
        }
        fiber.vs.push(f)
    }

    private fun popDeclaredArgs(s: ValueStack, count: Int): List<Any?> {
        val popped = ArrayList<Any?>(count)
        repeat(count) { popped.add(s.pop()) }
        return popped.asReversed()
    }

    private fun seed(fiber: Fiber, func: FuncValue, args: List<Any?>) {
        val act = Frame(func.frame, func.captured)
        // A seeded frame begins a fresh slice of its fiber's value stack; its
        // arguments become its operands in place.
        act.opBase = fiber.vs.top
        act.retBase = fiber.vs.top
        for (a in args) fiber.vs.push(a)
        fiber.callStack.addLast(act)
    }

    /** Drive a callable to completion synchronously; it must not suspend. Failures rethrow. */
    private fun runSync(func: FuncValue, args: List<Any?>, owner: ActorRef): Any? {
        var result: Any? = NO_VALUE
        var failure: Throwable? = null
        val fiber = newFiber(owner) { v, e -> result = v; failure = e }
        seed(fiber, func, args)
        drive(fiber)
        failure?.let { throw it }
        check(!fiber.suspended) { "this context cannot suspend (await is not allowed here)" }
        return result
    }

    // ---- VEL-9: try/catch/throw ----

    /**
     * Route a raised failure to the nearest active `try` handler, or report none
     * (call stack untouched, so drive's outer catch reports it). On a hit: drop
     * the frames above the handler's, restore that frame's scope and operand-stack
     * depth, push the Error value and jump to the catch block. Handlers live on
     * frames, so a fiber parked on an `await` inside a `try` keeps them for free.
     */
    private fun unwindToHandler(fiber: Fiber, ex: Throwable): Boolean {
        val cs = fiber.callStack
        if (cs.none { it.handlers?.isNotEmpty() == true }) return false
        val error = errorValue(ex)
        val s = fiber.vs
        while (cs.isNotEmpty()) {
            val act = cs.last()
            val handler = act.handlers?.takeIf { it.isNotEmpty() }?.removeLast()
            if (handler != null) {
                act.scope = handler.savedScope
                s.top = handler.savedTop
                s.push(error)
                act.ip = handler.catchIp
                return true
            }
            cs.removeLast()
        }
        return false // unreachable: the pre-scan found a handler
    }

    /**
     * The Velo `Error` value for a raised failure. A user `throw` already carries
     * one ([VeloThrow]); a runtime/actor failure is rebuilt into an
     * `Error(kind, message)` by re-running the stdlib constructor — the same path
     * actor/native marshalling uses.
     */
    private fun errorValue(ex: Throwable): Any? {
        if (ex is VeloThrow) return ex.error
        val frameNum = errorClassFrameNum
            ?: throw IllegalStateException("cannot build an Error: std/error is not in the program", ex)
        val fields = listOf<Any?>(errorKind(ex), ex.message ?: ex.toString())
        return runSync(FuncValue(frames[frameNum]!!, systemActor.instance.scope), fields, systemActor)
    }

    /** Classify a failure into an `ERR_*` kind (see std/error.vel). */
    private fun errorKind(ex: Throwable): String = when {
        ex is ArithmeticException -> "arithmetic"
        ex is IndexOutOfBoundsException -> "bounds"
        ex is NullPointerException -> "null"
        ex.message?.startsWith("Native call ") == true -> "native"
        ex is VeloError && ex.message?.startsWith("actor '") == true -> "actor"
        else -> "generic"
    }

    /** Best-effort "kind: message" from an Error instance — readable diagnostics. */
    private fun errorText(err: Any?): String? {
        if (err !is Instance) return null
        val info = dataClasses[err.frameNum] ?: return null
        return try {
            "${err.scope.load(info.fields[0].index)}: ${err.scope.load(info.fields[1].index)}"
        } catch (_: Throwable) {
            null
        }
    }

    private fun transfer(v: Any?, owner: ActorRef): Any? = when (v) {
        is VArray -> VArray(Array(v.size) { transfer(v.data[it], owner) })
        is FuncValue -> CallbackHandle(owner, v)
        is Instance -> {
            val info = dataClasses[v.frameNum]
            if (info != null) {
                val fields = info.fields.map { transfer(v.scope.load(it.index), owner) }
                runSync(FuncValue(frames[info.frameNum]!!, owner.instance.scope), fields, owner) as Instance
            } else {
                v
            }
        }
        else -> v
    }

    // ---- NativeSupport: host callbacks (core.VeloFunction) ----

    override fun makeCallback(veloFn: Any, defaultOwner: ActorRef): VeloFunction = when (veloFn) {
        is CallbackHandle -> HostCallback(veloFn.owner, veloFn.fn)
        is FuncValue -> HostCallback(defaultOwner, veloFn)
        else -> error("Not a callable value: $veloFn")
    }

    /**
     * Host → Velo. A registered host `data class` counterpart is re-materialised
     * by value into a Velo instance (re-running the Velo constructor with
     * recursively-converted fields); everything else defers to primitive/array
     * marshalling.
     */
    override fun hostToVelo(v: Any?): Any? {
        if (v != null) {
            val binding = registry.dataBindingByJvmClass(v.javaClass)
            if (binding != null) {
                val info = dataByName[binding.veloName]
                    ?: error("Host data '${binding.veloName}' has no Velo data class in this program")
                val fields = info.fields.map { hostToVelo(binding.read(v, it.name)) }
                return runSync(FuncValue(frames[info.frameNum]!!, systemActor.instance.scope), fields, systemActor)
            }
        }
        return bridge.hostToVelo(v)
    }

    /**
     * Velo → Host. A Velo `data class` instance is marshalled by value into its
     * registered host counterpart (host constructor in field order); everything
     * else defers to primitive/array marshalling.
     */
    override fun veloToHost(v: Any?): Any? {
        if (v is Instance) {
            val info = dataClasses[v.frameNum]
            if (info != null) {
                val binding = registry.dataBindingByVeloName(info.name)
                    ?: error("Velo data class '${info.name}' has no registered host counterpart")
                val fields = info.fields.map { veloToHost(v.scope.load(it.index)) }
                return binding.ctorHandle.invokeWithArguments(fields)
            }
        }
        return bridge.veloToHost(v)
    }

    /**
     * A Velo function exposed to native code. Invocations run on the owner's
     * dispatcher; when the host already calls from the owner's own thread the
     * body runs inline (no self-deadlock). [retain]/[release] keep the event
     * loop alive between an out-of-band registration and its eventual firing.
     */
    private inner class HostCallback(val owner: ActorRef, val fn: FuncValue) : VeloFunction {

        override fun post(vararg args: Any?) {
            val velo = args.map { transfer(hostToVelo(it), owner) }
            if (runningActor.get() === owner) {
                runSync(fn, velo, owner)
            } else {
                if (trackRoots) pendingRoots.addAll(velo)
                owner.dispatcher.execute {
                    val msg = newFiber(owner) { _, _ -> }
                    seed(msg, fn, velo)
                    if (trackRoots) pendingRoots.removeAll(velo.toSet())
                    drive(msg)
                }
            }
        }

        override fun call(vararg args: Any?): CompletableFuture<Any?> {
            val velo = args.map { transfer(hostToVelo(it), owner) }
            val cf = CompletableFuture<Any?>()
            if (runningActor.get() === owner) {
                try {
                    val r = runSync(fn, velo, owner)
                    cf.complete(if (r === NO_VALUE) null else veloToHost(r))
                } catch (t: Throwable) {
                    cf.completeExceptionally(t)
                }
            } else {
                if (trackRoots) pendingRoots.addAll(velo)
                owner.dispatcher.execute {
                    val msg = newFiber(owner) { r, e ->
                        if (e != null) cf.completeExceptionally(e)
                        else cf.complete(if (r === NO_VALUE) null else veloToHost(r))
                    }
                    seed(msg, fn, velo)
                    if (trackRoots) pendingRoots.removeAll(velo.toSet())
                    drive(msg)
                }
            }
            return cf
        }

        override fun retain() = retainInc()
        override fun release() = retainDec()
    }
}

/** Hooks the [NativeBridge] uses to reach the scheduler for callback wrapping and value conversion. */
interface NativeSupport {
    fun makeCallback(veloFn: Any, defaultOwner: ActorRef): VeloFunction
    fun hostToVelo(v: Any?): Any?
    fun veloToHost(v: Any?): Any?
}
