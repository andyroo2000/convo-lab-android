package com.cdcg.convolab.journal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/** Wakes the account-owned outbox after a connection returns; it never sends requests itself. */
class JournalConnectionMonitor(context: Context, private val connected: () -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { connected() }
    }
    fun start() { manager.registerDefaultNetworkCallback(callback) }
    fun stop() { manager.unregisterNetworkCallback(callback) }
}
