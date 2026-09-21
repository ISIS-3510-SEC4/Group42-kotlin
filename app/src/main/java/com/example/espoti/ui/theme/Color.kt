package com.example.espoti.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// APP COLOR PALETTE
// ----------------------------------------------------------------------------
// These values come from the official palette file exported alongside the
// Figma screens ("Figma Images/general.txt"):
//   primary (brown)  = 582707
//   secondary (orange) = ED7D3A
//   peach            = FFEAAE
// This is the ONE place to change colors for the whole app - a hex color is
// 0xFFRRGGBB, where FF is opacity (leave it as FF) and RRGGBB is the same
// hex you'd copy from Figma's Fill panel.
//
// These are just raw values. WHERE each one is actually used is documented
// next to it below, and wired up in Theme.kt.
// ============================================================================

/** Dark brown. Bienvenida's full-screen background, AND the fill color of
 *  every solid button elsewhere (Login/Register/Create meeting/Support),
 *  the bottom nav bar, section headings and the line-art logo. */
val BrandBrown = Color(0xFF582707)

/** Warm orange. The two buttons on Bienvenida, links ("Sign Up", "Login",
 *  "Forgot Password?"), the active bottom-nav icon and the floating "+"
 *  button on Inicio. */
val BrandOrange = Color(0xFFED7D3A)

/** Light peach. Fill color of every text field, and of the cards on Inicio
 *  (meeting cards, "Invite us a coffee" card, history placeholders).
 *  Same exact token as TextCream below - the design palette (general.txt)
 *  only defines one light/peach color, reused for both roles. */
val SurfacePeach = Color(0xFFFFEAAE)

/** Pale cream. Text color used on top of the dark brown Bienvenida
 *  background ("Espoti" wordmark, "Where do we meet?" tagline). */
val TextCream = Color(0xFFFFEAAE)

/** Near-black. Body text on white screens, e.g. the "Email"/"Password"
 *  field labels on Login/Registro. */
val TextDark = Color(0xFF202020)

// --- Status colors (not present in the Figma file, used for form errors) ---
val StatusError = Color(0xFFB3261E)

// Legacy Material 3 template colors (unused now, kept only so nothing else
// in the project breaks if it still references them). Safe to delete.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
