package compiler.nodes

import compiler.Context

interface Prop {
    fun compile(type: Type, args: List<Type>, ctx: Context): Type
}
