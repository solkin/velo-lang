package compiler.nodes

import core.Op

import compiler.Context

data class DefNode(
    val name: String,
    val type: Type,
    val def: Node?,
) : Node() {
    override fun compile(ctx: Context): Type {
        val defType = def?.compile(ctx) ?: let {
            type.default(ctx)
            type
        }
        // A numeric initializer is coerced to the declared type: byte/int widen
        // to float (an int literal also fits a byte), so the variable's runtime
        // value matches its declared kind. Narrowing is rejected by coerceNumeric.
        val coerced = coerceNumeric(ctx, type, defType, (def as? IntNode)?.value, "'$name'")
        val staticType = if (coerced != null) {
            coerced
        } else {
            if (!type.sameAs(defType) && !type.sameAs(AnyType)) {
                if (type is StringType && defType !is StringType) {
                    throw IllegalArgumentException(
                        "Cannot assign ${defType.log()} to '$name' of type str. Convert it with .str() " +
                            "(e.g. str $name = value.str()) or use string interpolation."
                    )
                }
                throw IllegalArgumentException("Cannot assign ${defType.log()} to '$name' of type ${type.log()}")
            }
            // An interface-typed declaration keeps the interface as the variable's
            // static type (dispatch stays dynamic, only interface methods callable);
            // other declarations keep the more precise initializer type.
            if (type is InterfaceType) type else defType
        }
        val v = ctx.def(name, staticType)
        ctx.add(Op.Store(v.index))
        return VoidType
    }
}
