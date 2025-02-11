package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord
import java.util.Arrays

class SubArr : SimpleOperation {

    override fun exec(frame: Frame) {
        val start = frame.subs.pop().getInt()
        val end = frame.subs.pop().getInt()
        val array = frame.subs.pop().getArray()

        val rec = ValueRecord(Arrays.copyOfRange(array, start, end))

        frame.subs.push(rec)
    }

}