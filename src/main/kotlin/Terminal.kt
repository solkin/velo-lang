import java.util.Scanner

class Terminal() {
    fun print(text: String) {
        kotlin.io.print(text)
    }
    fun input(): String {
        return Scanner(System.`in`).nextLine()
    }
}
