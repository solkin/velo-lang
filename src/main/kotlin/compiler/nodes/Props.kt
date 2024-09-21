package compiler.nodes

import CompilerContext

val propMap = mapOf(
    BaseType.STRING to mapOf(
        "sub" to SubStrProp,
        "len" to StrLenProp,
    ),
    BaseType.SLICE to mapOf(
        "sub" to SubSliceProp,
        "len" to SliceLenProp,
        "map" to MapSliceProp,
    )
)

interface Prop {
    fun compile(type: Type, args: List<Type>, ctx: CompilerContext): Type
}