package com.magicimageviewer.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

data class DiscoveredServer(val name: String, val host: String, val port: Int)

/**
 * Discovers PC agents on the LAN via mDNS/NSD. The PC agent registers itself
 * under SERVICE_TYPE (see pc-agent/agent.py). Reports every server found —
 * callers decide which one(s) to trust.
 */
class PcDiscovery(context: Context) {
    private val nsdManager = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null

    // NsdManager only tolerates one resolveService() in flight at a time on
    // most Android versions; multiple servers on the network can otherwise
    // trigger a crash. Resolve serially instead.
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start(onFound: (DiscoveredServer) -> Unit) {
        stop()

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                resolving = false
                resolveNext(this)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolving = false
                val host = serviceInfo.host?.hostAddress
                if (host != null) {
                    onFound(DiscoveredServer(serviceInfo.serviceName, host, serviceInfo.port))
                }
                resolveNext(this)
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.startsWith(SERVICE_TYPE)) {
                    resolveQueue.addLast(serviceInfo)
                    resolveNext(resolveListener)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        listener = discoveryListener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun resolveNext(resolveListener: NsdManager.ResolveListener) {
        if (resolving) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        nsdManager.resolveService(next, resolveListener)
    }

    fun stop() {
        listener?.let {
            runCatching { nsdManager.stopServiceDiscovery(it) }
        }
        listener = null
        resolveQueue.clear()
        resolving = false
    }

    companion object {
        private const val TAG = "PcDiscovery"
        private const val SERVICE_TYPE = "_magicimg._tcp."
    }
}
