package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Print : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val value = subs.pop().get()
        print(value.toString())
    }

}