package compiler.nodes

import core.Op

import compiler.Context
import java.util.concurrent.atomic.AtomicInteger

private val forCounter = AtomicInteger(0)

/**
 * `for name in start .. end { body }` — an ascending integer range, `end`
 * exclusive (evaluated once). The loop is driven by a hidden counter in the
 * enclosing frame; each iteration binds `name` **fresh** in the body's
 * per-iteration scope (initialised from the counter), so a closure capturing
 * `name` sees that iteration's value — the modern for-in semantics.
 *
 * Layout: `[cond] If [PRE] [body] [POST] [incr] back`, where PRE/POST are the
 * per-iteration `ScopeEnter`/`ScopeLeave` (or no-ops when the body makes no
 * closure), and `continue` targets `incr` so the step always runs.
 */
data class ForRangeNode(
    val name: String,
    val start: Node,
    val end: Node,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val scope = ctx.block()
        val tag = forCounter.getAndIncrement()

        // Hidden counter + bound (enclosing frame — persist across iterations).
        start.compile(scope)
        val counter = scope.def("\$for_h_$tag", IntType)
        scope.add(Op.Store(counter.index))
        end.compile(scope)
        val bound = scope.def("\$for_e_$tag", IntType)
        scope.add(Op.Store(bound.index))

        // cond: counter < bound
        val condCtx = scope.block()
        condCtx.add(Op.Load(counter.index))
        condCtx.add(Op.Load(bound.index))
        condCtx.add(Op.Swap)
        condCtx.add(Op.More)
        val condLen = condCtx.size()

        // body: the loop variable and any body locals form the per-iteration scope.
        val scopeBase = ctx.frame.varCounter.get()
        val bodyCtx = scope.block()
        bodyCtx.loopBody = true
        val loopVar = bodyCtx.def(name, IntType)
        bodyCtx.add(Op.Load(counter.index)) // name = counter
        bodyCtx.add(Op.Store(loopVar.index))
        body.compile(bodyCtx)
        val bodyLen = bodyCtx.size()
        val scopeCount = ctx.frame.varCounter.get() - scopeBase
        val scoped = bodyCtx.operations().any { it is Op.Frame }

        // incr: counter = counter + 1
        val incrCtx = scope.block()
        incrCtx.add(Op.Load(counter.index))
        incrCtx.add(Op.Push(1))
        incrCtx.add(Op.Add)
        incrCtx.add(Op.Store(counter.index))
        val incrLen = incrCtx.size()

        backpatchLoop(
            bodyCtx, scoped,
            breakDist = { m -> bodyLen + incrLen - m + 1 },
            contDist = { m -> bodyLen - m },
        )

        assembleLoop(scope, condCtx, bodyCtx, incrCtx, condLen, bodyLen, incrLen, scoped, scopeBase, scopeCount)
        ctx.merge(scope)
        return VoidType
    }
}

/**
 * `for name in array { body }` — iterates an `array[T]` in order (evaluated
 * once), binding `name` fresh per iteration in the body's scope, exactly like
 * [ForRangeNode] but over a hidden index.
 */
data class ForEachNode(
    val name: String,
    val collection: Node,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val scope = ctx.block()
        val tag = forCounter.getAndIncrement()

        val collType = collection.compile(scope)
        if (collType !is ArrayType) {
            throw IllegalArgumentException("'for .. in' expects an array, got ${collType.log()}")
        }
        val collVar = scope.def("\$for_coll_$tag", collType)
        scope.add(Op.Store(collVar.index))
        scope.add(Op.Push(0))
        val idxVar = scope.def("\$for_idx_$tag", IntType)
        scope.add(Op.Store(idxVar.index))

        // cond: idx < coll.len
        val condCtx = scope.block()
        condCtx.add(Op.Load(idxVar.index))
        condCtx.add(Op.Load(collVar.index))
        condCtx.add(Op.ArrLen)
        condCtx.add(Op.Swap)
        condCtx.add(Op.More)
        val condLen = condCtx.size()

        // body: name = coll[idx] (per-iteration), then the user body.
        val scopeBase = ctx.frame.varCounter.get()
        val bodyCtx = scope.block()
        bodyCtx.loopBody = true
        val elemVar = bodyCtx.def(name, collType.derived)
        bodyCtx.add(Op.Load(collVar.index))
        bodyCtx.add(Op.Load(idxVar.index))
        bodyCtx.add(Op.Push(1))
        bodyCtx.add(Op.ArrLoad)
        bodyCtx.add(Op.Store(elemVar.index))
        body.compile(bodyCtx)
        val bodyLen = bodyCtx.size()
        val scopeCount = ctx.frame.varCounter.get() - scopeBase
        val scoped = bodyCtx.operations().any { it is Op.Frame }

        // incr: idx = idx + 1
        val incrCtx = scope.block()
        incrCtx.add(Op.Load(idxVar.index))
        incrCtx.add(Op.Push(1))
        incrCtx.add(Op.Add)
        incrCtx.add(Op.Store(idxVar.index))
        val incrLen = incrCtx.size()

        backpatchLoop(
            bodyCtx, scoped,
            breakDist = { m -> bodyLen + incrLen - m + 1 },
            contDist = { m -> bodyLen - m },
        )

        assembleLoop(scope, condCtx, bodyCtx, incrCtx, condLen, bodyLen, incrLen, scoped, scopeBase, scopeCount)
        ctx.merge(scope)
        return VoidType
    }
}

/** Emit the shared `[cond] If [PRE] [body] [POST] [incr] back` for a `for`. */
private fun assembleLoop(
    scope: Context,
    condCtx: Context,
    bodyCtx: Context,
    incrCtx: Context,
    condLen: Int,
    bodyLen: Int,
    incrLen: Int,
    scoped: Boolean,
    scopeBase: Int,
    scopeCount: Int,
) {
    scope.merge(condCtx)
    scope.add(Op.If(elseSkip = bodyLen + incrLen + 3))
    scope.add(if (scoped) Op.ScopeEnter(scopeBase, scopeCount) else Op.Move(count = 0))
    scope.merge(bodyCtx)
    scope.add(if (scoped) Op.ScopeLeave else Op.Move(count = 0))
    scope.merge(incrCtx)
    scope.add(Op.Move(count = -(condLen + bodyLen + incrLen + 4)))
}
