package org.velo.android.ui

import com.google.android.material.R as MaterialR
import vm.RunStats

/** Which logical stream a chunk of terminal text came from — drives its colour. */
enum class OutputKind(val colorAttr: Int) {
    OUT(MaterialR.attr.colorOnSurface),
    ERR(MaterialR.attr.colorError),
    INPUT(MaterialR.attr.colorPrimary),
    SYSTEM(MaterialR.attr.colorOnSurfaceVariant),
}

/** A coloured run of terminal text. */
data class OutputSegment(val kind: OutputKind, val text: String)

/** The lifecycle of one program run, mirrored into the status row. */
sealed interface RunStatus {
    data object Running : RunStatus
    data class Finished(val stats: RunStats?) : RunStatus
    data object Stopped : RunStatus
    data class Failed(val reason: String) : RunStatus
}
