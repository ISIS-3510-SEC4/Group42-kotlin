package com.example.espoti.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================================
// FONTS / TEXT STYLES
// ----------------------------------------------------------------------------
// This is the ONE place to change fonts and font sizes for the whole app.
// Every screen reads a style through MaterialTheme.typography.<name>, so
// changing a style here updates every screen that uses it.
//
// YOUR FIGMA FILE USES TWO DIFFERENT FONTS:
//   1. A decorative/handwritten serif for the "Espoti" wordmark only
//      (curly, vintage-looking letters).
//   2. A clean, slightly rounded sans-serif for everything else (labels,
//      buttons, greetings, body text).
// I can't read the exact font *names* Figma has assigned from a PNG export
// alone - to get them: open the file in Figma, click the "Espoti" text
// layer, and look at the font name at the top of the right-hand panel
// (do the same for any other text layer). Then either:
//   a) find that exact font as a Google Font at fonts.google.com and follow
//      the "custom font" steps below, or
//   b) if it's a paid/custom font, export it as a .ttf and use the same steps.
// Until you do that, `displayLogo` below defaults to a bold serif and
// everything else to the system default, which is a reasonably close stand-in.
//
// WHERE EACH STYLE IS USED IN THIS APP:
//   displayLogo    -> the "Espoti" wordmark (WelcomeScreen, Login, Registro)
//   headlineMedium -> big greeting text, e.g. "Hello, Where do we meet?"
//   titleMedium    -> section headers, e.g. "Next Meetings", "Meeting History"
//   bodyLarge      -> normal paragraph text, text field input
//   bodyMedium     -> helper/secondary text, field labels
//   labelLarge     -> button text
//
// TO USE A CUSTOM FONT (e.g. one exported from Figma):
//   1. Put the .ttf/.otf file(s) in app/src/main/res/font/ (create the
//      folder), named in lowercase, e.g. poppins_regular.ttf
//   2. Define a FontFamily, for example:
//        val Poppins = FontFamily(
//            Font(R.font.poppins_regular, FontWeight.Normal),
//            Font(R.font.poppins_bold, FontWeight.Bold)
//        )
//   3. Replace `FontFamily.Default` / `FontFamily.Serif` below with it.
// ============================================================================

/** Style for the "Espoti" wordmark specifically - not part of Typography()
 *  below since Material3's Typography doesn't have a dedicated "logo" slot.
 *  Used directly, e.g. Text("Espoti", style = EspotiLogoStyle). */
val EspotiLogoStyle = TextStyle(
    fontFamily = FontFamily.Serif, // stand-in for the decorative Figma font
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp
)

val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)
