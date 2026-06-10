package compiler.nodes

import compiler.Context

interface Prop {
    fun compile(type: Type, args: List<Type>, ctx: Context): Type
}

interface AssignableProp : Prop {
    fun compileAssignment(parentType: Type, assignType: Type, args: List<Type>, ctx: Context)
}
