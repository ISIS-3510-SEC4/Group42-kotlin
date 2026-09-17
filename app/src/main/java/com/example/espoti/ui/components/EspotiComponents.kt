package com.example.espoti.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

// ============================================================================
// REUSABLE UI PIECES
// ----------------------------------------------------------------------------
// Buttons and text fields for all 4 screens are built once here and reused,
// so changing the shape/height/corner-radius here changes it everywhere.
// If you want ONE specific instance to look different, pass a different
// `modifier`/color argument at the call site instead of editing this file.
// ============================================================================

/**
 * The pill/stadium-shaped button used everywhere in the design: the two
 * Bienvenida buttons (pass `containerColor = BrandOrange` there), and the
 * Login/Register/"Create new meeting"/"Support" buttons (which use the
 * default brown - MaterialTheme.colorScheme.primary).
 *
 * Width is controlled by `modifier`: pass `Modifier.fillMaxWidth()` for a
 * full-width button (Bienvenida, Inicio) or something like
 * `Modifier.fillMaxWidth(0.55f)` for a narrower, centered one (Login/Registro).
 */
@Composable
fun EspotiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(52.dp), // <- button height, tweak to match Figma
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50), // <- 50% = fully rounded pill ends
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Label-above-field input used for every field on Login/Registro
 * (Email, Password, Confirm Email, Confirm password...): a plain text
 * label, then a solid peach box with no visible border - matching the
 * Figma design, which has no floating/inline label and no border ring.
 *
 * @param isPassword masks the input. The Figma design has no show/hide
 * icon on password fields, so none is added here - see the ICON SPOT
 * comment below if you want to add one.
 */
@Composable
fun EspotiField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = modifier) {
        // Field label - change size/weight via MaterialTheme.typography.bodyMedium in Type.kt
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp), // <- corner roundness of the field
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType
            ),
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
                // ICON SPOT: to add a show/hide toggle like most login forms
                // (not present in this Figma file), add a `trailingIcon = { ... }`
                // parameter here with an IconButton that flips a
                // `remember { mutableStateOf(false) }` boolean, same as
                // `visualTransformation` above does.
            } else {
                VisualTransformation.None
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}
