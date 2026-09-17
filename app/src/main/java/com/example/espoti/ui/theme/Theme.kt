package com.example.espoti.ui.theme

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

// ============================================================================
// COLOR ROLES
// ----------------------------------------------------------------------------
// This is where the raw colors from Color.kt get assigned to a *role*.
// Screens read colors through MaterialTheme.colorScheme.<role> instead of
// a hardcoded value, so changing a role here updates every screen that
// uses it at once.
//
// Cheat sheet for this app:
//   primary          -> BrandBrown  - solid buttons, headings, nav bar
//   secondary        -> BrandOrange - links, Bienvenida buttons, accents
//   background       -> white       - default screen background
//   surface          -> SurfacePeach - text fields, cards
//   onBackground     -> TextDark    - normal body text on white screens
//
// NOTE: Bienvenida is the one screen that does NOT use `background` (it's
// dark brown instead of white). Rather than swap the whole app's background
// role for one screen, WelcomeScreen.kt applies `BrandBrown` directly. See
// the comment there if you want to change that approach.
// ============================================================================

private val LightColorScheme = lightColorScheme(
    primary = BrandBrown,
    onPrimary = Color.White,
    secondary = BrandOrange,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = TextDark,
    surface = SurfacePeach,
    onSurface = BrandBrown,
    onSurfaceVariant = TextDark,
    error = StatusError
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color.Black,
    secondary = BrandBrown,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF2B2930),
    onBackground = Color.White,
    onSurface = Color.White,
    error = StatusError
)

@Composable
fun EspotiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color would make the app steal colors from the user's Android
    // wallpaper on Android 12+, which would hide the brand palette above.
    // Kept OFF so the Figma colors are always what's shown.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
