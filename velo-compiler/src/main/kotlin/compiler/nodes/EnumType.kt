package compiler.nodes

import compiler.Context
import core.VmType

/**
 * A closed sum type — a fixed, ordered set of [variants], each variant a
 * `data class` in its own right (its own frame, immutable value semantics,
 * structural equality/hash, cross-boundary transfer). Unlike a structural
 * [InterfaceType] (open: any value of matching shape satisfies it), an enum
 * *enumerates* its variants, which is exactly what lets `when` verify
 * exhaustiveness. A value of an enum type is always one of its variant
 * instances, discriminated at runtime by its class frame number
 * ([core.Op.ClassId]).
 *
 * The type carries no runtime representation of its own: the variants are
 * ordinary data-class frames, and the enum is erased to [VmType.Any] on the
 * wire. [variantFields] records each variant's declaration-order fields so a
 * `when` arm can bind a variant's payload to pattern variables.
 */
class EnumType(
    val enumName: String,
    val variants: MutableList<String> = mutableListOf(),
    val variantFields: MutableMap<String, List<DefNode>> = mutableMapOf(),
) : Type {
    override fun sameAs(type: Type): Boolean = type is EnumType && type.enumName == enumName

    /** Does a value of [type] belong to this enum — the enum itself, or one of its variants? */
    fun accepts(type: Type): Boolean =
        (type is EnumType && type.enumName == enumName) ||
            (type is ClassType && type.name in variants)

    override fun default(ctx: Context): Unit =
        throw IllegalStateException("Enum '$enumName' has no default value; assign one of its variants")

    override fun prop(name: String): Prop? = AnyType.prop(name)

    override fun log() = enumName

    override fun toString() = enumName

    override fun vmType() = VmType.Any

    override fun name() = enumName
}
