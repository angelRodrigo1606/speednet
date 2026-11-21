package com.angel.speednet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val latencyMs: Double,
    val jitterMs: Double,
    val packetLossPct: Double,
    val score: Int,
    val level: String,
    val networkType: String,
    val signalDbm: Int?,
    val ssid: String?,
    val carrierName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val verdicts: String
)
