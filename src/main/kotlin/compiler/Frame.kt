package compiler

import compiler.nodes.Type
import vm.Operation
import java.util.concurrent.atomic.AtomicInteger

data class Frame(
    val num: Int,
    val ops: MutableList<Operation>,
    val vars: MutableMap<String, Var>,
    val varCounter: AtomicInteger,
) {

    fun def(name: String, type: Type): Var {
        if (vars.containsKey(name)) {
            throw IllegalArgumentException("Variable $name is already defined")
        }
        val v = Var(index = varCounter.getAndIncrement(), type = type)
        vars[name] = v
        return v
    }

}