package com.angel.speednet.domain.engine

import com.angel.speednet.data.network.CloudflareClient

class ThroughputMeasurer(
    private val client: CloudflareClient = CloudflareClient()
) {

    suspend fun measureDownload(onProgress: (Float) -> Unit): Double =
        client.download(parallel = DOWNLOAD_PARALLEL, durationMs = DOWNLOAD_DURATION_MS, onProgress = onProgress)

    suspend fun measureUpload(onProgress: (Float) -> Unit): Double =
        client.upload(parallel = UPLOAD_PARALLEL, durationMs = UPLOAD_DURATION_MS, onProgress = onProgress)

    companion object {
        private const val DOWNLOAD_PARALLEL = 6
        private const val DOWNLOAD_DURATION_MS = 8_000L
        private const val UPLOAD_PARALLEL = 4
        private const val UPLOAD_DURATION_MS = 6_000L
    }
}
