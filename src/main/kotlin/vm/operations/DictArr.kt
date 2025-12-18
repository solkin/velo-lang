package vm.operations

import vm.VMContext

import vm.Frame
import vm.Record
import vm.SimpleOperation
import vm.records.RefRecord

class DictArr : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val dict = frame.subs.pop().getDict()

        // Each entry becomes a tuple [key, value] stored as RefRecord
        val array: Array<Record> = dict.toList().map { entry ->
            val tuple = arrayOf<Record>(entry.first, entry.second)
            RefRecord.array(tuple, ctx)
        }.toTypedArray()

        val rec = RefRecord.array(array, ctx)
        frame.subs.push(rec)
    }

}
