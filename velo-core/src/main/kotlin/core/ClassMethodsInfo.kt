package core

/**
 * The method table for one class frame: every method the class declares, mapped
 * to the variable index it occupies in an instance's scope. Built at compile
 * time and carried in the program so the runtime can resolve a method **by
 * name** on a receiver whose concrete class is only known dynamically — the
 * mechanism behind [Op.MethodLoad] interface dispatch.
 *
 * A concrete `instance.method(...)` call needs none of this: it bakes the slot
 * in at compile time. Only an interface-typed receiver does, because different
 * classes satisfying the same interface lay their methods out at different
 * slots. Keying by [frameNum] (the class frame) lets a single op recover the
 * right slot for whatever class the receiver turns out to be.
 */
data class ClassMethodsInfo(
    val frameNum: Int,
    val methods: List<ClassMethod>,
)

data class ClassMethod(
    val name: String,
    val index: Int,
)
