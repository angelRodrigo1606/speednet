package com.angel.speednet.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 * Sonda UDP sin retransmisión. Un timeout es una pérdida real: a diferencia del
 * handshake TCP, aquí nadie reenvía el paquete por nosotros.
 *
 * Se usa una consulta DNS mínima a los mismos servidores (1.1.1.1 / 8.8.8.8),
 * que responden siempre. No hace falta root ni el binario `ping`.
 */
class UdpPinger {

    data class Sample(val rttMs: Double?, val success: Boolean)

    suspend fun ping(host: String, port: Int = DNS_PORT, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Sample =
        withContext(Dispatchers.IO) {
            val query = dnsQuery(Random.nextInt(1, 0x10000))
            val socket = DatagramSocket()
            try {
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port))
                val started = System.nanoTime()
                socket.send(DatagramPacket(query, query.size))
                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val rttMs = (System.nanoTime() - started) / 1_000_000.0
                val matches = response.length >= 2 && buffer[0] == query[0] && buffer[1] == query[1]
                if (matches) Sample(rttMs, success = true) else Sample(null, success = false)
            } catch (_: SocketTimeoutException) {
                Sample(null, success = false)
            } catch (_: Exception) {
                Sample(null, success = false)
            } finally {
                runCatching { socket.close() }
            }
        }

    companion object {
        const val DNS_PORT = 53
        const val DEFAULT_TIMEOUT_MS = 2_000

        /** Consulta A de example.com. El id va en los dos primeros bytes. */
        fun dnsQuery(id: Int): ByteArray {
            val name = byteArrayOf(
                7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
                'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
                3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
                0
            )
            val packet = ByteArray(12 + name.size + 4)
            packet[0] = (id ushr 8).toByte()
            packet[1] = id.toByte()
            packet[2] = 0x01 // recursion desired
            packet[5] = 0x01 // QDCOUNT
            name.copyInto(packet, destinationOffset = 12)
            val question = 12 + name.size
            packet[question + 1] = 0x01 // QTYPE A
            packet[question + 3] = 0x01 // QCLASS IN
            return packet
        }
    }
}
