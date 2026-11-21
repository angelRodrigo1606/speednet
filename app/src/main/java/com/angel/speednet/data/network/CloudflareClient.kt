package com.angel.speednet.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Cliente HTTP contra speed.cloudflare.com para medir throughput.
 *
 * - Descarga: GET __down?bytes=... en varias conexiones paralelas.
 * - Subida: POST __up con un cuerpo "chunked" que se cuenta MIENTRAS se envía.
 *
 * Detalles importantes:
 * - Se fuerza HTTP/1.1 para tener varias conexiones TCP reales (más fiable que
 *   multiplexar subidas en una sola conexión HTTP/2).
 * - La subida cuenta bytes a medida que los escribe, no al terminar la petición:
 *   así un timeout o corte en red móvil no deja la métrica en 0.
 * - Hay un tope de bytes para no consumir datos sin control.
 */
class CloudflareClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(false)
        .build()

    private val uploadChunk: ByteArray = ByteArray(UPLOAD_CHUNK_BYTES).also {
        Random().nextBytes(it)
    }

    suspend fun download(
        parallel: Int,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ): Double = measure(parallel, durationMs, DOWNLOAD_BYTE_CAP, onProgress) { deadline, cap, counter, stop ->
        downloadLoop(deadline, cap, counter, stop)
    }

    suspend fun upload(
        parallel: Int,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ): Double {
        val firstAttempt = measure(parallel, durationMs, UPLOAD_BYTE_CAP, onProgress) { deadline, cap, counter, stop ->
            uploadLoop(deadline, cap, counter, stop)
        }
        if (firstAttempt > 0.0) return firstAttempt

        // Si no se pudo enviar ni un byte (red móvil inestable, primera conexión
        // rechazada...), reintentamos una vez con menos conexiones.
        return measure(
            parallel = (parallel / 2).coerceAtLeast(1),
            durationMs = durationMs,
            byteCap = UPLOAD_BYTE_CAP,
            onProgress = onProgress
        ) { deadline, cap, counter, stop ->
            uploadLoop(deadline, cap, counter, stop)
        }
    }

    private suspend fun measure(
        parallel: Int,
        durationMs: Long,
        byteCap: Long,
        onProgress: (Float) -> Unit,
        worker: (Long, Long, AtomicLong, AtomicBoolean) -> Unit
    ): Double {
        val start = System.currentTimeMillis()
        val deadline = start + durationMs
        val counter = AtomicLong(0L)
        val stop = AtomicBoolean(false)
        val windowBytes = AtomicLong(0L)
        val endTime = AtomicLong(start)

        coroutineScope {
            repeat(parallel) {
                launch(Dispatchers.IO) { worker(deadline, byteCap, counter, stop) }
            }
            launch {
                while (!stop.get() && System.currentTimeMillis() < deadline && counter.get() < byteCap) {
                    val fraction = (System.currentTimeMillis() - start).toFloat() / durationMs
                    onProgress(fraction.coerceIn(0f, 1f))
                    delay(PROGRESS_INTERVAL_MS)
                }
                stop.set(true)
                windowBytes.set(counter.get())
                endTime.set(System.currentTimeMillis())
                onProgress(1f)
            }
        }

        val bytes = windowBytes.get()
        val elapsedSeconds = ((endTime.get() - start).coerceAtLeast(1L)) / 1000.0
        return bytes * 8.0 / elapsedSeconds / 1_000_000.0
    }

    private fun downloadLoop(
        deadline: Long,
        byteCap: Long,
        counter: AtomicLong,
        stop: AtomicBoolean
    ) {
        val buffer = Buffer()
        while (!stop.get() && System.currentTimeMillis() < deadline && counter.get() < byteCap) {
            try {
                val request = Request.Builder().url(DOWNLOAD_URL).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body ?: return
                    val source = body.source()
                    while (!stop.get() && System.currentTimeMillis() < deadline && counter.get() < byteCap) {
                        val read = source.read(buffer, READ_BUFFER_BYTES)
                        if (read == -1L) break
                        counter.addAndGet(read)
                        buffer.clear()
                    }
                }
            } catch (_: Exception) {
                return
            }
        }
    }

    private fun uploadLoop(
        deadline: Long,
        byteCap: Long,
        counter: AtomicLong,
        stop: AtomicBoolean
    ) {
        while (!stop.get() && System.currentTimeMillis() < deadline && counter.get() < byteCap) {
            val body = object : RequestBody() {
                override fun contentType(): MediaType = OCTET_STREAM

                // -1 => Transfer-Encoding: chunked. Permite cortar en el deadline
                // y finalizar el cuerpo de forma válida.
                override fun contentLength(): Long = -1L

                override fun writeTo(sink: BufferedSink) {
                    while (!stop.get() && System.currentTimeMillis() < deadline && counter.get() < byteCap) {
                        sink.write(uploadChunk)
                        counter.addAndGet(uploadChunk.size.toLong())
                    }
                    sink.flush()
                }
            }

            try {
                val request = Request.Builder()
                    .url(UPLOAD_URL)
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    response.body?.byteStream()?.readBytes()
                }
            } catch (_: Exception) {
                // Los bytes ya escritos quedaron contados: no perdemos la muestra.
                return
            }
        }
    }

    companion object {
        private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=25000000"
        private const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
        private const val READ_BUFFER_BYTES = 64L * 1024L
        private const val UPLOAD_CHUNK_BYTES = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 150L

        // Topes de datos (agregados) para no consumir el plan sin control.
        private const val DOWNLOAD_BYTE_CAP = 40L * 1024L * 1024L
        private const val UPLOAD_BYTE_CAP = 20L * 1024L * 1024L

        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
