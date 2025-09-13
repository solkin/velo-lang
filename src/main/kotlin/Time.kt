class Time {
    fun sleep(millis: Int) {
        Thread.sleep(millis.toLong())
    }
    fun unix(): Int {
        return (System.currentTimeMillis() / 1000).toInt()
    }
    fun print(term: Terminal) {
        term.print(unix().toString())
    }
}