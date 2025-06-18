class Time {
    fun sleep(millis: Int) {
        Thread.sleep(millis.toLong())
    }
    fun unix(): Int {
        return (System.currentTimeMillis() / 1000).toInt()
    }
}