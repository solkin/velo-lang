package nodes

import Environment

data class StructNode(
    val nodes: List<DefNode>,
) : Node() {

    private val value = ArrayList<Type<*>>()

    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        value.clear()
        val map = HashMap<String, Type<*>>()
        nodes.forEach { v ->
            map[v.name] = v.def?.let { v.def.evaluate(env) } ?: BoolType(false)
        }
        return StructType(map)
    }
}

class StructType(val value: Map<String, Type<*>>) : Type<Map<String, Type<*>>>(value) {
    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return value[name] ?: when (name) {
            "len" -> IntType(value.size)
            else -> super.property(name, args)
        }
    }
}
