package vm.records

import vm.Record

data class FrameRecord(
    private val num: Int
) : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = num as T

}
