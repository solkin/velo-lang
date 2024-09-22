package compiler.nodes

import compiler.Context

val propMap = mapOf(
    BaseType.INT to mapOf(
        "str" to IntStrProp,
    ),
    BaseType.STRING to mapOf(
        "sub" to SubStrProp,
        "len" to StrLenProp,
        "con" to StrConProp,
    ),
    BaseType.SLICE to mapOf(
        "sub" to SubSliceProp,
        "len" to SliceLenProp,
        "map" to MapSliceProp,
    )
)

interface Prop {
    fun compile(type: Type, args: List<Type>, ctx: Context): Type
}