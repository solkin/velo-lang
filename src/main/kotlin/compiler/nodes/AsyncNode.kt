package compiler.nodes

import compiler.Context
import vm.operations.ActorCall

/**
 * `async receiver.method(args)` — start a cross-actor method invocation
 * without waiting for it to finish. Produces a value of type
 * [FutureType].
 *
 * Compilation:
 *   - The receiver expression is type-checked; it must resolve to an
 *     [ActorBoundType], otherwise the call has nowhere to go.
 *   - The method is resolved against the underlying class definition
 *     (kept in [ClassType.parent]), reusing the same arity / type checks
 *     as ordinary method dispatch.
 *   - A single [ActorCall] op is emitted; it pushes a `FutureRecord` onto
 *     the operand stack at runtime.
 *
 * The matching `await` keyword is handled by [AwaitNode]; together they
 * compile `await async x.method()` to two opcodes (`ActorCall` +
 * `FutureAwait`) — there is no special "synchronous call" opcode.
 */
data class AsyncNode(val expr: Node) : Node() {

    override fun compile(ctx: Context): Type {
        if (expr !is PropNode || expr.args == null) {
            throw IllegalArgumentException("'async' requires an actor method call: async receiver.method(args)")
        }

        val receiverType = expr.parent.compile(ctx)
        val actorType = receiverType as? ActorBoundType
            ?: throw IllegalArgumentException(
                "'async' requires an actor[T] receiver, but got ${receiverType.log()}"
            )

        val classType = actorType.derived
        // The class type carried by `actor[T]` may be a bare type registered
        // by the parser (no `parent`/`num`). Re-resolve through the compiler
        // context to get the fully-elaborated ClassType — same trick as
        // [ClassElementProp.compile].
        val resolved = (ctx.opt(classType.name)?.type as? ClassType) ?: classType
        val classCtx = resolved.parent
            ?: throw IllegalStateException("Actor class '${classType.name}' has no compilation context")

        val methodVar = classCtx.frame.vars[expr.name]
            ?: throw IllegalArgumentException("Actor '${classType.name}' has no method '${expr.name}'")
        val funcType = methodVar.type as? FuncType
            ?: throw IllegalArgumentException(
                "'${classType.name}.${expr.name}' is not a method (type ${methodVar.type.log()})"
            )

        val argTypes = expr.args.map { it.compile(ctx) }
        val expectedTypes = funcType.args
        if (expectedTypes != null) {
            if (expectedTypes.size != argTypes.size) {
                throw IllegalArgumentException(
                    "Actor method '${classType.name}.${expr.name}' expects ${expectedTypes.size} args, got ${argTypes.size}"
                )
            }
            expectedTypes.forEachIndexed { i, expected ->
                val actual = argTypes[i]
                if (!actual.sameAs(expected)) {
                    throw IllegalArgumentException(
                        "Actor method '${classType.name}.${expr.name}' arg #${i + 1}: " +
                            "expected ${expected.log()}, got ${actual.log()}"
                    )
                }
            }
        }

        ctx.add(ActorCall(methodVarIndex = methodVar.index, args = expr.args.size))
        return FutureType(funcType.derived)
    }
}
