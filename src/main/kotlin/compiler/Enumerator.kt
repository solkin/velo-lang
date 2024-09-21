package compiler

import compiler.nodes.Type

class Enumerator {

    private var scope = createGlobalScope()

    fun get(name: String): Var = scope.get(name)

    fun def(name: String, type: Type): Var = scope.def(name, type)

    fun extend(): Scope {
        val extScope = scope.extend()
        scope = extScope
        return extScope
    }

    fun free() {
        scope = scope.parent ?: scope
    }

}