package app.onlyshots.sources.machine

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.IO
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.darwin.inet_ntop
import platform.posix.AF_INET
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_UP
import platform.posix.INET_ADDRSTRLEN
import platform.posix.sockaddr_in

@OptIn(ExperimentalForeignApi::class)
actual fun localIpv4Addresses(): List<String> = memScoped {
    val list = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(list.ptr) != 0) return@memScoped emptyList()
    val out = mutableListOf<String>()
    try {
        var cursor = list.value
        while (cursor != null) {
            val entry = cursor.pointed
            val addr = entry.ifa_addr
            val flags = entry.ifa_flags.toInt()
            if (
                addr != null &&
                    addr.pointed.sa_family.toInt() == AF_INET &&
                    flags and IFF_UP != 0 &&
                    flags and IFF_LOOPBACK == 0
            ) {
                val buffer = allocArray<ByteVar>(INET_ADDRSTRLEN)
                val inet = addr.reinterpret<sockaddr_in>().pointed
                inet_ntop(AF_INET, inet.sin_addr.ptr, buffer, INET_ADDRSTRLEN.toUInt())?.let {
                    out += buffer.toKString()
                }
            }
            cursor = entry.ifa_next
        }
    } finally {
        freeifaddrs(list.value)
    }
    out
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun resolveIpv4(host: String): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        memScoped {
            val hints = alloc<platform.posix.addrinfo>().apply { ai_family = AF_INET }
            val result = alloc<CPointerVar<platform.posix.addrinfo>>()
            if (platform.posix.getaddrinfo(host, null, hints.ptr, result.ptr) != 0) return@memScoped null
            try {
                val addr = result.value?.pointed?.ai_addr ?: return@memScoped null
                val buffer = allocArray<ByteVar>(INET_ADDRSTRLEN)
                val inet = addr.reinterpret<sockaddr_in>().pointed
                inet_ntop(AF_INET, inet.sin_addr.ptr, buffer, INET_ADDRSTRLEN.toUInt())?.let { buffer.toKString() }
            } finally {
                platform.posix.freeaddrinfo(result.value)
            }
        }
    }
