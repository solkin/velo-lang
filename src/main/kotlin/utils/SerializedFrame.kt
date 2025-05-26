package utils

import vm.Operation

data class SerializedFrame(
    val num: Int,
    val ops: MutableList<Operation>,
    val vars: List<Int>,
)