package vm.operations

import vm.Frame
import vm.Record
import vm.SimpleOperation
import vm.records.LinkRecord

class DictOf : SimpleOperation {

    override fun exec(frame: Frame) {
        val size = frame.subs.pop().getInt()
        val dict = HashMap<Record, Record>(size)
        for(i in 0 until size) {
            val value = frame.subs.pop()
            val key = frame.subs.pop()
            dict[key] = value
        }
        val rec = LinkRecord.create(dict)
        frame.subs.push(rec)
    }

}