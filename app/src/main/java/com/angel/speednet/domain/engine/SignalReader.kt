package com.angel.speednet.domain.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import com.angel.speednet.domain.model.NetworkSignal
import com.angel.speednet.domain.model.NetworkType

/**
 * Lee el tipo de red y la fuerza de señal usando APIs del sistema.
 * Tolera permisos denegados devolviendo un [NetworkSignal] parcial.
 */
class SignalReader(private val context: Context) {

    fun read(): NetworkSignal {
        val type = detectNetworkType()
        return when (type) {
            NetworkType.WIFI -> readWifi()
            NetworkType.CELLULAR_4G,
            NetworkType.CELLULAR_5G,
            NetworkType.CELLULAR_OTHER -> readCellular(type)

            NetworkType.UNKNOWN -> NetworkSignal(type = NetworkType.UNKNOWN)
        }
    }

    private fun detectNetworkType(): NetworkType {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkType.UNKNOWN
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkType.UNKNOWN
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> cellularGeneration()
            else -> NetworkType.UNKNOWN
        }
    }

    private fun cellularGeneration(): NetworkType {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return NetworkType.CELLULAR_OTHER
        val dataType = try {
            tm.dataNetworkType
        } catch (_: SecurityException) {
            return NetworkType.CELLULAR_OTHER
        }
        return when (dataType) {
            TelephonyManager.NETWORK_TYPE_NR -> NetworkType.CELLULAR_5G
            TelephonyManager.NETWORK_TYPE_LTE -> NetworkType.CELLULAR_4G
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> NetworkType.CELLULAR_OTHER
            else -> NetworkType.CELLULAR_OTHER
        }
    }

    @Suppress("DEPRECATION")
    private fun readWifi(): NetworkSignal {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        var rssi: Int? = null
        var ssid: String? = null
        try {
            val info = wifiManager?.connectionInfo
            if (info != null) {
                rssi = info.rssi
                val rawSsid = info.ssid?.trim('"')
                if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                    ssid = rawSsid
                }
            }
        } catch (_: SecurityException) {
            // Sin ACCESS_FINE_LOCATION no se puede leer SSID/RSSI.
        }
        return NetworkSignal(type = NetworkType.WIFI, signalDbm = rssi, ssid = ssid)
    }

    @Suppress("DEPRECATION")
    private fun readCellular(type: NetworkType): NetworkSignal {
        val tm = context.getSystemService(TelephonyManager::class.java)
        var carrier: String? = null
        var dbm: Int? = null

        try {
            carrier = tm?.networkOperatorName
        } catch (_: SecurityException) {
            // sin READ_PHONE_STATE puede fallar
        }

        try {
            val cells = tm?.allCellInfo
            if (cells != null) {
                val nr = cells.filterIsInstance<CellInfoNr>().firstOrNull { it.isRegistered }
                val lte = cells.filterIsInstance<CellInfoLte>().firstOrNull { it.isRegistered }

                dbm = when {
                    type == NetworkType.CELLULAR_5G && nr != null ->
                        (nr.cellSignalStrength as? CellSignalStrengthNr)?.ssRsrp

                    lte != null -> lte.cellSignalStrength.rsrp
                    else -> null
                }
            }
        } catch (_: SecurityException) {
            // sin ubicación/telefonía no hay detalle de celdas
        }

        if (dbm != null && (dbm == Int.MAX_VALUE || dbm >= 0)) {
            dbm = null
        }

        return NetworkSignal(type = type, signalDbm = dbm, carrierName = carrier)
    }
}
