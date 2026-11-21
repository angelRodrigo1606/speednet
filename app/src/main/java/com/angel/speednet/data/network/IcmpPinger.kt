package com.angel.speednet.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Ping ICMP con el binario del sistema. No requiere root: en Android
 * `/system/bin/ping` es quien abre el socket ICMP.
 *
 * Devuelve null si el dispositivo no deja ejecutar ping (muy habitual en
 * Android 12+). En ese caso hay que medir la pérdida por otro medio.
 */
class IcmpPinger {

    data class Probe(val rttMs: Double?, val lost: Boolean)

    @Volatile
    private var unavailable = false

    private var binary: String? = null
    private var useNumeric = true
    private val candidates = mutableListOf("/system/bin/ping", "/system/xbin/ping", "ping")

    suspend fun probe(host: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Probe? = withContext(Dispatchers.IO) {
        runInterruptible {
            probeBlocking(host, timeoutMs)
        }
    }

    private fun probeBlocking(host: String, timeoutMs: Int): Probe? {
        if (unavailable) return null
        val timeoutSec = ((timeoutMs + 999) / 1000).coerceAtLeast(1)
        var execution = execute(timeoutSec, host, timeoutMs) ?: return null
        if (useNumeric && PingOutputParser.isInvalidOption(execution.output)) {
            useNumeric = false
            execution = execute(timeoutSec, host, timeoutMs) ?: return null
        }
        return when (val interpreted = PingOutputParser.interpret(execution.exitCode, execution.output)) {
            PingOutputParser.Interpreted.Unusable -> {
                unavailable = true
                null
            }
            is PingOutputParser.Interpreted.Sample -> Probe(interpreted.rttMs, interpreted.lost)
        }
    }

    private fun execute(timeoutSec: Int, host: String, timeoutMs: Int): Execution? {
        while (!unavailable) {
            val bin = binary ?: nextCandidate() ?: return null
            try {
                return runCommand(command(bin, timeoutSec, host), timeoutMs)
            } catch (_: IOException) {
                binary = null
            }
        }
        return null
    }

    private fun nextCandidate(): String? {
        if (unavailable) return null
        binary?.let { return it }
        if (candidates.isEmpty()) {
            unavailable = true
            return null
        }
        val candidate = candidates.removeAt(0)
        binary = candidate
        return candidate
    }

    private fun command(bin: String, timeoutSec: Int, host: String): List<String> {
        val args = mutableListOf(bin, "-c", "1", "-W", timeoutSec.toString())
        if (useNumeric) args += "-n"
        args += host
        return args
    }

    private fun runCommand(command: List<String>, timeoutMs: Int): Execution {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { reader ->
                    val buf = CharArray(512)
                    while (true) {
                        val read = reader.read(buf)
                        if (read < 0) break
                        output.append(buf, 0, read)
                    }
                }
            }
        }
        reader.isDaemon = true
        reader.start()
        try {
            val finished = process.waitFor(timeoutMs.toLong() + PROCESS_GRACE_MS, TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()
            reader.join(READER_JOIN_MS)
            val exit = if (finished) runCatching { process.exitValue() }.getOrDefault(1) else 1
            return Execution(exit, output.toString())
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            throw interrupted
        }
    }

    private data class Execution(val exitCode: Int, val output: String)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2_000
        private const val PROCESS_GRACE_MS = 1_500L
        private const val READER_JOIN_MS = 300L
    }
}
