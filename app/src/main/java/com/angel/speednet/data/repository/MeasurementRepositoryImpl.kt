package com.angel.speednet.data.repository

import com.angel.speednet.data.local.dao.MeasurementDao
import com.angel.speednet.data.local.toDomain
import com.angel.speednet.data.local.toEntity
import com.angel.speednet.domain.model.Measurement
import com.angel.speednet.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeasurementRepositoryImpl(
    private val dao: MeasurementDao
) : MeasurementRepository {

    override suspend fun save(measurement: Measurement): Measurement {
        val id = dao.insert(measurement.toEntity())
        return measurement.copy(id = id)
    }

    override fun observeAll(): Flow<List<Measurement>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: Long): Measurement? = dao.getById(id)?.toDomain()

    override suspend fun deleteAll() = dao.deleteAll()
}
