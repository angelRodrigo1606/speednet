package com.angel.speednet.domain.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.angel.speednet.data.network.TcpRetransmissionSampler
import com.angel.speednet.domain.model.Measurement
import com.angel.speednet.domain.model.TestStage
import com.angel.speednet.domain.scoring.ScoreCalculator
import com.angel.speednet.domain.scoring.VerdictCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Orquesta todas las etapas de la medición y emite progreso por etapa.
 */
class SpeedTestEngine(private val context: Context) {

    private val signalReader = SignalReader(context)
    private val locationProvider = LocationProvider(context)
    private val latencyMeasurer = LatencyMeasurer()
    private val throughputMeasurer = ThroughputMeasurer()

    suspend fun run(onProgress: (TestStage, Float) -> Unit): Measurement {
        // 1. Comprobación de red
        onProgress(TestStage.CHECKING_NETWORK, 0.1f)
        if (!hasNetwork()) {
            throw SpeedTestException("Sin conexión a Internet. Revisa tu red e inténtalo de nuevo.")
        }
        onProgress(TestStage.CHECKING_NETWORK, 1f)

        // 2. Señal y ubicación
        onProgress(TestStage.READING_SIGNAL, 0.2f)
        val signal = withContext(Dispatchers.IO) { signalReader.read() }
        val location = locationProvider.currentLocation()
        onProgress(TestStage.READING_SIGNAL, 1f)

        // 3. Latencia / jitter / pérdida (sin carga)
        onProgress(TestStage.MEASURING_LATENCY, 0f)
        val latency = latencyMeasurer.measure { progress ->
            onProgress(TestStage.MEASURING_LATENCY, progress)
        }

        // 4-5. Descarga y subida. En paralelo se siguen mandando sondas: la pérdida
        // a menudo solo aparece cuando el enlace está saturado, y el ping en reposo
        // se queda en 0%.
        val loadedLosses = AtomicInteger(0)
        val loadedAttempts = AtomicInteger(0)
        val tcpBefore = TcpRetransmissionSampler.read()

        val (downloadMbps, uploadMbps) = coroutineScope {
            val probeJob = launch {
                if (!latencyMeasurer.icmpAvailable && !latencyMeasurer.udpAvailable) return@launch
                val hosts = LatencyMeasurer.DEFAULT_HOSTS
                var index = 0
                while (isActive) {
                    val lost = try {
                        latencyMeasurer.probeLoaded(hosts[index % hosts.size])
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        break
                    } ?: break
                    if (!isActive) break
                    loadedAttempts.incrementAndGet()
                    if (lost) loadedLosses.incrementAndGet()
                    index++
                    delay(LOADED_PROBE_GAP_MS)
                }
            }
            try {
                onProgress(TestStage.DOWNLOADING, 0f)
                val down = throughputMeasurer.measureDownload { progress ->
                    onProgress(TestStage.DOWNLOADING, progress)
                }
                onProgress(TestStage.UPLOADING, 0f)
                val up = throughputMeasurer.measureUpload { progress ->
                    onProgress(TestStage.UPLOADING, progress)
                }
                down to up
            } finally {
                probeJob.cancelAndJoin()
            }
        }

        val tcpAfter = TcpRetransmissionSampler.read()
        val retransmissionPct = if (tcpBefore != null && tcpAfter != null) {
            TcpRetransmissionSampler.lossPct(tcpBefore, tcpAfter)
        } else {
            null
        }
        val packetLossPct = PacketLossMath.combine(
            probeLosses = latency.losses,
            probeAttempts = latency.attempts,
            extraLosses = loadedLosses.get(),
            extraAttempts = loadedAttempts.get(),
            retransmissionPct = retransmissionPct
        )

        // 6. Cálculo
        onProgress(TestStage.COMPUTING_SCORE, 0.5f)
        val score = ScoreCalculator.overallScore(
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            latencyMs = latency.latencyMs,
            jitterMs = latency.jitterMs,
            packetLossPct = packetLossPct
        )
        val verdicts = VerdictCalculator.verdicts(
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            latencyMs = latency.latencyMs,
            jitterMs = latency.jitterMs,
            packetLossPct = packetLossPct
        )
        onProgress(TestStage.COMPUTING_SCORE, 1f)

        val measurement = Measurement(
            timestamp = System.currentTimeMillis(),
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            latencyMs = latency.latencyMs,
            jitterMs = latency.jitterMs,
            packetLossPct = packetLossPct,
            score = score,
            level = ScoreCalculator.levelFor(score),
            network = signal,
            latitude = location?.first,
            longitude = location?.second,
            verdicts = verdicts
        )

        onProgress(TestStage.DONE, 1f)
        return measurement
    }

    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val LOADED_PROBE_GAP_MS = 200L
    }
}
