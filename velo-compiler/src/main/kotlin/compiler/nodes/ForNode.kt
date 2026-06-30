package compiler.nodes

import core.Op

import compiler.Context
import java.util.concurrent.atomic.AtomicInteger

private val forCounter = AtomicInteger(0)

/**
 * Backpatch a loop body's `break`/`continue` placeholders, given the body's op
 * list and the lengths of the increment step that follows it. `break` lands
 * past the body + increment + back-jump; `continue` lands at the increment (so
 * the step always runs, the key difference from a hand-written `while`).
 */
private fun backpatchLoopBody(ctx: Context, bodyLen: Int, incrLen: Int) {
    val body = ctx.operations()
    for (i in body.indices) {
        val op = body[i]
        if (op is Op.Move && op.count == LOOP_BREAK_MARKER) {
            ctx.replace(i, Op.Move(count = bodyLen + incrLen - i))
        } else if (op is Op.Move && op.count == LOOP_CONTINUE_MARKER) {
            ctx.replace(i, Op.Move(count = bodyLen - i - 1))
        }
    }
}

/**
 * `for name in start .. end { body }` — an ascending integer range, `end`
 * exclusive. Lowers to: init `name = start`, then a loop whose layout is
 * `[cond] If [body] [incr] back-jump`, with `continue` directed at `incr`.
 * The loop variable is scoped to the `for`.
 */
data class ForRangeNode(
    val name: String,
    val start: Node,
    val end: Node,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val scope = ctx.block()

        // init: name = start
        start.compile(scope)
        val loopVar = scope.def(name, IntType)
        scope.add(Op.Store(loopVar.index))

        // cond: name < end
        val condCtx = scope.block()
        condCtx.add(Op.Load(loopVar.index))
        end.compile(condCtx)
        condCtx.add(Op.Swap)
        condCtx.add(Op.More)
        val condLen = condCtx.size()

        // body
        val bodyCtx = scope.block()
        bodyCtx.loopBody = true
        body.compile(bodyCtx)
        val bodyLen = bodyCtx.size()

        // incr: name = name + 1
        val incrCtx = scope.block()
        incrCtx.add(Op.Load(loopVar.index))
        incrCtx.add(Op.Push(1))
        incrCtx.add(Op.Add)
        incrCtx.add(Op.Store(loopVar.index))
        val incrLen = incrCtx.size()

        backpatchLoopBody(bodyCtx, bodyLen, incrLen)

        scope.merge(condCtx)
        scope.add(Op.If(elseSkip = bodyLen + incrLen + 1))
        scope.merge(bodyCtx)
        scope.merge(incrCtx)
        scope.add(Op.Move(count = -(condLen + bodyLen + incrLen + 2)))

        ctx.merge(scope)
        return VoidType
    }
}

/**
 * `for name in array { body }` — iterates an `array[T]` in order, binding
 * `name` to each element. Lowers like [ForRangeNode] over a hidden index, with
 * the array evaluated once into a hidden slot.
 */
data class ForEachNode(
    val name: String,
    val collection: Node,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val scope = ctx.block()
        val tag = forCounter.getAndIncrement()

        // Evaluate the array once and stash it, plus a zero index.
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

        // body: name = coll[idx]; <body>
        val bodyCtx = scope.block()
        bodyCtx.loopBody = true
        bodyCtx.add(Op.Load(collVar.index))
        bodyCtx.add(Op.Load(idxVar.index))
        bodyCtx.add(Op.Push(1))
        bodyCtx.add(Op.ArrLoad)
        val elemVar = bodyCtx.def(name, collType.derived)
        bodyCtx.add(Op.Store(elemVar.index))
        body.compile(bodyCtx)
        val bodyLen = bodyCtx.size()

        // incr: idx = idx + 1
        val incrCtx = scope.block()
        incrCtx.add(Op.Load(idxVar.index))
        incrCtx.add(Op.Push(1))
        incrCtx.add(Op.Add)
        incrCtx.add(Op.Store(idxVar.index))
        val incrLen = incrCtx.size()

        backpatchLoopBody(bodyCtx, bodyLen, incrLen)

        scope.merge(condCtx)
        scope.add(Op.If(elseSkip = bodyLen + incrLen + 1))
        scope.merge(bodyCtx)
        scope.merge(incrCtx)
        scope.add(Op.Move(count = -(condLen + bodyLen + incrLen + 2)))

        ctx.merge(scope)
        return VoidType
    }
}
