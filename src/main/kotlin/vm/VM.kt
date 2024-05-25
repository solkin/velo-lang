package vm

import java.util.*

class VM(
    size: Int,
    dataStack: Int,
    callStack: Int,
) {

    private val memory = IntArray(size)
    private var ip = 0
    private val pmOffset = 0
    private val psSize = size - dataStack - callStack
    private val dataStack = IntStack(memory, memory.size - callStack - dataStack, dataStack)
    private val callStack = IntStack(memory, memory.size - callStack, callStack)

    fun load(img: IntArray) {
        if (img.size > memory.size) {
            throw IllegalArgumentException("image size is larger than allocated memory")
        }
        img.copyInto(memory, pmOffset, 0, img.size)
        ip = 0
        dataStack.reset()
        callStack.reset()
    }

    fun run() {
        try {
            while (true) {
                val i = next()
                when (i) {
                    InstrPush -> dataStack.push(v = next())
                    InstrDup -> dataStack.push(v = dataStack.peek())
                    InstrDrop -> dataStack.pop()
                    InstrSwap -> {
                        val v1 = dataStack.pop()
                        val v2 = dataStack.pop()
                        dataStack.push(v1)
                        dataStack.push(v2)
                    }
                    InstrRot -> {
                        val v0 = dataStack.pop()
                        val v1 = dataStack.pop()
                        val v2 = dataStack.pop()
                        dataStack.push(v1)
                        dataStack.push(v0)
                        dataStack.push(v2)
                    }
                    InstrPick -> {
                        val n = dataStack.pop()
                        val v = dataStack.pick(n)
                        dataStack.push(v)
                    }
                    InstrPlus -> {
                        val v1 = dataStack.pop()
                        val v2 = dataStack.pop()
                        dataStack.push(v1 + v2)
                    }
                    InstrMinus -> {
                        val v1 = dataStack.pop()
                        val v2 = dataStack.pop()
                        dataStack.push(v2 - v1)
                    }
                    InstrMultiply -> {
                        val v1 = dataStack.pop()
                        val v2 = dataStack.pop()
                        dataStack.push(v1 * v2)
                    }
                    InstrDivide -> {
                        val v1 = dataStack.pop()
                        val v2 = dataStack.pop()
                        dataStack.push(v2 / v1)
                    }
                    InstrNegative -> {
                        val v = dataStack.pop()
                        dataStack.push(-v)
                    }
                    InstrAnd -> {
                        val v1 = dataStack.pop() != 0
                        val v2 = dataStack.pop() != 0
                        dataStack.push((v2 && v1).toInt())
                    }
                    InstrOr -> {
                        val v1 = dataStack.pop() != 0
                        val v2 = dataStack.pop() != 0
                        dataStack.push((v2 || v1).toInt())
                    }
                    InstrNot -> {
                        val v = dataStack.pop() != 0
                        dataStack.push((!v).toInt())
                    }
                    InstrEquals -> {
                        val v1 = dataStack.pop()
                        val v2 = dataStack.pop()
                        dataStack.push((v2 == v1).toInt())
                    }
                    InstrMore -> {
                        val v1 = dataStack.pop()
                        val v2 = dataStack.pop()
                        dataStack.push((v2 > v1).toInt())
                    }
                    InstrWriteInt -> print(dataStack.pop())
                    InstrWriteChar -> print(Char(dataStack.pop()))
                    InstrWriteStr -> {
                        val l = next()
                        for (j in 0 until l) {
                            print(Char(next()))
                        }
                    }
                    InstrReadChar -> {
                        val v = System.`in`.read()
                        dataStack.push(v)
                    }
                    InstrFlush -> System.out.flush()
                    InstrStore -> {
                        val addr = next()
                        if (addr in pmOffset until psSize) {
                            val v = dataStack.pop()
                            memory[addr] = v
                        } else {
                            throw IllegalStateException("out of memory")
                        }
                    }
                    InstrFetch -> {
                        val addr = next()
                        if (addr in pmOffset until psSize) {
                            val v = memory[addr]
                            dataStack.push(v)
                        } else {
                            throw IllegalStateException("out of memory")
                        }
                    }
                    InstrCopy -> {
                        val addr1 = next()
                        if (addr1 in pmOffset until psSize) {
                            val addr2 = next()
                            if (addr2 in pmOffset until psSize) {
                                memory[addr2] = memory[addr1]
                                break
                            }
                        }
                        throw IllegalStateException("out of memory")
                    }
                    InstrCall -> {
                        val addr = dataStack.pop()
                        callStack.push(ip)
                        ip = addr
                    }
                    InstrCallIf -> {
                        val addr = dataStack.pop()
                        val cond = dataStack.pop()
                        if (cond != 0) {
                            callStack.push(ip)
                            ip = addr
                        }
                    }
                    InstrReturn -> {
                        val addr = callStack.pop()
                        ip = addr
                    }
                    InstrGoto -> {
                        val addr = next()
                        ip = addr
                    }
                    InstrGotoIf -> {
                        val addr = dataStack.pop()
                        val cond = dataStack.pop()
                        if (cond != 0) {
                            ip = addr
                        }
                    }
                    InstrEnd -> {
                        print("\n\nvm gracefully stopped\n")
                        return
                    }
                    else -> throw IllegalStateException("invalid instruction $i")
                }
            }
        } catch (ex: Throwable) {
            ex.printStackTrace()
        }
    }

    fun next(): Int {
        if (ip >= memory.size) {
            throw IllegalStateException("out of memory")
        }
        return memory[ip++]
    }

    private fun Boolean.toInt() = if (this) 1 else 0

}

const val InstrPush: Int = 1

const val InstrDup: Int = 2
const val InstrDrop: Int = 3
const val InstrSwap: Int = 4
const val InstrRot: Int = 5
const val InstrPick: Int = 6

const val InstrPlus: Int = 7
const val InstrMinus: Int = 8
const val InstrMultiply: Int = 9
const val InstrDivide: Int = 10
const val InstrNegative: Int = 11
const val InstrAnd: Int = 12
const val InstrOr: Int = 13
const val InstrNot: Int = 14

const val InstrMore: Int = 15
const val InstrEquals: Int = 16

const val InstrReadChar: Int = 17
const val InstrWriteChar: Int = 18
const val InstrWriteInt: Int = 19
const val InstrWriteStr: Int = 20
const val InstrFlush: Int = 21

const val InstrStore: Int = 22
const val InstrFetch: Int = 23
const val InstrCopy: Int = 24

const val InstrCall: Int = 25
const val InstrCallIf: Int = 26
const val InstrReturn: Int = 27

const val InstrGoto: Int = 28
const val InstrGotoIf: Int = 29

const val InstrEnd: Int = 30