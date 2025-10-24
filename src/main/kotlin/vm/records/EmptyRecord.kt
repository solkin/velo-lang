package vm.records

import vm.Record

object EmptyRecord : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = Unit as T

}
