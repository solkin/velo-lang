package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.VarPtrRecord

/**
 * Creates a pointer (reference) to a variable by its index.
 * Stack: [] -> [ptr]
 */
class PtrRef(val varIndex: Int) : SimpleOperation {
    override fun exec(frame: Frame) {
        val ptr = VarPtrRecord(frame.vars, varIndex)
        frame.subs.push(ptr)
    }
}

