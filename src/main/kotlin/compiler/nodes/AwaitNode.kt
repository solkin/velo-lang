package compiler.nodes

import compiler.Context
import vm.operations.FutureAwait

/**
 * `await futureExpr` — block until a [FutureType] value resolves, yielding
 * the underlying [FutureType.derived] type.
 *
 * `await` does only one thing: unwrap a future. Cross-actor calls are
 * started by [AsyncNode] (which produces the future); the canonical
 * synchronous form is `await async receiver.method(args)`, where the inner
 * `async` produces a `future[T]` and the outer `await` immediately drains
 * it. There is no special "synchronous call" syntax — keeping `await` and
 * `async` as orthogonal operations means the language has exactly two
 * cross-actor primitives, each with one job.
 *
 * Compile-time enforcement: the inner expression's type must be a
 * [FutureType]. Any other type is a clear user error (e.g., forgetting the
 * `async` prefix on a method call) and is rejected here.
 */
data class AwaitNode(val expr: Node) : Node() {

    override fun compile(ctx: Context): Type {
        val type = expr.compile(ctx)
        val futureType = type as? FutureType
            ?: throw IllegalArgumentException(buildAwaitTypeError(type))
        ctx.add(FutureAwait())
        return futureType.derived
    }

    /**
     * The hint about a missing `async` is only useful when the user clearly
     * wrote a method call (`await x.foo(...)`); for arbitrary expressions
     * such as `await 5` it would be misleading, so it's added conditionally.
     */
    private fun buildAwaitTypeError(actual: Type): String {
        val base = "'await' requires a future[T] value, but got ${actual.log()}"
        val isMethodCall = expr is PropNode && expr.args != null
        return if (isMethodCall) {
            "$base; did you forget `async` before the actor method call?"
        } else {
            base
        }
    }
}
