package com.angel.speednet.data.network

/**
 * Interpreta la salida de `ping` (toybox, iputils o busybox) sin depender del idioma
 * más de lo necesario: la pérdida se saca de los contadores, no del texto "% packet loss".
 */
object PingOutputParser {

    data class Parsed(
        val rttsMs: List<Double>,
        val transmitted: Int?,
        val received: Int?
    )

    sealed interface Interpreted {
        data object Unusable : Interpreted
        data class Sample(val rttMs: Double?, val lost: Boolean) : Interpreted
    }

    private val rttRegex = Regex("""time[=<]\s*(\d+(?:\.\d+)?)\s*ms""", RegexOption.IGNORE_CASE)
    private val statsRttRegex = Regex(
        """(?:rtt|round-trip)\s+min/avg/max(?:/\w+)?\s*=\s*(\d+(?:\.\d+)?)/(\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )
    private val summaryEn = Regex(
        """(\d+)\s+packets?\s+transmitted.*?(\d+)\s+(?:packets?\s+)?received""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val summaryEs = Regex(
        """(\d+)\s+paquetes\s+transmitidos.*?(\d+)\s+recibidos""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val invalidOption = Regex(
        """invalid option|unknown option|unrecognized option|bad option""",
        RegexOption.IGNORE_CASE
    )
    private val unusable = Regex(
        """operation not permitted|permission denied|operation not supported|not found|unknown host|bad address|name or service not known""",
        RegexOption.IGNORE_CASE
    )

    fun parse(output: String): Parsed {
        val rtts = output.lineSequence()
            .filterNot { it.contains("DUP!", ignoreCase = true) }
            .mapNotNull { line -> rttRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull() }
            .toList()
        val summary = summaryEn.find(output) ?: summaryEs.find(output)
        return Parsed(
            rttsMs = rtts,
            transmitted = summary?.groupValues?.get(1)?.toIntOrNull(),
            received = summary?.groupValues?.get(2)?.toIntOrNull()
        )
    }

    fun isInvalidOption(output: String): Boolean = invalidOption.containsMatchIn(output)

    fun interpret(exitCode: Int, output: String): Interpreted {
        if (unusable.containsMatchIn(output)) return Interpreted.Unusable
        val parsed = parse(output)
        if (parsed.received != null) {
            val lost = parsed.received == 0
            val rtt = parsed.rttsMs.firstOrNull() ?: statsRttRegex.find(output)
                ?.groupValues
                ?.get(2)
                ?.toDoubleOrNull()
            return Interpreted.Sample(rttMs = if (lost) null else rtt, lost = lost)
        }
        if (parsed.rttsMs.isNotEmpty()) {
            return Interpreted.Sample(rttMs = parsed.rttsMs.first(), lost = false)
        }
        // exit 2 sin estadísticas: el binario no pudo enviar (permiso, opción, etc.).
        // Salida vacía con otro exit es un timeout: pérdida, no un ping inutilizable.
        if (exitCode == 2) return Interpreted.Unusable
        return Interpreted.Sample(rttMs = null, lost = true)
    }
}
