package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.BoxPtrRecord

/**
 * Creates a new pointer (box) containing the value from the top of the stack.
 * Stack: [value] -> [ptr]
 */
class PtrNew : SimpleOperation {
    override fun exec(frame: Frame) {
        val value = frame.subs.pop()
        val ptr = BoxPtrRecord(value)
        frame.subs.push(ptr)
    }
}

