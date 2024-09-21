package compiler.nodes

import compiler.Environment

data class StructNode(
    val nodes: List<DefNode>,
) : Node() {

    private val value = ArrayList<Value<*>>()

    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        value.clear()
        val map = HashMap<String, Value<*>>()
        nodes.forEach { v ->
            map[v.name] = v.def?.let { v.def.evaluate(env) } ?: BoolValue(false)
        }
        return StructValue(map)
    }
}

class StructValue(val value: Map<String, Value<*>>) : Value<Map<String, Value<*>>>(value) {
    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return value[name] ?: when (name) {
            "len" -> IntValue(value.size)
            else -> super.property(name, args)
        }
    }
}
