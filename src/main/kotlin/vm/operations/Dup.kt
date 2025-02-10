package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Dup: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val rec = subs.peek()
        subs.push(rec)
    }

}