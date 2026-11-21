package com.angel.speednet.di

import android.content.Context
import androidx.room.Room
import com.angel.speednet.data.local.SpeednetDatabase
import com.angel.speednet.data.repository.MeasurementRepositoryImpl
import com.angel.speednet.data.repository.SpeedTestRepositoryImpl
import com.angel.speednet.domain.engine.SpeedTestEngine
import com.angel.speednet.domain.repository.MeasurementRepository
import com.angel.speednet.domain.repository.SpeedTestRepository

/**
 * Contenedor de dependencias manual (sin Hilt).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: SpeednetDatabase by lazy {
        Room.databaseBuilder(appContext, SpeednetDatabase::class.java, "speednet.db").build()
    }

    val measurementRepository: MeasurementRepository by lazy {
        MeasurementRepositoryImpl(database.measurementDao())
    }

    private val engine: SpeedTestEngine by lazy {
        SpeedTestEngine(appContext)
    }

    val speedTestRepository: SpeedTestRepository by lazy {
        SpeedTestRepositoryImpl(engine, measurementRepository)
    }
}
