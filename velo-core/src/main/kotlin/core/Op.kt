package core

/**
 * The literal a null pointer compiles to: `Op.Push(NullPtr)`. The VM maps it
 * to its runtime null-pointer record when the push executes; keeping the
 * sentinel here keeps runtime record types out of the bytecode contract.
 */
object NullPtr {
    override fun toString(): String = "nullptr"
}

/**
 * The Velo instruction set — one nested type per operation.
 *
 * An [Op] is inert data: operand fields only, no behaviour. The compiler
 * emits ops, [Bytecode] serializes them into `.vbc`, and the VM's
 * interpreter executes them. Each op's KDoc states its stack effect as
 * `[before] -> [after]` with the stack top on the right.
 *
 * [opcode] is the single source of truth for the `.vbc` encoding: the writer
 * stores it as the instruction tag and the reader dispatches on it. Opcode
 * values are part of the bytecode format — never renumber an existing op;
 * new ops take fresh values (gaps in the numbering are retired opcodes).
 *
 * Values on the operand stack are dynamically one of: int, float, byte,
 * bool, str, or a reference (array, dict, class instance, function, pointer,
 * actor, future, native object). The compiler's static typing guarantees the
 * shapes the ops expect; the VM does not re-check.
 */
sealed interface Op {

    val opcode: Int

    // ---- Stack manipulation ----

    /**
     * Push the literal [value] — the instruction set's only inline constant.
     * Carries an int, float, byte, bool, str, or [NullPtr].
     * Stack: `[] -> [value]`
     */
    data class Push(val value: Any) : Op {
        override val opcode get() = 0x29
    }

    /** Drop the top value. Stack: `[a] -> []` */
    object Pop : Op {
        override val opcode get() = 0x0c
    }

    /** Duplicate the top value. Stack: `[a] -> [a, a]` */
    object Dup : Op {
        override val opcode get() = 0x0d
    }

    /** Swap the two top values. Stack: `[a, b] -> [b, a]` */
    object Swap : Op {
        override val opcode get() = 0x34
    }

    /** Rotate the three top values. Stack: `[a, b, c] -> [c, a, b]` */
    object Rot : Op {
        override val opcode get() = 0x2c
    }

    // ---- Arithmetic (numeric; int/float/byte promote per vm.Numbers) ----

    /** Stack: `[a, b] -> [a + b]` */
    object Add : Op {
        override val opcode get() = 0x26
    }

    /** Stack: `[a, b] -> [a - b]` */
    object Sub : Op {
        override val opcode get() = 0x1a
    }

    /** Stack: `[a, b] -> [a * b]` */
    object Mul : Op {
        override val opcode get() = 0x1e
    }

    /** Stack: `[a, b] -> [a / b]` */
    object Div : Op {
        override val opcode get() = 0x0b
    }

    /** Integer remainder. Stack: `[a, b] -> [a % b]` */
    object Rem : Op {
        override val opcode get() = 0x2a
    }

    // ---- Bitwise (int operands; booleans compile to If-chains instead) ----

    /** Stack: `[a, b] -> [a and b]` */
    object And : Op {
        override val opcode get() = 0x02
    }

    /** Stack: `[a, b] -> [a or b]` */
    object Or : Op {
        override val opcode get() = 0x21
    }

    /** Stack: `[a, b] -> [a xor b]` */
    object Xor : Op {
        override val opcode get() = 0x35
    }

    /** Stack: `[value, bits] -> [value shl bits]` */
    object Shl : Op {
        override val opcode get() = 0x46
    }

    /** Stack: `[value, bits] -> [value shr bits]` */
    object Shr : Op {
        override val opcode get() = 0x47
    }

    // ---- Comparison ----

    /** Integer greater-than. Stack: `[a, b] -> [a > b]` */
    object More : Op {
        override val opcode get() = 0x1b
    }

    /** Structural equality of any two values. Stack: `[a, b] -> [a == b]` */
    object Equals : Op {
        override val opcode get() = 0x0e
    }

    // ---- Conversions / hashing ----

    /** Unicode code point to 1-char string. Stack: `[int] -> [str]` */
    object IntChar : Op {
        override val opcode get() = 0x14
    }

    /** Stack: `[int] -> [str]` */
    object IntStr : Op {
        override val opcode get() = 0x15
    }

    /** Stack: `[float] -> [str]` */
    object FloatStr : Op {
        override val opcode get() = 0x49
    }

    /** Widen an int (or byte) to a float. Stack: `[int] -> [float]` */
    object IntToFloat : Op {
        override val opcode get() = 0x16
    }

    /** Narrow a float to an int, truncating toward zero. Stack: `[float] -> [int]` */
    object FloatToInt : Op {
        override val opcode get() = 0x17
    }

    /** Narrow an int to a byte (low 8 bits, sign-extended). Stack: `[int] -> [byte]` */
    object IntToByte : Op {
        override val opcode get() = 0x1f
    }

    /** Parse a decimal string. Stack: `[str] -> [int]` */
    object StrInt : Op {
        override val opcode get() = 0x40
    }

    /** Hash of the top value. Stack: `[a] -> [hash(a)]` */
    object Hash : Op {
        override val opcode get() = 0x48
    }

    // ---- Strings ----

    /** Concatenation. Stack: `[a, b] -> [a + b]` */
    object StrCon : Op {
        override val opcode get() = 0x2e
    }

    /** Stack: `[str] -> [length]` */
    object StrLen : Op {
        override val opcode get() = 0x30
    }

    /** Code point at index. Stack: `[str, index] -> [int]` */
    object StrIndex : Op {
        override val opcode get() = 0x2f
    }

    /** Substring. Stack: `[str, end, start] -> [result]` */
    object StrSub : Op {
        override val opcode get() = 0x33
    }

    // ---- Arrays ----

    /** Allocate an array of `size` empty cells. Stack: `[size] -> [array]` */
    object ArrNew : Op {
        override val opcode get() = 0x03
    }

    /** Stack: `[array] -> [length]` */
    object ArrLen : Op {
        override val opcode get() = 0x05
    }

    /**
     * Load `count` consecutive elements starting at `index`.
     * Stack: `[array, index, count] -> [e_index, ..., e_index+count-1]`
     */
    object ArrLoad : Op {
        override val opcode get() = 0x04
    }

    /**
     * Store `count` values into the array starting at `index`; the array
     * reference is pushed back (mutated in place).
     * Stack: `[v_1 ... v_count, array, index, count] -> [array]`
     */
    object ArrStore : Op {
        override val opcode get() = 0x08
    }

    /**
     * `System.arraycopy` equivalent.
     * Stack: `[dst, src, length, dstPos, srcPos] -> []`
     */
    object ArrCopy : Op {
        override val opcode get() = 0x32
    }

    // ---- Variables ----

    /** Load local variable [index]. Stack: `[] -> [value]` */
    data class Load(val index: Int) : Op {
        override val opcode get() = 0x0f
    }

    /** Store into local variable [index]. Stack: `[value] -> []` */
    data class Store(val index: Int) : Op {
        override val opcode get() = 0x2d
    }

    // ---- Control flow ----

    /**
     * Conditional: on `false` skip [elseSkip] following ops.
     * Stack: `[bool] -> []`
     */
    data class If(val elseSkip: Int) : Op {
        override val opcode get() = 0x12
    }

    /** Unconditional skip of [count] following ops. Stack: `[] -> []` */
    data class Move(val count: Int) : Op {
        override val opcode get() = 0x1d
    }

    /**
     * Enter a fresh lexical scope for a loop body: push a new variable scope
     * holding [count] slots starting at index [base], chained to the current
     * scope. This is an **environment** change only — no call frame, no control
     * transfer — so jumps and returns still act on the enclosing frame. The loop
     * re-executes it each iteration, giving the body's locals a fresh binding per
     * iteration (what a closure created in the body then captures). Emitted only
     * when the body creates a closure; otherwise the body stays flat.
     * Stack: `[] -> []`
     */
    data class ScopeEnter(val base: Int, val count: Int) : Op {
        override val opcode get() = 0x22
    }

    /** Leave the innermost scope pushed by [ScopeEnter]. Stack: `[] -> []` */
    object ScopeLeave : Op {
        override val opcode get() = 0x23
    }

    /** Stop the whole program. */
    object Halt : Op {
        override val opcode get() = 0x11
    }

    /**
     * Return from the current frame; if its operand stack is non-empty, the
     * top value is pushed onto the caller's stack as the return value.
     */
    object Ret : Op {
        override val opcode get() = 0x2b
    }

    /**
     * Push a function value for the bytecode frame [num], capturing the
     * current frame's variable chain — lexical (definition-site) scoping:
     * captured outer variables stay reachable after the defining frame
     * returns. Stack: `[] -> [func]`
     */
    data class Frame(val num: Int) : Op {
        override val opcode get() = 0x18
    }

    /**
     * Materialise the currently executing class frame as a class-instance
     * value — what `new X(...)` leaves behind. Stack: `[] -> [instance]`
     */
    object Instance : Op {
        override val opcode get() = 0x42
    }

    /**
     * Resolve the method [name] on the receiver instance whose scope is this
     * frame's lexical parent, and push its function value — the dynamic half of
     * **interface dispatch**. A concrete `instance.method(...)` knows the
     * method's slot statically (`Op.Load`); an interface-typed receiver does
     * not, because the concrete class is only known at run time. This op is
     * emitted inside the one-op dispatch wrapper an interface call builds, which
     * runs with the receiver as its parent scope (the same `classParent` setup a
     * concrete call uses), so the lookup consults the receiver class's method
     * table (built at load time, keyed by class frame). Stack: `[] -> [func]`
     */
    data class MethodLoad(val name: String) : Op {
        override val opcode get() = 0x19
    }

    /**
     * Invoke method [method] on an interface-typed receiver, dispatching on the
     * receiver's runtime kind — the outer half of interface dispatch, emitted in
     * place of a concrete call's `Op.Call(classParent = true)`. Stack matches that
     * call: the dispatch wrapper (a function value) on top, then [args] arguments,
     * then the receiver.
     *
     * If the receiver is a Velo class instance, this behaves exactly like a
     * `classParent` call — it enters the wrapper, whose [MethodLoad] resolves the
     * slot from the receiver's method table. If the receiver is a **native handle**
     * (a host object satisfying the interface structurally), the wrapper is
     * discarded and the method is resolved by name on the host class and invoked
     * across the native boundary. [args] is the argument count.
     */
    data class InterfaceCall(val method: String, val args: Int) : Op {
        override val opcode get() = 0x1c
    }

    /**
     * Invoke a callable. Stack (top to bottom): the callable; [args]
     * arguments; iff [classParent] — the receiver instance whose variables
     * become the new frame's parent scope (`instance.method(...)` dispatch).
     * Otherwise the function's captured (definition-site) scope is used,
     * which is what makes escaping closures work.
     *
     * A callback owned by another actor is not entered locally; its arguments
     * are structurally cloned and delivered to the owner's dispatcher. When
     * [callbackResult] is false (a `void` callback) the delivery is
     * fire-and-forget; when true (a value-returning callback) the caller
     * blocks for the owner's reply and the decoded result is pushed.
     */
    data class Call(val args: Int, val classParent: Boolean = false, val callbackResult: Boolean = false) : Op {
        override val opcode get() = 0x09
    }

    // ---- Pointers ----

    /** Box the top value into a fresh pointer. Stack: `[value] -> [ptr]` */
    object PtrNew : Op {
        override val opcode get() = 0x50
    }

    /** Dereference. Stack: `[ptr] -> [value]` */
    object PtrLoad : Op {
        override val opcode get() = 0x51
    }

    /** Store through a pointer. Stack: `[value, ptr] -> []` */
    object PtrStore : Op {
        override val opcode get() = 0x52
    }

    /** Pointer to local variable [varIndex] (`&x`). Stack: `[] -> [ptr]` */
    data class PtrRef(val varIndex: Int) : Op {
        override val opcode get() = 0x53
    }

    /** Pointer to an array element (`&a[i]`). Stack: `[array, index] -> [ptr]` */
    object PtrRefIndex : Op {
        override val opcode get() = 0x54
    }

    // ---- Native interop ----

    /**
     * Invoke entry [poolIndex] of the program's native pool — the only
     * opcode of the native interop. Stack (top to bottom): the arguments
     * (first argument on top), then the receiver instance for method
     * entries. [args] carries the call-site Velo types used to convert each
     * argument across the boundary; for callback parameters this is the
     * actual function signature, which arms host-side argument validation.
     * Constructor entries push the new native instance; method entries push
     * the converted result, or nothing for void methods.
     */
    data class NativeCall(val poolIndex: Int, val args: List<VmType>) : Op {
        override val opcode get() = 0x43
    }

    // ---- Actors / futures ----

    /**
     * Instantiate an `actor class` on a fresh worker thread: pop [args]
     * constructor arguments, structurally clone them (no aliasing of caller
     * state), run constructor frame [classFrameNum] on the worker, push an
     * actor reference. One opcode because every step happens on another
     * thread — the cross-thread handshake stays atomic from the bytecode's
     * perspective. Stack: `[arg_args ... arg_1] -> [actor]`
     */
    data class ActorSpawn(val classFrameNum: Int, val className: String, val args: Int) : Op {
        override val opcode get() = 0x60
    }

    /**
     * Asynchronous cross-actor method call, emitted by `async recv.method(...)`:
     * pop [args] arguments and the actor receiver, clone the arguments, and
     * submit the call to the receiver's worker — without blocking. Pushes a
     * `future[T]` that [FutureAwait] unwraps, so `await async x.m()` is
     * exactly these two opcodes. Stack: `[actor, arg_args ... arg_1] -> [future]`
     */
    data class ActorCall(val methodVarIndex: Int, val args: Int) : Op {
        override val opcode get() = 0x61
    }

    /**
     * Block until a `future[T]` completes and push its value (decoded into
     * the caller's memory). Awaiting the same future twice yields the same
     * value without re-running the actor method. An actor failure surfaces
     * here as an exception carrying the actor-tagged message.
     * Stack: `[future] -> [value]`
     */
    object FutureAwait : Op {
        override val opcode get() = 0x62
    }
}
