package com.angel.speednet.domain.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cálculo de latencia, jitter y pérdida.
 *
 * Un `connect()` TCP exitoso no significa pérdida 0: si se pierde el SYN, el
 * kernel lo retransmite ~1 s después y la conexión igual "funciona". Esas
 * muestras infladas son pérdidas recuperadas y hay que contarlas.
 */
object PacketLossMath {

    /** Por debajo del RTO inicial del SYN (~1000 ms) y por encima del jitter normal. */
    const val RETRANSMIT_GAP_MS = 800.0

    data class Summary(
        val latencyMs: Double,
        val jitterMs: Double,
        val packetLossPct: Double,
        val attempts: Int,
        val losses: Int
    )

    fun classifyTcpSamples(samples: List<Double?>): List<Double?> {
        val finite = samples.filterNotNull()
        if (finite.size < 2) return samples
        val min = finite.min()
        val max = finite.max()
        if (max - min < RETRANSMIT_GAP_MS) return samples
        return samples.map { rtt ->
            if (rtt != null && rtt - min >= RETRANSMIT_GAP_MS) null else rtt
        }
    }

    fun summarize(samples: List<Double?>): Summary {
        val successes = samples.filterNotNull()
        val losses = samples.count { it == null }
        val latency = if (successes.isEmpty()) 0.0 else successes.average()
        val jitter = if (successes.size < 2) {
            0.0
        } else {
            successes.zipWithNext { a, b -> abs(b - a) }.average()
        }
        val lossPct = if (samples.isEmpty()) 100.0 else losses * 100.0 / samples.size
        return Summary(
            latencyMs = latency,
            jitterMs = jitter,
            packetLossPct = lossPct,
            attempts = samples.size,
            losses = losses
        )
    }

    /**
     * Combina sondas (reposo + bajo carga) con la tasa de retransmisión TCP.
     * Se queda con el peor valor observado: un 0% en reposo no debe tapar
     * pérdidas que solo aparecen al saturar el enlace.
     */
    fun combine(
        probeLosses: Int,
        probeAttempts: Int,
        extraLosses: Int,
        extraAttempts: Int,
        retransmissionPct: Double?
    ): Double {
        val attempts = probeAttempts + extraAttempts
        val probePct = if (attempts <= 0) {
            null
        } else {
            (probeLosses + extraLosses).coerceAtLeast(0) * 100.0 / attempts
        }
        val retrans = retransmissionPct?.takeIf { it >= 0.0 }
        val raw = listOfNotNull(probePct, retrans).maxOrNull() ?: 0.0
        return (raw * 10.0).roundToInt() / 10.0
    }
}
