package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.PtrRecord

/**
 * Stores a value through a pointer.
 * Stack: [value, ptr] -> []
 */
class PtrStore : SimpleOperation {
    override fun exec(frame: Frame, ctx: VMContext) {
        val ptr = frame.subs.pop()
        val value = frame.subs.pop()
        if (ptr !is PtrRecord) {
            throw IllegalStateException("PtrStore requires a pointer on stack, got: ${ptr.javaClass.simpleName}")
        }
        if (ptr.isNull()) {
            throw NullPointerException("Null pointer dereference on store")
        }
        ptr.assign(value)
    }
}

