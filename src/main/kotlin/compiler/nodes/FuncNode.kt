package compiler.nodes

import compiler.Context
import vm.operations.Frame
import vm.operations.Load
import vm.operations.NativeFunction
import vm.operations.NativeInvoke
import vm.operations.Ret
import vm.operations.Store

data class FuncNode(
    val name: String?,
    val native: Boolean,
    val defs: List<DefNode>,
    val type: Type,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val argTypes = ArrayList<Type>()
        var resultType: Type = FuncType(derived = type, argTypes)

        // Define var before body frame creation (because var counter will be forked) if name is defined
        val named = !name.isNullOrEmpty()
        val nameVar = if (named) ctx.def(name.orEmpty(), resultType) else null

        if (native) {
            name ?: throw IllegalArgumentException("Native function must have a name")
            // Get native instance variable
            val iv = ctx.get(name = NATIVE_INSTANCE)
            // Put native instance to the stack
            ctx.add(Load(index = iv.index))
            // Add operation to find native method and put it on the stack
            ctx.add(NativeFunction(name, argTypes = defs.map { it.type.vmType() }))
            val fv = ctx.def(name = NATIVE_FUNCTION_PREFIX + name, type = type)
            // Save native method instance to the class frame vars
            ctx.add(Store(index = fv.index))
        }

        // Create body frame and fork var counter
        val funcOps = ctx.extend()

        // Insert function frame pointer into stack
        ctx.add(Frame(num = funcOps.frame.num))
        // Define var if named variable defined
        nameVar?.let {
            ctx.add(Store(index = nameVar.index))
            resultType = VoidType
        }

        // Compile body
        val args = defs.reversed().map { def ->
            val v = funcOps.def(def.name, def.type)
            funcOps.add(Store(v.index))
            v
        }.reversed()
        argTypes += args.map { it.type }
        val retType = if (native) {
            name ?: throw IllegalArgumentException("Native function must have a name")

            // Get native method variable
            val fv = ctx.get(name = NATIVE_FUNCTION_PREFIX + name)
            // Put native method to the stack
            funcOps.add(Load(index = fv.index))

            // Get native instance variable
            val iv = ctx.get(name = NATIVE_INSTANCE)
            // Put native instance to the stack
            funcOps.add(Load(index = iv.index))

            // Invoke native method from the stack with specific arguments
            funcOps.add(NativeInvoke(args = args.map { Pair(it.index, it.type.vmType()) }))
            // Native method must return declared function type
            type
        } else {
            body.compile(funcOps)
        }
        if (!retType.sameAs(type)) {
            throw IllegalStateException("Function $name return type $retType is not the same as defined $type")
        }
        funcOps.add(Ret())

        // Add function operations to the real context
        ctx.merge(funcOps)

        return resultType
    }
}

data class FuncType(val derived: Type, override val args: List<Type>? = null) : Callable {
    override fun sameAs(type: Type): Boolean {
        return type is FuncType && type.derived.sameAs(derived)
    }

    override fun default(ctx: Context) {
        ctx.add(Frame(num = 0))
    }

    override fun prop(name: String): Prop? = null

    override fun log() = toString()

    override fun vmType() = vm.VmFunc()

    override fun name() = "func"
}

const val NATIVE_FUNCTION_PREFIX = "native@function_"
