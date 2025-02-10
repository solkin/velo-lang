package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Rot: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val rec1 = subs.pop()
        val rec2 = subs.pop()
        val rec3 = subs.pop()
        subs.push(rec2)
        subs.push(rec1)
        subs.push(rec3)
    }

}