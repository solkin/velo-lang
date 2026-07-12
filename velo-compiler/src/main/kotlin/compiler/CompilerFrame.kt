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
    /**
     * The first variable slot this frame owns — the [varCounter] value when the
     * frame was created. A frame's local slots span `[varBase, varCounter)`,
     * which is what sizes its runtime variable array. Counting from the counter
     * (not the name map) is essential: inline blocks (`if`/`while` bodies) draw
     * slots from this same counter but keep their names in their own maps, so
     * their slots would otherwise be invisible to serialization.
     */
    val varBase: Int = 0,
) {

    fun def(name: String, type: Type, immutable: Boolean = false, frameAddressable: Boolean = false): Var {
        if (vars.containsKey(name)) {
            throw IllegalArgumentException("Variable $name is already defined")
        }
        val v = Var(index = varCounter.getAndIncrement(), type = type, immutable = immutable, frameAddressable = frameAddressable)
        vars[name] = v
        return v
    }

    fun retype(name: String, type: Type): Var {
        val e = vars[name] ?: throw IllegalArgumentException("Variable $name is not defined")
        // A reassigned or refined binding is no longer a direct declaration: its
        // runtime value may differ from its static type, so drop frame-addressing.
        val v = Var(index = e.index, type = type, immutable = e.immutable)
        vars[name] = v
        return v
    }

}