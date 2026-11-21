package com.angel.speednet.domain.scoring

import com.angel.speednet.domain.model.NetworkUse
import com.angel.speednet.domain.model.UseVerdict

/**
 * Veredictos por uso: videollamadas, streaming, juegos y navegación.
 */
object VerdictCalculator {

    fun verdicts(
        downloadMbps: Double,
        uploadMbps: Double,
        latencyMs: Double,
        jitterMs: Double,
        packetLossPct: Double
    ): Map<NetworkUse, UseVerdict> = mapOf(
        NetworkUse.VIDEO_CALL to videoCall(downloadMbps, uploadMbps, latencyMs, jitterMs, packetLossPct),
        NetworkUse.STREAMING to streaming(downloadMbps),
        NetworkUse.GAMING to gaming(latencyMs, jitterMs, packetLossPct),
        NetworkUse.BROWSING to browsing(downloadMbps, latencyMs)
    )

    private fun videoCall(
        downloadMbps: Double,
        uploadMbps: Double,
        latencyMs: Double,
        jitterMs: Double,
        packetLossPct: Double
    ): UseVerdict = when {
        uploadMbps >= 5 && downloadMbps >= 5 && latencyMs < 50 && jitterMs < 20 && packetLossPct < 1 ->
            UseVerdict.OPTIMAL

        uploadMbps >= 1.5 && downloadMbps >= 1.5 && latencyMs < 100 && packetLossPct < 3 ->
            UseVerdict.ACCEPTABLE

        else -> UseVerdict.NOT_RECOMMENDED
    }

    private fun streaming(downloadMbps: Double): UseVerdict = when {
        downloadMbps >= 25 -> UseVerdict.OPTIMAL
        downloadMbps >= 5 -> UseVerdict.ACCEPTABLE
        else -> UseVerdict.NOT_RECOMMENDED
    }

    private fun gaming(latencyMs: Double, jitterMs: Double, packetLossPct: Double): UseVerdict = when {
        latencyMs < 30 && jitterMs < 10 && packetLossPct < 0.5 -> UseVerdict.OPTIMAL
        latencyMs < 60 && jitterMs < 20 && packetLossPct < 1 -> UseVerdict.ACCEPTABLE
        else -> UseVerdict.NOT_RECOMMENDED
    }

    private fun browsing(downloadMbps: Double, latencyMs: Double): UseVerdict = when {
        downloadMbps >= 5 && latencyMs < 100 -> UseVerdict.OPTIMAL
        downloadMbps >= 1 && latencyMs < 200 -> UseVerdict.ACCEPTABLE
        else -> UseVerdict.NOT_RECOMMENDED
    }
}
