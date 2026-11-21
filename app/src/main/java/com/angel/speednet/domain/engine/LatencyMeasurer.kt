package com.angel.speednet.domain.engine

import com.angel.speednet.data.network.IcmpPinger
import com.angel.speednet.data.network.TcpPinger
import com.angel.speednet.data.network.UdpPinger
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Latencia (promedio de RTT), jitter (media de |RTT_i - RTT_i-1|) y pérdida.
 *
 * El `connect()` TCP al puerto 443 casi nunca falla: si se pierde un paquete,
 * el kernel lo retransmite y la prueba lo contaba como éxito (pérdida 0.0%).
 * Por eso el orden es:
 * 1. ICMP (`ping`), que no retransmite.
 * 2. UDP/DNS a los mismos servidores, tampoco retransmite y no requiere root.
 * 3. TCP solo como último recurso, contando timeouts y retransmisiones del SYN.
 */
class LatencyMeasurer(
    private val tcpPinger: TcpPinger = TcpPinger(),
    private val icmpPinger: IcmpPinger = IcmpPinger(),
    private val udpPinger: UdpPinger = UdpPinger()
) {

    data class Result(
        val latencyMs: Double,
        val jitterMs: Double,
        val packetLossPct: Double,
        val attempts: Int,
        val losses: Int
    )

    var icmpAvailable: Boolean = false
        private set

    var udpAvailable: Boolean = false
        private set

    suspend fun measure(
        hosts: List<String> = DEFAULT_HOSTS,
        attemptsPerHost: Int = DEFAULT_ATTEMPTS,
        timeoutMs: Int = PROBE_TIMEOUT_MS,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
        onProgress: (Float) -> Unit = {}
    ): Result {
        icmpAvailable = false
        udpAvailable = false

        collect(hosts, attemptsPerHost, timeoutMs, intervalMs, onProgress) { host, timeout ->
            icmpPinger.probe(host, timeout)?.let { Attempt.Done(it.rttMs, it.lost) }
                ?: Attempt.Unavailable
        }?.let { summary ->
            icmpAvailable = true
            return summary.toResult()
        }

        collect(hosts, attemptsPerHost, timeoutMs, intervalMs, onProgress) { host, timeout ->
            val sample = udpPinger.ping(host, timeoutMs = timeout)
            Attempt.Done(sample.rttMs, lost = !sample.success)
        }?.let { summary ->
            udpAvailable = true
            return summary.toResult()
        }

        val tcpSamples = collectTcp(hosts, attemptsPerHost, intervalMs, onProgress)
        return PacketLossMath.summarize(PacketLossMath.classifyTcpSamples(tcpSamples)).toResult()
    }

    /**
     * Una sonda extra mientras hay tráfico. Null si el protocolo elegido ya no responde.
     * True = paquete perdido.
     */
    suspend fun probeLoaded(host: String, timeoutMs: Int = LOADED_TIMEOUT_MS): Boolean? {
        if (icmpAvailable) {
            val probe = icmpPinger.probe(host, timeoutMs) ?: return null
            return probe.lost
        }
        if (udpAvailable) {
            return !udpPinger.ping(host, timeoutMs = timeoutMs).success
        }
        return null
    }

    private suspend fun collect(
        hosts: List<String>,
        attemptsPerHost: Int,
        timeoutMs: Int,
        intervalMs: Long,
        onProgress: (Float) -> Unit,
        probe: suspend (host: String, timeoutMs: Int) -> Attempt
    ): PacketLossMath.Summary? {
        val total = (hosts.size * attemptsPerHost).coerceAtLeast(1)
        val samples = ArrayList<Double?>(total)
        var successes = 0
        var failures = 0
        var filterChecked = false

        for (attempt in 0 until attemptsPerHost) {
            for (host in hosts) {
                coroutineContext.ensureActive()
                when (val outcome = probe(host, timeoutMs)) {
                    Attempt.Unavailable -> return null
                    is Attempt.Done -> {
                        if (outcome.lost) {
                            failures++
                            samples.add(null)
                        } else {
                            successes++
                            // Éxito sin RTT parseado: no lo cuentes como pérdida.
                            if (outcome.rttMs != null) samples.add(outcome.rttMs)
                        }
                    }
                }
                onProgress((samples.size.toFloat() / total).coerceIn(0f, 1f))

                // Varios timeouts y un TCP rápido: el protocolo está filtrado.
                // Si el enlace de verdad pierde paquetes, el SYN TCP no vuelve rápido.
                if (!filterChecked && failures >= FILTER_STREAK && successes == 0) {
                    filterChecked = true
                    if (fastTcp(hosts.first())) return null
                }
                if (intervalMs > 0) delay(intervalMs)
            }
        }

        if (samples.isEmpty()) return null
        if (successes == 0 && !filterChecked && fastTcp(hosts.first())) return null
        return PacketLossMath.summarize(samples)
    }

    private suspend fun collectTcp(
        hosts: List<String>,
        attemptsPerHost: Int,
        intervalMs: Long,
        onProgress: (Float) -> Unit
    ): List<Double?> {
        val total = (hosts.size * attemptsPerHost).coerceAtLeast(1)
        val samples = ArrayList<Double?>(total)
        var baseline = Double.POSITIVE_INFINITY

        for (attempt in 0 until attemptsPerHost) {
            for (host in hosts) {
                coroutineContext.ensureActive()
                // Por debajo del RTO del SYN (~1 s) una pérdida no se recupera a tiempo
                // y el intento falla de verdad. Solo se acorta cuando ya sabemos que
                // el RTT cabe en esa ventana; si no, un enlace lento parecería pérdida.
                val timeout = if (baseline <= FAST_PATH_RTT_MS) {
                    SHORT_TIMEOUT_MS
                } else {
                    TcpPinger.DEFAULT_TIMEOUT_MS
                }
                val rtt = tcpPinger.ping(host, timeoutMs = timeout).rttMs
                if (rtt != null && rtt < baseline) baseline = rtt
                samples.add(rtt)
                onProgress((samples.size.toFloat() / total).coerceIn(0f, 1f))
                if (intervalMs > 0) delay(intervalMs)
            }
        }
        return samples
    }

    /**
     * True solo si el camino responde rápido por TCP. Una pérdida real infla o
     * tumba el SYN; un TCP de pocos milisegundos con todas las sondas en timeout
     * significa que ICMP/UDP está filtrado, no que se pierda el 100%.
     */
    private suspend fun fastTcp(host: String): Boolean {
        val tcp = tcpPinger.ping(host, timeoutMs = TcpPinger.DEFAULT_TIMEOUT_MS)
        return tcp.success && (tcp.rttMs ?: Double.MAX_VALUE) < FAST_TCP_RTT_MS
    }

    private fun PacketLossMath.Summary.toResult(): Result = Result(
        latencyMs = latencyMs,
        jitterMs = jitterMs,
        packetLossPct = packetLossPct,
        attempts = attempts,
        losses = losses
    )

    private sealed interface Attempt {
        data object Unavailable : Attempt
        data class Done(val rttMs: Double?, val lost: Boolean) : Attempt
    }

    companion object {
        val DEFAULT_HOSTS = listOf("1.1.1.1", "8.8.8.8")
        const val DEFAULT_ATTEMPTS = 12
        const val DEFAULT_INTERVAL_MS = 250L
        const val PROBE_TIMEOUT_MS = 2_000
        const val LOADED_TIMEOUT_MS = 3_000
        private const val FILTER_STREAK = 3
        private const val SHORT_TIMEOUT_MS = 800
        private const val FAST_PATH_RTT_MS = 400.0
        private const val FAST_TCP_RTT_MS = 1_200.0
    }
}
