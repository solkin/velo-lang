package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.RefRecord
import vm.records.RefKind

class ArrStore : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val count = frame.subs.pop().getInt()
        val index = frame.subs.pop().getInt()
        val arrayRec = frame.subs.pop()
        val array = arrayRec.getArray()

        var i = index + count - 1
        repeat(count) {
            array[i--] = frame.subs.pop()
        }

        // Push back the same reference (array is mutated in place)
        frame.subs.push(value = arrayRec)
    }

}
