package vm2

class ScopedHeap: Heap {

    private var scope = createGlobalScope<Record>()

    override fun current(): Scope<Record> = scope

    override fun extend(): Scope<Record> {
        val extScope = scope.extend()
        scope = extScope
        return extScope
    }

    override fun free() {
        scope = scope.parent ?: scope
    }

}