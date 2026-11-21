package com.angel.speednet.domain.model

/**
 * Resultado completo de una medición.
 */
data class Measurement(
    val id: Long? = null,
    val timestamp: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val latencyMs: Double,
    val jitterMs: Double,
    val packetLossPct: Double,
    val score: Int,
    val level: ScoreLevel,
    val network: NetworkSignal,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val verdicts: Map<NetworkUse, UseVerdict> = emptyMap()
)
