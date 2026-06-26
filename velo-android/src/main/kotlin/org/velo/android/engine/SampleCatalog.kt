package org.velo.android.engine

import android.content.res.AssetManager
import org.json.JSONObject
import java.io.IOException

/** A bundled demo, described by its `meta.json` and backed by a compiled `program.vbc`. */
data class SampleInfo(
    val id: String,
    val name: String,
    val description: String,
    val interactive: Boolean,
)

/**
 * The bundled samples, read from the app's `assets/samples` directory (filled at
 * build time by the `compileVeloSamples` Gradle task: each `samples/<id>/` holds
 * `meta.json` + a compiled `program.vbc`). The `.vel` sources never ship.
 */
class SampleCatalog(private val assets: AssetManager) {

    fun samples(): List<SampleInfo> =
        ids().mapNotNull { id -> readMeta(id) }

    /** The compiled bytecode for a sample, as raw bytes. */
    fun bytecode(id: String): ByteArray =
        assets.open("samples/$id/program.vbc").use { it.readBytes() }

    private fun ids(): List<String> =
        (assets.list("samples") ?: emptyArray()).sorted()

    private fun readMeta(id: String): SampleInfo? {
        val text = try {
            assets.open("samples/$id/meta.json").use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (e: IOException) {
            return null
        }
        val json = JSONObject(text)
        return SampleInfo(
            id = json.optString("id", id),
            name = json.optString("name", id),
            description = json.optString("description", ""),
            interactive = json.optBoolean("interactive", false),
        )
    }
}
