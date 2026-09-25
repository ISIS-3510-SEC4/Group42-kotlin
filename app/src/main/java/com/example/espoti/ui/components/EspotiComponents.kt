package com.example.espoti.ui.components

import androidx.compose.foundation.layout.Column
import com.example.espoti.model.NoticeType
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.espoti.ui.theme.BrandBrown

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
 * @param isPassword masks the input and adds a show/hide eye icon.
 * @param supportingText small helper text shown under the field (e.g. which
 * email extensions are accepted). Stays visible regardless of [isError].
 * @param isError tints the field red to flag it as the source of the
 * current validation error - pair with an [EspotiNoticeCard] that explains
 * *why* it's invalid, rather than duplicating the message here.
 */
@Composable
fun EspotiField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
    isError: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }

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
            isError = isError,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType
            ),
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            } else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )

        if (supportingText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Dismissible card shown at the top of a screen for two cases: (1) form
 * validation failed - message explains what's wrong, [type] = ERROR - or
 * (2) the user tapped something not wired up yet in this prototype (social
 * login, "Forgot Password?"), [type] = INFO.
 *
 * Callers own the visibility state (e.g. `var notice by remember { mutableStateOf<String?>(null) }`)
 * and typically wrap this in `AnimatedVisibility` - see LoginScreen.kt/RegisterScreen.kt.
 */
@Composable
fun EspotiNoticeCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    type: NoticeType = NoticeType.ERROR
) {
    val containerColor = when (type) {
        NoticeType.ERROR -> MaterialTheme.colorScheme.error
        NoticeType.INFO -> BrandBrown
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (type == NoticeType.ERROR) Icons.Filled.ErrorOutline else Icons.Filled.Info,
                contentDescription = null,
                tint = Color.White
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White)
            }
        }
    }
}
