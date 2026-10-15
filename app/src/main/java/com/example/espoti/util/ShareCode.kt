package com.example.espoti.util

import android.content.Context
import android.content.Intent

// ============================================================================
// SHARE FRIEND CODE
// ----------------------------------------------------------------------------
// Opens Android's share sheet with the user's friend code, so they can send
// it by WhatsApp, SMS, email... Used by Friends (share icon + "Contacts")
// and Profile (share icon). Needs no permission.
// ============================================================================

/** Text sent with the code; the receiver types the code in Friends > Search. */
fun friendCodeMessage(code: String): String =
    "Add me on Espoti! My friend code is $code"

fun shareFriendCode(context: Context, code: String) {
    if (code.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, friendCodeMessage(code))
    }
    context.startActivity(Intent.createChooser(send, "Share your Espoti code"))
}
