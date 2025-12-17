package compiler.nodes

import compiler.Context
import vm.operations.Load
import vm.operations.Store

/**
 * Apply-block node that executes a body in the context of a target expression.
 * Similar to Kotlin's `.apply{}` but with simpler `{}` syntax.
 *
 * Example:
 * ```
 * Person p = new Person("", 0) {
 *     it.setName("John");
 *     it.setAge(25);
 * };
 * ```
 *
 * The variable `it` inside the block refers to the result of the target expression.
 * The apply-block returns the value of `it` (the target).
 */
data class ApplyNode(
    val target: Node,  // Expression before {}
    val body: Node,    // Body inside {}
) : Node() {
    override fun compile(ctx: Context): Type {
        return ctx.wrapScope { scopeCtx ->
            // 1. Compile target expression, result goes on stack
            val targetType = target.compile(scopeCtx)

            // 2. Store result in "it" variable within this scope
            val itVar = scopeCtx.def(IT_VARIABLE, targetType)
            scopeCtx.add(Store(itVar.index))

            // 3. Compile body (which can use "it")
            body.compile(scopeCtx)

            // 4. Load "it" back onto stack as the return value
            // (apply-block always returns the target, not the last expression in body)
            scopeCtx.add(Load(itVar.index))

            targetType
        }
    }
}

const val IT_VARIABLE = "it"
