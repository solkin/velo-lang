package org.velo.android.engine

import java.io.File
import java.io.FileOutputStream

/**
 * Host implementation of the Velo `FileSystem` native (registered under the name
 * `FileSystem`). Mirrors the CLI's `FileSystem` surface exactly, but built on
 * `java.io.File` instead of `java.nio.file` so it works from minSdk 23 (the NIO
 * file API only exists on Android API 26+).
 *
 * Android storage is sandboxed: absolute paths outside the app's own directories
 * are typically not accessible, so demo programs should use app-local paths.
 */
class VeloFileSystem {

    fun read(path: String): String = File(path).readText()

    fun write(path: String, content: String) = File(path).writeText(content)

    fun append(path: String, content: String) = File(path).appendText(content)

    fun readBytes(path: String): ByteArray = File(path).readBytes()

    fun writeBytes(path: String, data: Array<Byte>) = File(path).writeBytes(data.toByteArray())

    fun appendBytes(path: String, data: Array<Byte>) {
        FileOutputStream(path, true).use { it.write(data.toByteArray()) }
    }

    fun exists(path: String): Boolean = File(path).exists()

    fun isFile(path: String): Boolean = File(path).isFile

    fun isDir(path: String): Boolean = File(path).isDirectory

    fun mkdir(path: String) {
        File(path).mkdirs()
    }

    fun delete(path: String) {
        val file = File(path)
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun size(path: String): Int {
        val len = File(path).length()
        return if (len > Int.MAX_VALUE) Int.MAX_VALUE else len.toInt()
    }

    fun list(path: String): Array<String> = File(path).list() ?: arrayOf()

    fun move(source: String, target: String) {
        val src = File(source)
        if (!src.renameTo(File(target))) {
            src.copyTo(File(target), overwrite = true)
            src.delete()
        }
    }

    fun copy(source: String, target: String) {
        File(source).copyTo(File(target), overwrite = true)
    }
}
