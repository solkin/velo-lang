package org.velo.android.engine.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import org.velo.android.R

/**
 * The fixed built-in icon set, mirroring NanoVM's `Icons` names one-for-one. Each name maps
 * to a bundled `res/drawable/ic_*.xml` Material vector; unknown names fall back to `info`, so
 * a program never crashes on a missing glyph. The names are the single source of truth shared
 * with the future `Icons` constant native (it hands these same strings to programs).
 */
internal fun iconRes(name: String): Int = when (name) {
    "add" -> R.drawable.ic_add
    "remove" -> R.drawable.ic_remove
    "menu" -> R.drawable.ic_menu
    "search" -> R.drawable.ic_search
    "settings" -> R.drawable.ic_settings
    "home" -> R.drawable.ic_home
    "favorite" -> R.drawable.ic_favorite
    "favoriteBorder" -> R.drawable.ic_favorite_border
    "star" -> R.drawable.ic_star
    "info" -> R.drawable.ic_info
    "check" -> R.drawable.ic_check
    "checkCircle" -> R.drawable.ic_check_circle
    "close" -> R.drawable.ic_close
    "clear" -> R.drawable.ic_close
    "delete" -> R.drawable.ic_delete
    "edit" -> R.drawable.ic_edit
    "share" -> R.drawable.ic_share
    "back" -> R.drawable.ic_arrow_back
    "forward" -> R.drawable.ic_arrow_forward
    "more" -> R.drawable.ic_more_vert
    "person" -> R.drawable.ic_person
    "account" -> R.drawable.ic_account_circle
    "notifications" -> R.drawable.ic_notifications
    "refresh" -> R.drawable.ic_refresh
    "email" -> R.drawable.ic_email
    "phone" -> R.drawable.ic_phone
    "play" -> R.drawable.ic_play_arrow
    "pause" -> R.drawable.ic_pause
    "cart" -> R.drawable.ic_shopping_cart
    "warning" -> R.drawable.ic_warning
    "done" -> R.drawable.ic_check
    "list" -> R.drawable.ic_list
    "send" -> R.drawable.ic_send
    "lock" -> R.drawable.ic_lock
    "location" -> R.drawable.ic_location_on
    "thumbUp" -> R.drawable.ic_thumb_up
    "date" -> R.drawable.ic_date_range
    "dropdown" -> R.drawable.ic_arrow_drop_down
    else -> R.drawable.ic_info
}

/** Load the bundled vector for [name] (compat-aware, so it works back to minSdk 23). */
internal fun loadIcon(ctx: Context, name: String): Drawable? =
    AppCompatResources.getDrawable(ctx, iconRes(name))
