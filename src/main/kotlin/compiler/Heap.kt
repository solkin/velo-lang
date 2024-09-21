package compiler

class Heap {

    private var scope = createGlobalScope()

    fun current(): Scope = scope

    fun extend(): Scope {
        val extScope = scope.extend()
        scope = extScope
        return extScope
    }

    fun free() {
        scope = scope.parent ?: scope
    }

}