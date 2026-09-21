package com.example.espoti.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.espoti.R

// ============================================================================
// FONTS / TEXT STYLES
// ----------------------------------------------------------------------------
// This is the ONE place to change fonts and font sizes for the whole app.
// Every screen reads a style through MaterialTheme.typography.<name>, so
// changing a style here updates every screen that uses it.
//
// THE THREE FONTS, PER "Figma Images/general.txt":
//   appFont            = Irish Grover      -> decorative, ONLY the "Espoti"
//                         wordmark (EspotiLogoStyle). This is the curly,
//                         hand-drawn font seen on Bienvenida/Login/Registro.
//   general font       = Plus Jakarta Sans -> the app's main UI font: big
//                         headings, section titles and buttons - anything
//                         that needs emphasis or is short/UI chrome.
//   second general font = Inter            -> the reading font: paragraph
//                         text, form input and helper/secondary labels -
//                         anything meant to be read at length, since Inter
//                         is tuned for on-screen legibility at small sizes.
//
// The .ttf files live in app/src/main/res/font/ (irish_grover.ttf,
// plus_jakarta_sans.ttf, inter.ttf - the last two are variable fonts, so a
// single file covers every weight via FontVariation below).
//
// WHERE EACH STYLE IS USED IN THIS APP:
//   EspotiLogoStyle -> the "Espoti" wordmark (WelcomeScreen, Login, Registro)
//   headlineMedium  -> big greeting text, e.g. "Hello, Where do we meet?"
//   titleMedium     -> section headers, e.g. "Next Meetings", "Meeting History"
//   bodyLarge       -> normal paragraph text, text field input
//   bodyMedium      -> helper/secondary text, field labels
//   labelLarge      -> button text
//   labelSmall      -> small helper/caption text
//
// HOW TO USE THESE IN YOUR OWN COMPOSABLES:
//   - For one of the roles above, just read it off the theme, e.g.:
//       Text("Next Meetings", style = MaterialTheme.typography.titleMedium)
//   - For the logo font specifically (not a Typography() role):
//       Text("Espoti", style = EspotiLogoStyle)
//   - To use one of the three font families directly, outside a predefined
//     role (e.g. a one-off Text that needs Inter but a custom size):
//       Text("...", fontFamily = Inter, fontWeight = FontWeight.Medium)
// ============================================================================

/** Decorative wordmark font (Irish Grover). Only one weight exists. */
val IrishGrover = FontFamily(Font(R.font.irish_grover, FontWeight.Normal))

/** Main UI font (Plus Jakarta Sans) - headings, titles, buttons. Variable
 *  font: one file, weight picked per-instance via FontVariation. */
@OptIn(ExperimentalTextApi::class)
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/** Reading font (Inter) - paragraph text, form input, helper labels.
 *  Also a variable font, same weight-via-FontVariation approach. */
@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/** Style for the "Espoti" wordmark specifically - not part of Typography()
 *  below since Material3's Typography doesn't have a dedicated "logo" slot.
 *  Used directly, e.g. Text("Espoti", style = EspotiLogoStyle). */
val EspotiLogoStyle = TextStyle(
    fontFamily = IrishGrover,
    fontWeight = FontWeight.Normal,
    fontSize = 30.sp
)

val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)
