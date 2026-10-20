package app.onlyshots.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import app.onlyshots.sources.machine.MdnsBrowser
import app.onlyshots.sources.machine.MdnsService
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * mDNS through [NsdManager]. Wi-Fi drops multicast to save power unless a lock is held, so one is held while browsing.
 * Services are resolved one at a time: before API 34 a second resolve while one is running fails outright.
 */
class NsdBrowser(context: Context) : MdnsBrowser {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    override fun browse(type: String): Flow<MdnsService> = callbackFlow {
        val lock = wifi?.createMulticastLock("onlyshots-mdns")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) {
                    found.trySend(info)
                }

                override fun onServiceLost(info: NsdServiceInfo) = Unit

                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    found.close()
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        launch {
            for (info in found) {
                resolve(info)?.let { send(it) }
            }
        }
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            found.close()
            lock?.release()
        }
    }

    @Suppress("DEPRECATION") // registerServiceInfoCallback is API 34+; minSdk is 26.
    private suspend fun resolve(info: NsdServiceInfo): MdnsService? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(
            info,
            object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = resolved.host?.hostAddress
                    cont.resume(
                        host?.let {
                            MdnsService(resolved.serviceName, resolved.serviceType, it, resolved.port)
                        }
                    )
                }

                override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                    cont.resume(null)
                }
            },
        )
    }
}
