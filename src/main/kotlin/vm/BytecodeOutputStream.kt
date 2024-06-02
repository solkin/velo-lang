package vm

import java.io.IOException
import java.io.OutputStream
import java.util.*

class BytecodeOutputStream(
    private val out: OutputStream,
) : OutputStream() {

    private var buffs = Stack<Buffer>()
    private var written = 0

    fun subCreate() {
        val buffer = ByteArrayBuffer(offset = written + 8)
        buffs.push(buffer)
    }

    fun subReturn() {
        writeCommand(InstrReturn)
        val b = buffs.pop()
        writeGotoRel(b.offset()/4) // TODO: check

        // TODO: write sub return impl

    }

    fun writeGoto(addr: Int) {
        writeCommand(InstrGoto)
        writeInt(addr)
    }

    fun writeGotoRel(diff: Int) {
        writeCommand(InstrGoto)
        val addr = offset() + 1 + diff
        writeInt(addr)
    }

    fun writeCommand(c: Int) {
        writeInt(c)
    }

    private fun output() = buffs.peek()?.output() ?: out

    private fun offset() = buffs.peek()?.offset() ?: 0

    override fun write(b: Int) {
        output().write(b)
        this.incCount(1)
    }

    @Throws(IOException::class)
    private fun writeInt(v: Int) {
        write(v ushr 0)
        write(v ushr 8)
        write(v ushr 16)
        write(v ushr 24)
    }

    private fun incCount(value: Int) {
        var temp: Int = this.written + value
        if (temp < 0) {
            temp = Int.MAX_VALUE
        }
        this.written = temp
    }

}