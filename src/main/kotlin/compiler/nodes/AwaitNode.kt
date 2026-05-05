package compiler.nodes

import compiler.Context
import vm.operations.ActorAwaitCall

/**
 * `await receiver.method(args)` — synchronous cross-actor method invocation.
 *
 * Why a dedicated AST node instead of letting [PropNode] handle it:
 *   - `actor[T]` deliberately exposes no props ([ActorBoundType.prop] returns
 *     `null`), so naked `receiver.method(...)` on an actor-bound value fails
 *     compilation. `await` is the only way to invoke methods across the
 *     actor boundary, which is exactly the rule we want to enforce.
 *   - Compilation here resolves the method against the *underlying* class
 *     definition (kept in [ClassType.parent]), reuses the same arity / type
 *     checks as ordinary method dispatch, and emits a single
 *     [ActorAwaitCall] op that does the marshalling at runtime.
 *
 * The wrapped [expr] must be a [PropNode] with non-null `args` (i.e., a
 * method call). Property reads on actors are intentionally not supported —
 * actors should expose their state through methods.
 */
data class AwaitNode(val expr: Node) : Node() {

    override fun compile(ctx: Context): Type {
        if (expr !is PropNode || expr.args == null) {
            throw IllegalArgumentException("'await' requires an actor method call: await receiver.method(args)")
        }

        val receiverType = expr.parent.compile(ctx)
        val actorType = receiverType as? ActorBoundType
            ?: throw IllegalArgumentException(
                "'await' requires an actor[T] receiver, but got ${receiverType.log()}"
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

        ctx.add(ActorAwaitCall(methodVarIndex = methodVar.index, args = expr.args.size))

        // Wrap class-typed returns as `actor[T]` — they live inside the actor
        // and must keep the same calling discipline.
        return when (val ret = funcType.derived) {
            is ClassType -> if (ret.isActor) ActorBoundType(ret) else ret
            else -> ret
        }
    }
}
