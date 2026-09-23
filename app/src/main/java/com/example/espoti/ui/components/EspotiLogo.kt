package com.example.espoti.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.espoti.R

/**
 * The Espoti logo, shared by every screen so it always matches the theme:
 * the light-colored icon on dark mode, the dark-colored one on light mode.
 * Use this instead of referencing the drawables directly, so future screens
 * get the light/dark switch for free.
 *
 * [lightVariant] can be forced for screens whose background doesn't follow the
 * theme (e.g. the Welcome screen is always brown, so it needs the light icon).
 */
@Composable
fun EspotiLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    lightVariant: Boolean = isSystemInDarkTheme()
) {
    val logo = if (lightVariant) R.drawable.logoicon else R.drawable.logoicondark
    Image(
        painter = painterResource(id = logo),
        contentDescription = "Espoti logo",
        modifier = modifier.size(size)
    )
}
