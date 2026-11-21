package com.angel.speednet.domain.repository

import com.angel.speednet.domain.model.Measurement
import kotlinx.coroutines.flow.Flow

interface MeasurementRepository {
    suspend fun save(measurement: Measurement): Measurement
    fun observeAll(): Flow<List<Measurement>>
    suspend fun getById(id: Long): Measurement?
    suspend fun deleteAll()
}
