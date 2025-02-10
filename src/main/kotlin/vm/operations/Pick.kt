package vm.operations

import vm.Frame
import vm.Heap
import vm.OperationNotSupportedException
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Pick: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        throw OperationNotSupportedException()
    }

}