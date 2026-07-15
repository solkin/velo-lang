package compiler.nodes

import core.Op

import compiler.Context

/**
 * A `try { ... } catch (Error e) { ... }` statement.
 *
 * Compiles inline in the enclosing frame, mirroring [IfNode]: the try body and
 * the catch body are inline sub-blocks and control moves between them with
 * relative offsets — no closure frames, so a `return`/`break`/`continue` inside
 * either body still jumps straight out.
 *
 *   TryEnter(tryLen+2) [try] TryLeave Move(catchLen) [Store(err); catch]
 *
 * [Op.TryEnter] installs a handler for the try body; on a catchable error the
 * VM unwinds the call stack back to this frame, drops the operands the try body
 * had pushed, and jumps to the catch block (the [Op.Store] that binds the
 * `Error` value to `err`). On normal completion [Op.TryLeave] removes the
 * handler and the [Op.Move] skips the catch block. A user `halt` is never
 * caught.
 */
data class TryNode(
    val tryBody: Node,
    val errName: String,
    val catchBody: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        // Try body: an inline sub-block, like an if-branch. Marked as a try body
        // so a `break`/`continue` escaping it emits a TryLeave for this handler
        // (the normal-exit TryLeave below is jumped over).
        val tryCtx = ctx.block()
        tryCtx.tryBody = true
        tryBody.compile(tryCtx)

        // Catch body: an inline sub-block whose first op stores the caught Error
        // into the catch-local `err` (a fresh slot in the shared frame space,
        // visible only inside the catch block). The class is resolved by name at
        // use sites, so a bare ClassType("Error") is enough here.
        val catchCtx = ctx.block()
        val errVar = catchCtx.def(errName, ClassType(name = "Error"))
        catchCtx.add(Op.Store(errVar.index))
        catchBody.compile(catchCtx)

        // Layout: TryEnter(tryLen+2) [try] TryLeave Move(catchLen) [Store; catch]
        ctx.add(Op.TryEnter(catchOffset = tryCtx.size() + 2))
        ctx.merge(tryCtx)
        ctx.add(Op.TryLeave)
        ctx.add(Op.Move(count = catchCtx.size()))
        ctx.merge(catchCtx)

        return VoidType
    }
}
