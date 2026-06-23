package com.belsi.work.data.remote.interceptor

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * FIX(2026-05-01): VPN-совместимая DNS-резолюция.
 *
 * Проблема: некоторые VPN (особенно Shadowsocks/V2Ray/AmneziaVPN/корпоративные)
 * ломают системный DNS-резолвер, и `Dns.SYSTEM.lookup("api.belsi.ru")` падает с
 * `UnknownHostException: Unable to resolve host`.
 *
 * Решение:
 * 1. Сначала пробуем системный DNS.
 * 2. Если падает → используем захардкоженный IP-литерал для известных хостов
 *    (api.belsi.ru, bucket.api.belsi.ru → 94.228.123.95).
 *
 * SNI в TLS handshake идёт по hostname'у — сертификат api.belsi.ru остаётся
 * валидным, безопасность не страдает. Только обходим DNS-проблему.
 *
 * Если IP сервера сменится — нужно обновить `KNOWN_IPS` и выпустить релиз.
 */
class FallbackDns(
    private val system: Dns = Dns.SYSTEM,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // 1) Системный DNS — основной путь
            val result = system.lookup(hostname)
            if (result.isNotEmpty()) return result
            // На некоторых устройствах возвращается пустой список вместо exception
            useFallback(hostname)
                ?: throw UnknownHostException("System DNS returned empty for $hostname")
        } catch (e: UnknownHostException) {
            useFallback(hostname) ?: throw e
        }
    }

    private fun useFallback(hostname: String): List<InetAddress>? {
        val ipBytes = KNOWN_IPS[hostname] ?: return null
        return try {
            // getByAddress(hostname, bytes) НЕ выполняет DNS lookup —
            // создаёт InetAddress напрямую из байтов. SNI/Host Header
            // остаются с правильным hostname.
            listOf(InetAddress.getByAddress(hostname, ipBytes))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to construct fallback IP for $hostname", e)
            null
        }
    }

    companion object {
        private const val TAG = "FallbackDns"

        // 94.228.123.95 = api.belsi.ru (production)
        private val PROD_IP = byteArrayOf(
            94.toByte(),
            228.toByte(),
            123.toByte(),
            95.toByte(),
        )

        private val KNOWN_IPS: Map<String, ByteArray> = mapOf(
            "api.belsi.ru" to PROD_IP,
            "bucket.api.belsi.ru" to PROD_IP,
        )
    }
}
