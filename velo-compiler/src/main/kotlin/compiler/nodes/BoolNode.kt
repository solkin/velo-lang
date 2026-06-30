package compiler.nodes

import core.Op

import compiler.Context

data class BoolNode(
    val value: Boolean,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Op.Push(value))
        return BoolType
    }
}

object BoolType : Type {
    override fun sameAs(type: Type): Boolean {
        return type is BoolType
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Push(value = false))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "str" -> BoolStrProp
            else -> null
        }
    }

    override fun log() = name()

    override fun vmType() = core.VmType.Bool

    override fun name() = "bool"
}

object BoolStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        // No bool->str opcode: pick the literal with the bool already on the
        // stack. true -> "true" (then skip the else), false -> "false".
        ctx.add(Op.If(elseSkip = 2))
        ctx.add(Op.Push(value = "true"))
        ctx.add(Op.Move(count = 1))
        ctx.add(Op.Push(value = "false"))
        return StringType
    }
}
