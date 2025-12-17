package vm.records

import vm.Native
import vm.Record
import vm.VMContext

data class NativeRecord(
    val id: Int,
    @Transient private val native: Native? = null
) : Record {

    override fun <T> get(): T {
        val n = native ?: throw IllegalStateException("NativeRecord requires native context for get()")
        return n.get(this)
    }

    fun <T> get(ctx: VMContext): T = ctx.nativeGet(this)

    companion object {
        fun create(value: Any, ctx: VMContext): NativeRecord {
            val record = ctx.nativePut(value)
            return NativeRecord(record.id, ctx.nativeArea)
        }
    }
}
