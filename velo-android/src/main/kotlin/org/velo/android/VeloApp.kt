package org.velo.android

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Applies Material You dynamic colours to every activity when the platform
 * supports them (Android 12+), so the app follows the system wallpaper palette.
 * On older devices this is a no-op and the brand green scheme from the theme
 * (see `themes.xml` / `colors.xml`) is used instead.
 */
class VeloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
