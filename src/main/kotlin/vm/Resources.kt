package vm

import utils.SerializedFrame

data class Resources(
    var frames: Map<Int, SerializedFrame> = emptyMap()
)