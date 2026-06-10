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

        // Define var before body frame creation (because var counter will be forked) if name is defined
        val named = !name.isNullOrEmpty()
        val nameVar = if (named) ctx.def(name.orEmpty(), resultType) else null

        // Create body frame and fork var counter
        val funcOps = ctx.extend()

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
        val retType = body.compile(funcOps)
        if (!retType.sameAs(type)) {
            throw IllegalStateException("Function $name return type $retType is not the same as defined $type")
        }
        funcOps.add(Op.Ret)

        // Add function operations to the real context
        ctx.merge(funcOps)

        return resultType
    }
}

data class FuncType(
    val derived: Type,
    override val args: List<Type>? = null,
    val typeParams: List<String> = emptyList(),
) : Callable {
    /**
     * Return types must always agree. Argument lists are compared only when
     * both sides declare them: the loose form `func[T]` (args unknown) stays
     * assignment-compatible with any function returning `T`, preserving the
     * untyped higher-order style. The full form `func[(T1, T2) T]` is strict —
     * that's what actor signatures and native callbacks rely on.
     */
    override fun sameAs(type: Type): Boolean {
        if (type !is FuncType || !type.derived.sameAs(derived)) return false
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
