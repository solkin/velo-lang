package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.PtrRecord

/**
 * Loads (dereferences) the value from a pointer.
 * Stack: [ptr] -> [value]
 */
class PtrLoad : SimpleOperation {
    override fun exec(frame: Frame) {
        val ptr = frame.subs.pop()
        if (ptr !is PtrRecord) {
            throw IllegalStateException("PtrLoad requires a pointer on stack, got: ${ptr.javaClass.simpleName}")
        }
        if (ptr.isNull()) {
            throw NullPointerException("Null pointer dereference")
        }
        frame.subs.push(ptr.deref())
    }
}

