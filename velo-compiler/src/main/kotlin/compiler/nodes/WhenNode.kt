package compiler.nodes

import compiler.Context
import core.Op
import java.util.concurrent.atomic.AtomicInteger

/** One `when` pattern. */
sealed interface Pattern

/** A value pattern (`0`, `"Sun"`, `true`): the arm runs when the subject `==` [value]. */
data class LiteralPattern(val value: Node) : Pattern

/**
 * A variant pattern (`Add(l, r)` or bare `None`): the arm runs when the subject
 * is that enum variant. [bindings] name locals bound to the variant's fields, in
 * declaration order; an empty list matches the variant without binding.
 */
data class VariantPattern(val variantName: String, val bindings: List<String>) : Pattern

data class WhenArm(val pattern: Pattern, val body: Node)

/**
 * `when subject { pattern -> body ... else -> body }` — the single pattern-match
 * / switch expression. It evaluates the subject once, then runs the first arm
 * whose pattern matches, yielding that arm's value (like `if`, arm blocks return
 * their last expression). Two subject kinds:
 *
 *  - an **enum**: variant patterns dispatch on the runtime class discriminant
 *    ([Op.ClassId]) and may bind the variant's fields; the compiler checks the
 *    arms are exhaustive (every variant covered, or an `else`).
 *  - a **primitive** (int/byte/long/float/str/bool): literal patterns compare
 *    with `==`; an `else` arm is required (the value domain is open).
 *
 * Lowering mirrors [IfNode] — each arm is a two-way branch (`If`/`Move` over
 * inline blocks), chained like `if / else if / … / else`, so no new control
 * machinery is needed beyond the discriminant read.
 */
data class WhenNode(
    val subject: Node,
    val arms: List<WhenArm>,
    val elseBody: Node?,
) : Node() {
    override fun compile(ctx: Context): Type {
        val subjectType = subject.compile(ctx)
        val subjVar = ctx.def("\$when\$${counter.getAndIncrement()}", subjectType)
        ctx.add(Op.Store(subjVar.index))

        return when {
            subjectType is EnumType -> compileEnum(ctx, subjectType, subjVar.index)
            isPrimitive(subjectType) -> compilePrimitive(ctx, subjectType, subjVar.index)
            else -> throw IllegalStateException(
                "`when` subject must be an enum or a primitive (int/byte/long/float/str/bool), got '${subjectType.log()}'"
            )
        }
    }

    // ---- enum subject: variant patterns + exhaustiveness ----

    private fun compileEnum(ctx: Context, enumType: EnumType, subjSlot: Int): Type {
        val variantArms = arms.map {
            val p = it.pattern as? VariantPattern
                ?: throw IllegalStateException("`when` over enum '${enumType.enumName}' takes variant patterns, not literals")
            if (p.variantName !in enumType.variants) {
                throw IllegalStateException("'${p.variantName}' is not a variant of enum '${enumType.enumName}'")
            }
            p to it.body
        }
        val covered = variantArms.map { it.first.variantName }
        covered.groupingBy { it }.eachCount().forEach { (name, n) ->
            if (n > 1) throw IllegalStateException("Duplicate arm for variant '$name' in `when`")
        }
        if (elseBody == null) {
            val missing = enumType.variants.filter { it !in covered }
            if (missing.isNotEmpty()) {
                throw IllegalStateException(
                    "`when` over enum '${enumType.enumName}' is not exhaustive; add arms for ${missing.joinToString(", ")} or an `else`"
                )
            }
        }

        // With no `else`, the last variant arm becomes the unconditional base
        // (reached by elimination), so every path yields a value and no
        // redundant final test is emitted.
        val tested: List<Pair<VariantPattern, Node>>
        val base: (Context) -> Type
        if (elseBody != null) {
            tested = variantArms
            base = { c -> elseBody.compile(c) }
        } else {
            tested = variantArms.dropLast(1)
            val (lastPat, lastBody) = variantArms.last()
            base = { c -> bindVariant(c, enumType, lastPat, subjSlot); lastBody.compile(c) }
        }

        return emitChain(ctx, 0, tested.size, base,
            test = { c, i -> emitVariantTest(c, enumType, tested[i].first, subjSlot) },
            body = { c, i -> bindVariant(c, enumType, tested[i].first, subjSlot); tested[i].second.compile(c) })
    }

    private fun emitVariantTest(ctx: Context, enumType: EnumType, pat: VariantPattern, subjSlot: Int) {
        val variant = ctx.get(pat.variantName).type as? ClassType
            ?: throw IllegalStateException("Variant '${pat.variantName}' of enum '${enumType.enumName}' is not in scope")
        val frameNum = variant.num
            ?: throw IllegalStateException("Variant '${pat.variantName}' has no frame number")
        ctx.add(Op.Load(subjSlot))
        ctx.add(Op.ClassId)
        ctx.add(Op.Push(frameNum))
        ctx.add(Op.Equals)
    }

    /** Bind a variant's fields to the pattern's locals: `local_k = subject.field_k`. */
    private fun bindVariant(ctx: Context, enumType: EnumType, pat: VariantPattern, subjSlot: Int) {
        if (pat.bindings.isEmpty()) return
        val fields = enumType.variantFields[pat.variantName] ?: emptyList()
        if (pat.bindings.size != fields.size) {
            throw IllegalStateException(
                "Variant '${pat.variantName}' has ${fields.size} field(s) but the pattern binds ${pat.bindings.size}"
            )
        }
        val variant = ctx.get(pat.variantName).type as ClassType
        pat.bindings.forEachIndexed { k, bindName ->
            val field = fields[k]
            val local = ctx.def(bindName, field.type)
            ctx.add(Op.Load(subjSlot))
            variant.prop(field.name).compile(variant, emptyList(), ctx)
            ctx.add(Op.Store(local.index))
        }
    }

    // ---- primitive subject: literal patterns, `else` required ----

    private fun compilePrimitive(ctx: Context, subjType: Type, subjSlot: Int): Type {
        val litArms = arms.map {
            val p = it.pattern as? LiteralPattern
                ?: throw IllegalStateException("`when` over a primitive takes literal patterns, not variants")
            p to it.body
        }
        // `bool` has a closed domain: covering both `true` and `false` is
        // exhaustive, so no `else` is required (the last arm becomes the base).
        val boolExhaustive = subjType == BoolType && elseBody == null &&
            litArms.mapNotNull { (it.first.value as? BoolNode)?.value }.toSet().containsAll(setOf(true, false))

        val tested: List<Pair<LiteralPattern, Node>>
        val base: (Context) -> Type
        when {
            elseBody != null -> { tested = litArms; base = { c -> elseBody.compile(c) } }
            boolExhaustive -> { tested = litArms.dropLast(1); val last = litArms.last(); base = { c -> last.second.compile(c) } }
            subjType == BoolType -> throw IllegalStateException("`when` over bool must cover both `true` and `false`, or add an `else`")
            else -> throw IllegalStateException("`when` over a primitive needs an `else` arm (the value domain is open)")
        }

        return emitChain(ctx, 0, tested.size, base,
            test = { c, i -> c.add(Op.Load(subjSlot)); tested[i].first.value.compile(c); c.add(Op.Equals) },
            body = { c, i -> tested[i].second.compile(c) })
    }

    // ---- shared lowering: a chain of two-way branches, like nested `if` ----

    private fun emitChain(
        ctx: Context,
        i: Int,
        n: Int,
        base: (Context) -> Type,
        test: (Context, Int) -> Unit,
        body: (Context, Int) -> Type,
    ): Type {
        if (i >= n) {
            // The base runs in its own block so a no-`else` base (a variant arm,
            // reached by elimination) or an `else` body that binds/declares locals
            // does not leak them into the enclosing frame — matters when the arm
            // chain is empty (a single-variant enum) and the base is the top ctx.
            val baseCtx = ctx.block()
            val baseType = base(baseCtx)
            ctx.merge(baseCtx)
            return baseType
        }
        test(ctx, i)
        val thenCtx = ctx.block()
        val thenType = body(thenCtx, i)
        val elseCtx = ctx.block()
        val elseType = emitChain(elseCtx, i + 1, n, base, test, body)
        ctx.add(Op.If(elseSkip = thenCtx.size() + 1))
        ctx.merge(thenCtx)
        ctx.add(Op.Move(count = elseCtx.size()))
        ctx.merge(elseCtx)
        return if (thenType.sameAs(elseType)) thenType else AnyType
    }

    // Every primitive that supports `==` is matchable: a primitive arm is just
    // `subject == literal`, the same comparison `if` already allows (`float`
    // included — exact-equality precision is the caller's concern, as anywhere).
    private fun isPrimitive(t: Type): Boolean =
        t == IntType || t == ByteType || t == LongType || t == FloatType || t == StringType || t == BoolType

    companion object {
        private val counter = AtomicInteger(0)
    }
}
