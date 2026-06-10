package compiler.nodes

import core.Op

import compiler.Context
import java.util.concurrent.atomic.AtomicInteger

/** The stdlib class (lang/map.vel) the dict syntax lowers onto. */
const val DICT_CLASS = "Map"

private val dictTempCounter = AtomicInteger(0)

/**
 * A dict literal: `new dict[K:V] {k1: v1, ...}`.
 *
 * Dict is pure syntax — there is no dict in the VM. The literal lowers
 * to the stdlib Map class, exactly as if the program had written
 * `Map[K, V]()` followed by a `put` per entry. Indexing, assignment and
 * the rest of the dict surface go through the same class: the dict type
 * itself parses as `ClassType(Map)` (see TypeParser).
 */
data class DictNode(
    val dictOf: Map<Node, Node>,
    val keyType: Type,
    val valType: Type,
) : Node() {
    override fun compile(ctx: Context): Type {
        val mapType = CallNode(
            func = VarNode(name = DICT_CLASS, typeArgs = listOf(keyType, valType)),
            args = emptyList(),
        ).compile(ctx)
        val tempName = "@dict${dictTempCounter.getAndIncrement()}"
        val temp = ctx.def(tempName, mapType)
        ctx.add(Op.Store(temp.index))
        dictOf.forEach { (k, v) ->
            try {
                PropNode(name = "put", args = listOf(k, v), parent = VarNode(tempName)).compile(ctx)
            } catch (e: Exception) {
                throw Exception("Dict entry \"$k\": ${e.message}")
            }
        }
        ctx.add(Op.Load(temp.index))
        return mapType
    }
}
