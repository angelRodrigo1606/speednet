package com.angel.speednet.domain.scoring

import com.angel.speednet.domain.model.ScoreLevel
import kotlin.math.roundToInt

/**
 * Sistema de calificación 0..100 con pesos:
 * descarga 30%, latencia 25%, subida 15%, jitter 15%, pérdida 15%.
 *
 * Lógica pura (sin Android) para poder testearla con JUnit.
 */
object ScoreCalculator {

    private const val DOWNLOAD_WEIGHT = 0.30
    private const val LATENCY_WEIGHT = 0.25
    private const val UPLOAD_WEIGHT = 0.15
    private const val JITTER_WEIGHT = 0.15
    private const val LOSS_WEIGHT = 0.15

    fun downloadScore(mbps: Double): Int = higherIsBetter(
        value = mbps,
        bands = listOf(
            100.0 to 100, 50.0 to 90, 25.0 to 75, 10.0 to 55, 5.0 to 35, 2.0 to 15
        ),
        fallback = 0
    )

    fun uploadScore(mbps: Double): Int = higherIsBetter(
        value = mbps,
        bands = listOf(
            50.0 to 100, 20.0 to 90, 10.0 to 75, 5.0 to 55, 2.0 to 35, 1.0 to 15
        ),
        fallback = 0
    )

    fun latencyScore(ms: Double): Int = lowerIsBetter(
        value = ms,
        bands = listOf(
            20.0 to 100, 40.0 to 90, 60.0 to 75, 100.0 to 55, 150.0 to 35, 200.0 to 15
        ),
        fallback = 0
    )

    fun jitterScore(ms: Double): Int = lowerIsBetter(
        value = ms,
        bands = listOf(
            5.0 to 100, 10.0 to 90, 20.0 to 75, 30.0 to 55, 50.0 to 35, 75.0 to 15
        ),
        fallback = 0
    )

    fun lossScore(packetLossPct: Double): Int = when {
        packetLossPct <= 0.0 -> 100
        packetLossPct <= 0.5 -> 90
        packetLossPct <= 1.0 -> 75
        packetLossPct <= 2.0 -> 55
        packetLossPct <= 3.0 -> 35
        packetLossPct <= 5.0 -> 15
        else -> 0
    }

    fun overallScore(
        downloadMbps: Double,
        uploadMbps: Double,
        latencyMs: Double,
        jitterMs: Double,
        packetLossPct: Double
    ): Int {
        val weighted =
            DOWNLOAD_WEIGHT * downloadScore(downloadMbps) +
                LATENCY_WEIGHT * latencyScore(latencyMs) +
                UPLOAD_WEIGHT * uploadScore(uploadMbps) +
                JITTER_WEIGHT * jitterScore(jitterMs) +
                LOSS_WEIGHT * lossScore(packetLossPct)
        return weighted.roundToInt().coerceIn(0, 100)
    }

    fun levelFor(score: Int): ScoreLevel = when {
        score >= 80 -> ScoreLevel.EXCELLENT
        score >= 60 -> ScoreLevel.GOOD
        score >= 40 -> ScoreLevel.FAIR
        else -> ScoreLevel.POOR
    }

    private fun higherIsBetter(value: Double, bands: List<Pair<Double, Int>>, fallback: Int): Int {
        for ((threshold, points) in bands) {
            if (value >= threshold) return points
        }
        return fallback
    }

    private fun lowerIsBetter(value: Double, bands: List<Pair<Double, Int>>, fallback: Int): Int {
        for ((threshold, points) in bands) {
            if (value < threshold) return points
        }
        return fallback
    }
}
