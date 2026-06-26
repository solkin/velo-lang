package org.velo.android.engine.ui.view

import com.google.android.material.R as M

/**
 * Map a Velo text-style token to a Material3 `TextAppearance` style resource. The tokens are
 * the single source of truth shared with the public `TextStyles` constant native; an unknown
 * token falls back to Body Large.
 */
internal fun textAppearanceRes(token: String): Int = when (token) {
    "displayLarge" -> M.style.TextAppearance_Material3_DisplayLarge
    "displayMedium" -> M.style.TextAppearance_Material3_DisplayMedium
    "displaySmall" -> M.style.TextAppearance_Material3_DisplaySmall
    "headlineLarge" -> M.style.TextAppearance_Material3_HeadlineLarge
    "headlineMedium" -> M.style.TextAppearance_Material3_HeadlineMedium
    "headlineSmall" -> M.style.TextAppearance_Material3_HeadlineSmall
    "titleLarge" -> M.style.TextAppearance_Material3_TitleLarge
    "titleMedium" -> M.style.TextAppearance_Material3_TitleMedium
    "titleSmall" -> M.style.TextAppearance_Material3_TitleSmall
    "bodyLarge" -> M.style.TextAppearance_Material3_BodyLarge
    "bodyMedium" -> M.style.TextAppearance_Material3_BodyMedium
    "bodySmall" -> M.style.TextAppearance_Material3_BodySmall
    "labelLarge" -> M.style.TextAppearance_Material3_LabelLarge
    "labelMedium" -> M.style.TextAppearance_Material3_LabelMedium
    "labelSmall" -> M.style.TextAppearance_Material3_LabelSmall
    else -> M.style.TextAppearance_Material3_BodyLarge
}
