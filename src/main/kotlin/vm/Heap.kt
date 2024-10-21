package vm

interface Heap {
    fun current(): Scope<Record>
    fun extend(): Scope<Record>
    fun free()
}
