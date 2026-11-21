package com.angel.speednet.domain.repository

import com.angel.speednet.domain.model.Measurement
import com.angel.speednet.domain.model.TestStage

interface SpeedTestRepository {
    /**
     * Ejecuta una medición completa, reportando el progreso por etapa.
     * Devuelve la medición ya persistida (con id asignado).
     */
    suspend fun runTest(onProgress: (TestStage, Float) -> Unit): Measurement
}
