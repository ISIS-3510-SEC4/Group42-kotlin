package com.example.espoti.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ============================================================================
// CONNECTIVITY OBSERVER (eventual connectivity)
// ----------------------------------------------------------------------------
// Tells the app whether it can reach the internet right now. ViewModels read
// it before network-only actions (login, register, friend changes...) and
// reload their data when it goes from offline back to online.
// ============================================================================
interface ConnectivityObserver {
    /** True while the device has a network that actually reaches the internet. */
    val isOnline: StateFlow<Boolean>
}

/** Used in JVM unit tests (no Android framework) unless a test passes its own fake. */
object AlwaysOnlineConnectivity : ConnectivityObserver {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}

/**
 * Real implementation: a [ConnectivityManager.NetworkCallback] on the default
 * network, so changes are pushed to us by the system (no polling). A network
 * only counts as online when Android VALIDATED it, i.e. it really reaches the
 * internet (a Wi-Fi without internet or a captive portal counts as offline).
 * Registered once for the whole app (see AppContainer), never unregistered.
 */
class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(isCurrentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // The default network we last heard about. When the phone switches from
    // Wi-Fi to mobile data, onLost may arrive for the OLD network after the new
    // one is already available; only losing the current one means offline.
    @Volatile
    private var currentNetwork: Network? = null

    init {
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                currentNetwork = network
                _isOnline.value = capabilities.reachesInternet()
            }

            override fun onLost(network: Network) {
                if (network == currentNetwork || currentNetwork == null) {
                    currentNetwork = null
                    _isOnline.value = false
                }
            }
        })
    }

    private fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        return connectivityManager.getNetworkCapabilities(network)?.reachesInternet() == true
    }

    private fun NetworkCapabilities.reachesInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
