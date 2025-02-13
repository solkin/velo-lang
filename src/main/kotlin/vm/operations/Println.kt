package vm.operations

import vm.Frame
import vm.SimpleOperation

class Println : SimpleOperation {

    override fun exec(frame: Frame) {
        val rec = frame.subs.pop()
        println(rec.get<Any>().toString())
    }

}