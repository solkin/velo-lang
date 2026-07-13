package compiler.nodes

import core.Op

import compiler.Context

data class FuncNode(
    val name: String?,
    val typeParams: List<String> = emptyList(),
    val defs: List<DefNode>,
    val type: Type,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val argTypes = ArrayList<Type>()
        var resultType: Type = FuncType(derived = type, argTypes, typeParams = typeParams)

        // Define var before body frame creation (because var counter will be forked) if name is defined.
        val named = !name.isNullOrEmpty()
        val nameVar = if (named) ctx.def(name.orEmpty(), resultType) else null

        // Create body frame and fork var counter
        val funcOps = ctx.extend()
        // Record this name's function identity (its body frame) on the symbol, so
        // a direct call — including a recursive self-call, resolved here before the
        // variable is even stored — is addressed by frame number. If the body turns
        // out to capture an enclosing variable, this is cleared below and callers
        // fall back to the variable path. See Var.funcFrameNum.
        nameVar?.funcFrameNum = funcOps.frame.num
        // Mark the frame so an explicit `return` in the body (or in an inline
        // branch/loop within it) can type-check against this return type.
        funcOps.returnType = type

        // Insert function frame pointer into stack
        ctx.add(Op.Frame(num = funcOps.frame.num))
        // Define var if named variable defined
        nameVar?.let {
            ctx.add(Op.Store(index = nameVar.index))
            resultType = VoidType
        }

        // Compile body
        val args = defs.reversed().map { def ->
            val v = funcOps.def(def.name, def.type)
            funcOps.add(Op.Store(v.index))
            v
        }.reversed()
        argTypes += args.map { it.type }
        // Widen narrower int/byte arguments to float/long params on entry (same
        // prologue normalization a class constructor uses).
        normalizeNumericParams(funcOps, args)
        body.compile(funcOps)
        // Returns are explicit (each `return` is type-checked against `type` by
        // ReturnNode). A non-void function must therefore return on every path —
        // there is no implicit "last expression is the result". `Self` returns
        // are resolved at the call site, so the path check still applies.
        if (type !is VoidType && !alwaysReturns(body)) {
            throw IllegalStateException(
                "Function ${name ?: "<anonymous>"} must return ${type.log()} on every path (add an explicit 'return')"
            )
        }
        funcOps.add(Op.Ret)

        // Capture analysis: a function that reads or writes an *enclosing* slot
        // (index below its own frame base) is a closure — it depends on the scope
        // live where it was defined, so it must be reached through its variable
        // (which carries that captured scope). A function that touches only its
        // own slots (args/locals) and calls other frame-addressed functions is
        // free-standing: it can be addressed by frame number and run from any
        // thread, including an actor's. Only the latter keeps `frameNum` set.
        val varBase = funcOps.frame.varBase
        val capturesEnclosing = funcOps.operations().any { op ->
            when (op) {
                is Op.Load -> op.index < varBase
                is Op.Store -> op.index < varBase
                is Op.PtrRef -> op.varIndex < varBase
                else -> false
            }
        }
        if (capturesEnclosing) nameVar?.funcFrameNum = null

        // Add function operations to the real context
        ctx.merge(funcOps)

        return resultType
    }
}

/**
 * Conservative control-flow check: does every path through [node] hit a
 * `return`? A bare expression, a loop (may run zero times), and a `let`/scope
 * closure are not terminal; an `if` counts only when both branches return.
 */
private fun alwaysReturns(node: Node): Boolean = when (node) {
    is ReturnNode -> true
    is ProgramNode -> node.prog.isNotEmpty() && alwaysReturns(node.prog.last())
    is IfNode -> node.elseNode != null && alwaysReturns(node.thenNode) && alwaysReturns(node.elseNode)
    else -> false
}

data class FuncType(
    val derived: Type,
    override val args: List<Type>? = null,
    val typeParams: List<String> = emptyList(),
) : Callable {
    /**
     * Return types must agree, unless the expected return is `any` — the fully
     * loose form a raw host `VeloFunction` maps to, which accepts a callback of
     * any return type. Argument lists are compared only when both sides declare
     * them: the loose form `func[T]` (args unknown) stays assignment-compatible
     * with any function returning `T`, preserving the untyped higher-order
     * style. The full form `func[(T1, T2) T]` is strict — that's what actor
     * signatures and native callbacks rely on.
     */
    override fun sameAs(type: Type): Boolean {
        if (type !is FuncType) return false
        if (derived !is AnyType && !type.derived.sameAs(derived)) return false
        val expected = args ?: return true
        val actual = type.args ?: return true
        return expected.size == actual.size &&
            expected.zip(actual).all { (a, b) -> a.sameAs(b) }
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Frame(num = 0))
    }

    override fun prop(name: String): Prop? = null

    override fun log() = args
        ?.let { "func[(${it.joinToString(", ") { a -> a.log() }}) ${derived.log()}]" }
        ?: "func[${derived.log()}]"

    override fun vmType() = core.VmType.Func(
        args = args?.map { it.vmType() },
        ret = derived.vmType(),
    )

    override fun name() = "func"
}
