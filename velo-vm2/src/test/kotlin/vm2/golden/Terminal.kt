package vm2.golden

import java.util.Scanner

/** Native `Terminal` for golden tests — writes to stdout so output can be captured. */
class Terminal {
    private val scanner = Scanner(System.`in`)

    fun print(text: String) = kotlin.io.print(text)
    fun println(text: String) = kotlin.io.println(text)
    fun input(): String = scanner.nextLine()
}
