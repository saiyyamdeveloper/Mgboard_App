package com.mgboard.keyboard.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Themes — web ke `applyTheme(name)` ke same 4 options: system/dark/light/amoled.
 * Android 12+ par dynamic (Material You) colour system theme par chalta hai.
 */
@Composable
fun MgTheme(forcedTheme: String = "system", content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val dark = when (forcedTheme) {
        "dark", "amoled" -> true
        "light" -> false
        else -> systemDark
    }

    val scheme = when {
        forcedTheme == "amoled" -> AMOLED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && forcedTheme == "system" ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Pure-black theme (web: amoled). */
private val AMOLED = darkColorScheme(
    primary = Color(0xFF8BD48B),
    onPrimary = Color.Black,
    secondary = Color(0xFF6FBF73),
    background = Color.Black,
    onBackground = Color(0xFFE6E6E6),
    surface = Color.Black,
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2A2A2A),
)

/** Theme names jo Settings mein dikhte hain (web ke same). */
object ThemeOptions {
    val ALL = listOf("system", "light", "dark", "amoled")
    fun label(name: String, hindi: Boolean): String = when (name) {
        "system" -> if (hindi) "सिस्टम डिफ़ॉल्ट" else "System default"
        "light" -> if (hindi) "हल्का" else "Light"
        "dark" -> if (hindi) "गहरा" else "Dark"
        "amoled" -> if (hindi) "काला (AMOLED)" else "Black (AMOLED)"
        else -> name
    }
}
