package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class PairFirst : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val pair = subs.pop().getPair()

        subs.push(pair.first)
    }

}
