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
    BaseType.ARRAY to mapOf(
        "sub" to SubArrayProp,
        "len" to ArrayLenProp,
        "con" to ArrayConProp,
        "plus" to ArrayPlusProp,
        "map" to MapArrayProp,
    )
)

interface Prop {
    fun compile(type: Type, args: List<Type>, ctx: Context): Type
}