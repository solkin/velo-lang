package vm.operations

import vm.VMContext

import vm.Frame
import vm.Record
import vm.SimpleOperation
import vm.records.RefRecord

class DictOf : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val size = frame.subs.pop().getInt()
        val dict = HashMap<Record, Record>(size)
        for(i in 0 until size) {
            val value = frame.subs.pop()
            val key = frame.subs.pop()
            dict[key] = value
        }
        val rec = RefRecord.dict(dict, ctx)
        frame.subs.push(rec)
    }

}
