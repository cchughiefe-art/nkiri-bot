package com.nkiridown.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkMonitor(
    context: Context,
    private val onAvailable: () -> Unit,
    private val onLost: () -> Unit
) {
    private val connectivity =
        context.applicationContext
            .getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

    private var registered = false

    private val callback =
        object :
            ConnectivityManager.NetworkCallback() {

            override fun onAvailable(
                network: Network
            ) {
                onAvailable()
            }

            override fun onLost(
                network: Network
            ) {
                if (!isOnline()) {
                    onLost()
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                if (
                    capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    ) &&
                    capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )
                ) {
                    onAvailable()
                }
            }
        }

    fun start() {
        if (registered) return

        val request =
            NetworkRequest.Builder()
                .addCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                .build()

        connectivity.registerNetworkCallback(
            request,
            callback
        )

        registered = true

        if (isOnline()) {
            onAvailable()
        } else {
            onLost()
        }
    }

    fun stop() {
        if (!registered) return

        runCatching {
            connectivity.unregisterNetworkCallback(
                callback
            )
        }

        registered = false
    }

    fun isOnline(): Boolean {
        val network =
            connectivity.activeNetwork
                ?: return false

        val caps =
            connectivity.getNetworkCapabilities(
                network
            ) ?: return false

        return (
            caps.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) &&
            caps.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
        )
    }
}
