package compiler.nodes

import compiler.Context

abstract class Node {
    open fun compile(ctx: Context): Type {
        throw NotImplementedError("Compile function for $this is not implemented")
    }
}
