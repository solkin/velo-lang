package compiler

import compiler.nodes.Type
import core.Op
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.set

data class CompilerFrame(
    val num: Int,
    val ops: MutableList<Op>,
    val vars: MutableMap<String, Var>,
    val varCounter: AtomicInteger,
) {

    fun def(name: String, type: Type, immutable: Boolean = false): Var {
        if (vars.containsKey(name)) {
            throw IllegalArgumentException("Variable $name is already defined")
        }
        val v = Var(index = varCounter.getAndIncrement(), type = type, immutable = immutable)
        vars[name] = v
        return v
    }

    fun retype(name: String, type: Type): Var {
        val e = vars[name] ?: throw IllegalArgumentException("Variable $name is not defined")
        val v = Var(index = e.index, type = type, immutable = e.immutable)
        vars[name] = v
        return v
    }

}