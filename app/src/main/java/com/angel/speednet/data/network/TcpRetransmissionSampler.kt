package com.angel.speednet.data.network

import java.io.File

/**
 * Pérdida bajo carga a partir de retransmisiones TCP del propio dispositivo.
 *
 * El ping en reposo suele dar 0% aunque el enlace tire paquetes al saturarse
 * (descarga/subida). `RetransSegs / OutSegs` durante la prueba es esa pérdida.
 * En Android 12+ `/proc/net/snmp` a veces no se puede leer; en ese caso no hay dato.
 */
object TcpRetransmissionSampler {

    data class Snapshot(val outSegs: Long, val retransSegs: Long)

    fun read(): Snapshot? {
        for (path in PATHS) {
            val text = try {
                File(path).readText()
            } catch (_: Exception) {
                continue
            }
            parse(text)?.let { return it }
        }
        return null
    }

    fun parse(text: String): Snapshot? {
        val lines = text.lines()
        for (index in lines.indices) {
            val header = lines[index].trim()
            if (!header.startsWith("Tcp:") || !header.contains("RetransSegs")) continue
            val valuesLine = lines.getOrNull(index + 1)?.trim() ?: return null
            val names = header.split(Regex("\\s+"))
            val values = valuesLine.split(Regex("\\s+"))
            if (!values.firstOrNull().equals("Tcp:")) continue
            val outIdx = names.indexOf("OutSegs")
            val retransIdx = names.indexOf("RetransSegs")
            if (outIdx !in values.indices || retransIdx !in values.indices) return null
            val outSegs = values[outIdx].toLongOrNull() ?: return null
            val retransSegs = values[retransIdx].toLongOrNull() ?: return null
            return Snapshot(outSegs, retransSegs)
        }
        return null
    }

    /**
     * Porcentaje de segmentos retransmitidos entre dos lecturas.
     * Null si no hubo tráfico suficiente o los contadores no cuadran.
     */
    fun lossPct(before: Snapshot, after: Snapshot): Double? {
        val outSegs = after.outSegs - before.outSegs
        val retrans = after.retransSegs - before.retransSegs
        if (outSegs < MIN_SEGMENTS || retrans < 0 || retrans > outSegs) return null
        return retrans * 100.0 / outSegs
    }

    private val PATHS = listOf("/proc/self/net/snmp", "/proc/net/snmp")
    private const val MIN_SEGMENTS = 100L
}
