package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Drop: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        subs.pop()
    }

}