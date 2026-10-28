package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.connectivity.ConnectivityObserver
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ============================================================================
// CONNECTIVITY STATE FOR VIEWMODELS
// ----------------------------------------------------------------------------
// Shared by every ViewModel that behaves differently offline. The app's
// ViewModels expose Compose State (not StateFlow), so this copies the
// observer's flow into a State, calls [onDisconnected] when the connection is
// lost and [onReconnected] when the device goes from offline back to online,
// so screens refresh by themselves (avoids the "unavailable functionality
// after connection recovery" anti-pattern).
// ============================================================================
internal fun ViewModel.trackConnectivity(
    observer: ConnectivityObserver,
    onDisconnected: () -> Unit = {},
    onReconnected: () -> Unit = {}
): State<Boolean> {
    val state = mutableStateOf(observer.isOnline.value)
    viewModelScope.launch {
        observer.isOnline.collect { online ->
            val wasOnline = state.value
            state.value = online
            when {
                online && !wasOnline -> onReconnected()
                !online && wasOnline -> onDisconnected()
            }
        }
    }
    return state
}

/** Messages shown when an action needs the network (specific, not "something went wrong"). */
internal object OfflineMessages {
    const val LOGIN = "You're offline. Logging in needs internet. Connect and try again."
    const val REGISTER = "You're offline. Creating an account needs internet to check that " +
        "your username is free. Your data stays in the form."
    const val FRIENDS_ACTION = "You're offline. Adding, removing or blocking friends needs internet."
    const val NOTIFICATION_ACTION = "You're offline. Answering invitations needs internet."
    const val USERNAME_CHANGE = "You're offline. Changing your username needs internet " +
        "(we check that it's free). Your other changes can be saved offline."
    const val DELETE_ACCOUNT = "You're offline. Deleting your account needs internet. Connect and try again."
    const val PROFILE_SAVED_OFFLINE = "Saved on this device. It will sync when you're back online."
}

/** "HH:mm" in the device zone, for "saved at ..." captions. */
internal fun timeLabel(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
