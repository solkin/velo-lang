package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Index : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val index = subs.pop().getInt()
        val array = subs.pop().getArray()

        val rec = array[index]

        subs.push(rec)
    }

}