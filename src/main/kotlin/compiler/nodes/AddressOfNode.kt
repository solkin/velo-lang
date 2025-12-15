package compiler.nodes

import compiler.Context
import vm.operations.PtrRef
import vm.operations.PtrRefIndex

/**
 * AST Node for taking the address of a variable or array element: `&variable` or `&array[index]`
 */
data class AddressOfNode(
    val target: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        return when (target) {
            is VarNode -> {
                // Pointer to a variable
                val v = ctx.get(target.name)
                ctx.add(PtrRef(varIndex = v.index))
                PtrType(v.type)
            }
            is IndexNode -> {
                // Pointer to an array element
                val listType = target.list.compile(ctx)
                target.index.compile(ctx)
                
                if (listType !is ArrayType) {
                    throw IllegalArgumentException("Cannot take address of index on non-array type: ${listType.log()}")
                }
                
                ctx.add(PtrRefIndex())
                PtrType(listType.derived)
            }
            else -> throw IllegalArgumentException("Cannot take address of ${target.javaClass.simpleName}. Only variables and array elements are supported.")
        }
    }
}

