package org.velo.android.engine.ui

/**
 * Discoverable constant providers for the string tokens the UI natives accept — the Velo
 * equivalent of NanoVM's `Color`/`Icons`/`TextStyle` constants. Each method just returns its
 * own canonical token (e.g. `Colors.primary()` -> "primary"), so programs can write
 * `ui.icon(Icons.add())` and have the host validate the name, while raw strings still work.
 * Registered as the natives `Colors`, `Icons`, `TextStyles`.
 */

@Suppress("FunctionName")
class Colors {
    fun primary(): String = "primary"
    fun onPrimary(): String = "onPrimary"
    fun primaryContainer(): String = "primaryContainer"
    fun onPrimaryContainer(): String = "onPrimaryContainer"
    fun secondary(): String = "secondary"
    fun onSecondary(): String = "onSecondary"
    fun secondaryContainer(): String = "secondaryContainer"
    fun onSecondaryContainer(): String = "onSecondaryContainer"
    fun tertiary(): String = "tertiary"
    fun onTertiary(): String = "onTertiary"
    fun tertiaryContainer(): String = "tertiaryContainer"
    fun onTertiaryContainer(): String = "onTertiaryContainer"
    fun error(): String = "error"
    fun onError(): String = "onError"
    fun errorContainer(): String = "errorContainer"
    fun onErrorContainer(): String = "onErrorContainer"
    fun background(): String = "background"
    fun onBackground(): String = "onBackground"
    fun surface(): String = "surface"
    fun onSurface(): String = "onSurface"
    fun surfaceVariant(): String = "surfaceVariant"
    fun onSurfaceVariant(): String = "onSurfaceVariant"
    fun surfaceContainer(): String = "surfaceContainer"
    fun outline(): String = "outline"
    fun outlineVariant(): String = "outlineVariant"
}

@Suppress("FunctionName")
class Icons {
    fun add(): String = "add"
    fun remove(): String = "remove"
    fun menu(): String = "menu"
    fun search(): String = "search"
    fun settings(): String = "settings"
    fun home(): String = "home"
    fun favorite(): String = "favorite"
    fun favoriteBorder(): String = "favoriteBorder"
    fun star(): String = "star"
    fun info(): String = "info"
    fun check(): String = "check"
    fun checkCircle(): String = "checkCircle"
    fun close(): String = "close"
    fun clear(): String = "clear"
    fun delete(): String = "delete"
    fun edit(): String = "edit"
    fun share(): String = "share"
    fun back(): String = "back"
    fun forward(): String = "forward"
    fun more(): String = "more"
    fun person(): String = "person"
    fun account(): String = "account"
    fun notifications(): String = "notifications"
    fun refresh(): String = "refresh"
    fun email(): String = "email"
    fun phone(): String = "phone"
    fun play(): String = "play"
    fun pause(): String = "pause"
    fun cart(): String = "cart"
    fun warning(): String = "warning"
    fun done(): String = "done"
    fun list(): String = "list"
    fun send(): String = "send"
    fun lock(): String = "lock"
    fun location(): String = "location"
    fun thumbUp(): String = "thumbUp"
    fun date(): String = "date"
    fun dropdown(): String = "dropdown"
}

@Suppress("FunctionName")
class TextStyles {
    fun displayLarge(): String = "displayLarge"
    fun displayMedium(): String = "displayMedium"
    fun displaySmall(): String = "displaySmall"
    fun headlineLarge(): String = "headlineLarge"
    fun headlineMedium(): String = "headlineMedium"
    fun headlineSmall(): String = "headlineSmall"
    fun titleLarge(): String = "titleLarge"
    fun titleMedium(): String = "titleMedium"
    fun titleSmall(): String = "titleSmall"
    fun bodyLarge(): String = "bodyLarge"
    fun bodyMedium(): String = "bodyMedium"
    fun bodySmall(): String = "bodySmall"
    fun labelLarge(): String = "labelLarge"
    fun labelMedium(): String = "labelMedium"
    fun labelSmall(): String = "labelSmall"
}
