package vm

import vm.records.EmptyRecord

/**
 * A lexical scope's variables, backed by a flat slot array indexed by a dense
 * per-frame range `[base, base + slots.size)`. Variables of enclosing scopes
 * (closures, class fields seen from a method) live in [parent] and are reached
 * by walking the chain — the same resolution as before, but each level is now a
 * single subtract-and-bounds-check into an array instead of a `HashMap` lookup
 * with a boxed `Int` key.
 *
 * This works because the compiler numbers each frame's variables contiguously
 * (`CompilerFrame.varCounter`, with child frames continuing from the parent's
 * count), so a frame's indices are exactly `base .. base + count - 1`. Numeric
 * collisions between a parent and a nested scope are resolved by the same
 * innermost-scope-wins walk as the old map chain.
 */
class Vars(
    private val base: Int,
    private val slots: Array<Record>,
    val parent: Vars?,
    /**
     * The bytecode frame number whose scope this is, or -1 for synthetic scopes.
     * For a class-instance scope this is the class frame number, which interface
     * dispatch ([Op.MethodLoad]) uses to find the receiver's method table — the
     * legacy VM's [Vars] otherwise carries no class identity (vm2's Frame does).
     */
    val frameNum: Int = -1,
) {
    fun get(index: Int): Record {
        var scope: Vars? = this
        while (scope != null) {
            val i = index - scope.base
            if (i >= 0 && i < scope.slots.size) return scope.slots[i]
            scope = scope.parent
        }
        throw IllegalArgumentException("Undefined variable $index on var get")
    }

    fun set(index: Int, value: Record): Record {
        var scope: Vars? = this
        while (scope != null) {
            val i = index - scope.base
            if (i >= 0 && i < scope.slots.size) {
                scope.slots[i] = value
                return value
            }
            scope = scope.parent
        }
        throw IllegalArgumentException("Undefined variable $index on var set")
    }

    fun empty(): Boolean = slots.isEmpty()

    /** This scope's own slots (not the parent chain) — for GC tracing and error dumps. */
    fun localRecords(): Array<Record> = slots
}

/**
 * Build a scope from a frame's variable indices (ascending and contiguous, as
 * emitted by the compiler). The slots start uninitialised ([EmptyRecord]); the
 * base is the frame's first index, so a raw variable index maps to `index - base`.
 */
fun createVars(vars: List<Int>, parent: Vars? = null, frameNum: Int = -1): Vars {
    if (vars.isEmpty()) return Vars(base = 0, slots = emptyArray(), parent = parent, frameNum = frameNum)
    // Indices are ascending (declaration order); slots span first..last so a raw
    // index maps to `index - base`. The compiler numbers each frame contiguously,
    // so span == count, but a sparse set (e.g. hand-written bytecode) still maps
    // cleanly — the gaps are just unused EmptyRecord slots.
    val base = vars.first()
    return Vars(base = base, slots = Array(vars.last() - base + 1) { EmptyRecord }, parent = parent, frameNum = frameNum)
}
