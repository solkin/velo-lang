package vm.operations

import vm.Frame
import vm.HaltException
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Halt: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        throw HaltException()
    }

}