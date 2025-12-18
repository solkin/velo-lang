package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.PtrRecord

/**
 * Creates a new pointer (box) containing the value from the top of the stack.
 * Stack: [value] -> [ptr]
 */
class PtrNew : SimpleOperation {
    override fun exec(frame: Frame, ctx: VMContext) {
        val value = frame.subs.pop()
        val ptr = PtrRecord.Box(value)
        frame.subs.push(ptr)
    }
}

