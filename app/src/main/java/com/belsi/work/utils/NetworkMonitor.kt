package com.belsi.work.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class NetworkEvent {
    object Connected : NetworkEvent()
    object Disconnected : NetworkEvent()
}

/**
 * Реактивный мониторинг состояния сети.
 * Вместо point-in-time проверки через NetworkUtils, этот класс
 * предоставляет StateFlow<Boolean> и SharedFlow<NetworkEvent>.
 *
 * При появлении сети можно триггерить WorkManager задачи (sync, upload).
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentNetwork())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkEvents = MutableSharedFlow<NetworkEvent>(extraBufferCapacity = 1)
    val networkEvents: SharedFlow<NetworkEvent> = _networkEvents.asSharedFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            _isOnline.value = true
            _networkEvents.tryEmit(NetworkEvent.Connected)
        }

        override fun onLost(network: Network) {
            // Проверяем есть ли другая сеть (wifi → mobile fallback)
            val stillOnline = checkCurrentNetwork()
            Log.d(TAG, "Network lost, still online: $stillOnline")
            _isOnline.value = stillOnline
            if (!stillOnline) {
                _networkEvents.tryEmit(NetworkEvent.Disconnected)
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isOnline.value = hasInternet
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun checkCurrentNetwork(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
