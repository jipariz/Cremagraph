package app.onlyshots.sources.machine

import java.net.Inet4Address
import java.net.NetworkInterface

actual fun localIpv4Addresses(): List<String> = jvmLocalIpv4Addresses()

internal fun jvmLocalIpv4Addresses(): List<String> =
    try {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
    } catch (e: Exception) {
        emptyList()
    }

actual suspend fun resolveIpv4(host: String): String? = jvmResolveIpv4(host)

internal suspend fun jvmResolveIpv4(host: String): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            java.net.InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
