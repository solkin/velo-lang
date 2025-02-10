package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Free : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        heap.free()
    }

}
