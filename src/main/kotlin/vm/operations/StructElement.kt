package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class StructElement : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val index = subs.pop().getInt()
        val struct = subs.pop().getStruct()

        subs.push(struct[index])
    }

}
