package com.ivandsky.puzzlebox.app.match

import android.net.ConnectivityManager
import android.net.Network
import com.ivandsky.puzzlebox.data.network.NetworkStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** [NetworkStatus] from the system's default network; registered for as long as something collects it. */
class AndroidNetworkStatus(private val connectivity: ConnectivityManager) : NetworkStatus {

    override val online: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(connectivity.activeNetwork != null)
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
