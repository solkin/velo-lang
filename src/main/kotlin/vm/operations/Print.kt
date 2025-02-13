package vm.operations

import vm.Frame
import vm.SimpleOperation

class Print : SimpleOperation {

    override fun exec(frame: Frame) {
        val rec = frame.subs.pop()
        print(rec.get<Any>().toString())
    }

}