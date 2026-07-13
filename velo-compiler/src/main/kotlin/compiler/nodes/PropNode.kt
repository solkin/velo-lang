package compiler.nodes

import core.Op

import compiler.Context

data class PropNode(
    val name: String,
    val args: List<Node>?,
    val parent: Node
) : Node(), AssignableNode {
    override fun compile(ctx: Context): Type {
        return ctx.wrapScope { scopeCtx ->
            val parentType = parent.compile(scopeCtx)
            val ext = scopeCtx.opt(parentType.name() + "@" + name)
            // Parens rule (one obvious way): anything that computes — a method,
            // a conversion, an extension call — is written with `()`; only a
            // stored field (class field, tuple element) is accessed bare.
            val hasParens = args != null
            if (ext != null && !hasParens) {
                throw IllegalArgumentException("'$name' is a function; call it with parentheses: $name()")
            }
            if (ext == null) {
                when (classifyAccess(parentType, name, scopeCtx)) {
                    Access.COMPUTATION -> if (!hasParens) {
                        throw IllegalArgumentException("'$name' is a method or conversion; call it with parentheses: $name()")
                    }
                    Access.FIELD -> if (hasParens) {
                        throw IllegalArgumentException("'$name' is a field; access it without parentheses: $name")
                    }
                    Access.UNKNOWN -> {}
                }
            }
            if (ext != null) {
                val argTypes = listOf(parentType) + args.orEmpty().map { it.compile(scopeCtx) }
                // A free-standing extension is addressed by frame number so the
                // call also works from an actor thread; otherwise load its variable.
                val directNum = scopeCtx.directFuncNum(parentType.name() + "@" + name)
                if (directNum != null) scopeCtx.add(Op.Frame(num = directNum))
                else scopeCtx.add(Op.Load(ext.index))
                val funcType = ext.type as? FuncType
                    ?: throw IllegalArgumentException("Call on non-function type")
                var funcArgTypes = funcType.args ?: throw Exception("Extension arguments is not defined")
                if (funcArgTypes.size != argTypes.size) {
                    throw Exception("Call args count ${argTypes.size} is differ from required ${funcArgTypes.size}")
                }
                // A generic extension (`ext[T](array[T]) ...`) infers its type
                // parameters from the actual receiver + argument types, then
                // resolves the parameter and return types before checking — the
                // same inference a generic free function or method uses.
                var resultType = funcType.derived
                if (funcType.typeParams.isNotEmpty()) {
                    val bindings = inferTypeBindings(funcType.typeParams, funcArgTypes, argTypes)
                    funcArgTypes = funcArgTypes.map { resolveGenericType(it, funcType.typeParams, bindings) }
                    resultType = resolveGenericType(resultType, funcType.typeParams, bindings)
                }
                funcArgTypes.forEachIndexed { i, def ->
                    val argType = argTypes[i]
                    if (!assignableArg(def, argType)) {
                        throw Exception("Argument \"${argType.log()}\" is differ from required type ${def.log()}")
                    }
                }
                scopeCtx.add(Op.Call(argTypes.size))
                resultType
            } else {
                val argTypes = args.orEmpty().reversed().map { it.compile(scopeCtx) }
                // Try type property, else common any-type property, else throw exception
                val prop = parentType.prop(name) ?: AnyType.prop(name)
                    ?: throw IllegalArgumentException(propHint(parentType, name))
                prop.compile(parentType, args = argTypes, scopeCtx)
            }
        }
    }

    private fun propHint(parentType: Type, name: String): String {
        val base = "Property '$name' of ${parentType.log()} is not supported"
        // A pointer's only members are the dereference (`*p` / `p.val()`); reaching
        // for `.str()` etc. on a pointer usually means a `dict.get()` result wasn't
        // dereferenced — dict/array indexing (`d[k]`) returns the value directly.
        if (parentType is PtrType) {
            return "$base. A pointer has no properties — dereference it first with " +
                "*p or p.val(), or index the collection directly (d[key], a[i])."
        }
        return base
    }

    private enum class Access { FIELD, COMPUTATION, UNKNOWN }

    /**
     * Whether `name` on [parentType] is a bare-accessed field or a parenthesised
     * computation. Tuple elements and a class's non-function members are fields;
     * methods and every built-in conversion compute. UNKNOWN (e.g. a member that
     * isn't found) defers to the normal resolution error.
     */
    private fun classifyAccess(parentType: Type, name: String, ctx: Context): Access = when (parentType) {
        is TupleType -> if (name.toIntOrNull() != null) Access.FIELD else Access.COMPUTATION
        is ClassType -> {
            val def = ctx.opt(parentType.name)?.type as? ClassType
            val member = def?.parent?.frame?.vars?.get(name)
            when {
                member == null -> Access.UNKNOWN
                member.type is FuncType -> Access.COMPUTATION
                else -> Access.FIELD
            }
        }
        else -> Access.COMPUTATION
    }

    override fun compileAssignment(type: Type, ctx: Context) {
        val parentType = parent.compile(ctx)
        val argsType = args.orEmpty().reversed().map { it.compile(ctx) }
        val prop = parentType.prop(name)
            ?: throw IllegalArgumentException("Property '$name' of ${parentType.log()} is not supported")
        if (prop !is AssignableProp) {
            throw IllegalArgumentException("Cannot assign to non-assignable prop '$name' of type $prop")
        }
        prop.compileAssignment(parentType, type, argsType, ctx)
    }
}
