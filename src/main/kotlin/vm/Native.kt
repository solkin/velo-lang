package vm

import vm.records.NativeRecord
import java.util.concurrent.atomic.AtomicInteger

interface Native {
    fun put(value: Any): NativeRecord
    fun <T> get(link: NativeRecord): T
}

/**
 * Default implementation of Native area.
 * Each VM instance should have its own NativeImpl.
 */
class NativeImpl : Native {

    private val enumerator = AtomicInteger()
    private val area = HashMap<Int, Any>()

    override fun put(value: Any): NativeRecord {
        val id = enumerator.getAndIncrement()
        area[id] = value
        return NativeRecord(id)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(link: NativeRecord): T {
        return area[link.id] as T ?: throw Exception("Broken native area link: $link")
    }

}
