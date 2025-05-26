package vm.records

import vm.Record

class EmptyRecord : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = Unit as T

}
