package com.angel.speednet.data.local

import com.angel.speednet.data.local.entity.MeasurementEntity
import com.angel.speednet.domain.model.Measurement
import com.angel.speednet.domain.model.NetworkSignal
import com.angel.speednet.domain.model.NetworkType
import com.angel.speednet.domain.model.NetworkUse
import com.angel.speednet.domain.model.ScoreLevel
import com.angel.speednet.domain.model.UseVerdict

internal fun Measurement.toEntity(): MeasurementEntity = MeasurementEntity(
    id = id ?: 0L,
    timestamp = timestamp,
    downloadMbps = downloadMbps,
    uploadMbps = uploadMbps,
    latencyMs = latencyMs,
    jitterMs = jitterMs,
    packetLossPct = packetLossPct,
    score = score,
    level = level.name,
    networkType = network.type.name,
    signalDbm = network.signalDbm,
    ssid = network.ssid,
    carrierName = network.carrierName,
    latitude = latitude,
    longitude = longitude,
    verdicts = encodeVerdicts(verdicts)
)

internal fun MeasurementEntity.toDomain(): Measurement = Measurement(
    id = if (id == 0L) null else id,
    timestamp = timestamp,
    downloadMbps = downloadMbps,
    uploadMbps = uploadMbps,
    latencyMs = latencyMs,
    jitterMs = jitterMs,
    packetLossPct = packetLossPct,
    score = score,
    level = runCatching { ScoreLevel.valueOf(level) }.getOrDefault(ScoreLevel.POOR),
    network = NetworkSignal(
        type = runCatching { NetworkType.valueOf(networkType) }.getOrDefault(NetworkType.UNKNOWN),
        signalDbm = signalDbm,
        ssid = ssid,
        carrierName = carrierName
    ),
    latitude = latitude,
    longitude = longitude,
    verdicts = decodeVerdicts(verdicts)
)

private fun encodeVerdicts(verdicts: Map<NetworkUse, UseVerdict>): String =
    verdicts.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }

private fun decodeVerdicts(raw: String): Map<NetworkUse, UseVerdict> {
    if (raw.isBlank()) return emptyMap()
    return raw.split(";")
        .mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val use = runCatching { NetworkUse.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val verdict = runCatching { UseVerdict.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
            use to verdict
        }
        .toMap()
}
