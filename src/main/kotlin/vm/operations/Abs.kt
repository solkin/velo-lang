package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord
import kotlin.math.abs

class Abs: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec = frame.subs.pop()

        val result = ValueRecord(abs(rec.getInt()))

        frame.subs.push(result)
    }

}