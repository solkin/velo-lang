package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.PtrRecord

/**
 * Creates a pointer to an array element.
 * Stack: [array, index] -> [ptr]
 */
class PtrRefIndex : SimpleOperation {
    override fun exec(frame: Frame, ctx: VMContext) {
        val index = frame.subs.pop().getInt()
        val array = frame.subs.pop().getArray()
        val ptr = PtrRecord.Array(array, index)
        frame.subs.push(ptr)
    }
}
