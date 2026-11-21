package com.angel.speednet.domain.model

enum class NetworkType {
    WIFI,
    CELLULAR_4G,
    CELLULAR_5G,
    CELLULAR_OTHER,
    UNKNOWN
}

/**
 * Información de la red activa en el momento de la medición.
 * [signalDbm] es RSSI en Wi-Fi y RSRP en celular (null si no se pudo leer).
 */
data class NetworkSignal(
    val type: NetworkType = NetworkType.UNKNOWN,
    val signalDbm: Int? = null,
    val ssid: String? = null,
    val carrierName: String? = null
)
