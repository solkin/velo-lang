import java.util.Scanner

class Terminal() {
    private val scanner = Scanner(System.`in`)

    fun print(text: String) {
        kotlin.io.print(text)
    }
    fun println(text: String) {
        kotlin.io.println(text)
    }
    fun input(): String {
        return scanner.nextLine()
    }
}
