package vm.operations

import vm.Frame
import vm.SimpleOperation

class DictSet : SimpleOperation {

    override fun exec(frame: Frame) {
        val key = frame.subs.pop()
        val value = frame.subs.pop()
        val dict = frame.subs.pop().getDict()

        dict[key] = value
    }

}