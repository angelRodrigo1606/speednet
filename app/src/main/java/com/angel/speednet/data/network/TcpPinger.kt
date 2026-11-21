package com.angel.speednet.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Medición de RTT mediante "TCP connect" (no requiere root).
 *
 * No sirve sola para la pérdida: TCP retransmite el SYN y un paquete perdido
 * sigue contando como éxito. Usar solo como último recurso, junto con
 * [com.angel.speednet.domain.engine.PacketLossMath.classifyTcpSamples].
 */
class TcpPinger {

    data class Sample(val rttMs: Double?, val success: Boolean)

    suspend fun ping(host: String, port: Int = DEFAULT_PORT, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Sample =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                val startNanos = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val rttMs = (System.nanoTime() - startNanos) / 1_000_000.0
                Sample(rttMs, success = true)
            } catch (_: Exception) {
                Sample(null, success = false)
            } finally {
                runCatching { socket.close() }
            }
        }

    companion object {
        const val DEFAULT_PORT = 443
        const val DEFAULT_TIMEOUT_MS = 2_000
    }
}
