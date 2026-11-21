package com.angel.speednet.data.repository

import com.angel.speednet.domain.engine.SpeedTestEngine
import com.angel.speednet.domain.model.Measurement
import com.angel.speednet.domain.model.TestStage
import com.angel.speednet.domain.repository.MeasurementRepository
import com.angel.speednet.domain.repository.SpeedTestRepository

class SpeedTestRepositoryImpl(
    private val engine: SpeedTestEngine,
    private val measurementRepository: MeasurementRepository
) : SpeedTestRepository {

    override suspend fun runTest(onProgress: (TestStage, Float) -> Unit): Measurement {
        val measurement = engine.run(onProgress)
        return measurementRepository.save(measurement)
    }
}
