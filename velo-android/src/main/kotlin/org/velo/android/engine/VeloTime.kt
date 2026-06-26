package org.velo.android.engine

/**
 * Host implementation of the Velo `Time` native (registered under the name `Time`).
 * Same surface as the CLI's `Time`; `print` takes the `Terminal` native, which on
 * Android is [AndroidTerminal].
 */
class VeloTime {
    fun sleep(millis: Int) {
        Thread.sleep(millis.toLong())
    }

    fun unix(): Int {
        return (System.currentTimeMillis() / 1000).toInt()
    }

    fun print(term: AndroidTerminal) {
        term.print(unix().toString())
    }
}
