package org.velo.android.engine.ui.view

import android.graphics.Color
import android.view.View
import com.google.android.material.R as M
import com.google.android.material.color.MaterialColors

/**
 * Velo color specs → ARGB ints, resolved against the live Material3 theme.
 *
 * A spec is either a hex string (`#RRGGBB` / `#AARRGGBB`) or a Material role token
 * (`"primary"`, `"surfaceVariant"`, `"onPrimaryContainer"`, …). The role tokens are the
 * single source of truth shared with the public `Colors` constant native — it just hands
 * the same strings back to programs, and they round-trip through here. Unknown tokens fall
 * back to the primary role rather than throwing, so a typo degrades gracefully on screen.
 */
internal fun resolveColor(view: View, spec: String): Int {
    if (spec.startsWith("#")) return Color.parseColor(spec)
    val attr = colorRoleAttr(spec) ?: M.attr.colorPrimary
    return MaterialColors.getColor(view, attr, 0)
}

private fun colorRoleAttr(token: String): Int? = when (token) {
    "primary" -> M.attr.colorPrimary
    "onPrimary" -> M.attr.colorOnPrimary
    "primaryContainer" -> M.attr.colorPrimaryContainer
    "onPrimaryContainer" -> M.attr.colorOnPrimaryContainer
    "secondary" -> M.attr.colorSecondary
    "onSecondary" -> M.attr.colorOnSecondary
    "secondaryContainer" -> M.attr.colorSecondaryContainer
    "onSecondaryContainer" -> M.attr.colorOnSecondaryContainer
    "tertiary" -> M.attr.colorTertiary
    "onTertiary" -> M.attr.colorOnTertiary
    "tertiaryContainer" -> M.attr.colorTertiaryContainer
    "onTertiaryContainer" -> M.attr.colorOnTertiaryContainer
    "error" -> M.attr.colorError
    "onError" -> M.attr.colorOnError
    "errorContainer" -> M.attr.colorErrorContainer
    "onErrorContainer" -> M.attr.colorOnErrorContainer
    "background" -> M.attr.colorSurface
    "onBackground" -> M.attr.colorOnSurface
    "surface" -> M.attr.colorSurface
    "onSurface" -> M.attr.colorOnSurface
    "surfaceVariant" -> M.attr.colorSurfaceVariant
    "onSurfaceVariant" -> M.attr.colorOnSurfaceVariant
    "surfaceContainer" -> M.attr.colorSurfaceContainer
    "outline" -> M.attr.colorOutline
    "outlineVariant" -> M.attr.colorOutlineVariant
    else -> null
}
