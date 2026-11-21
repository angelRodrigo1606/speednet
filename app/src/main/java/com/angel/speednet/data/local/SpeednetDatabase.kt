package com.angel.speednet.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.angel.speednet.data.local.dao.MeasurementDao
import com.angel.speednet.data.local.entity.MeasurementEntity

@Database(entities = [MeasurementEntity::class], version = 1, exportSchema = false)
abstract class SpeednetDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
}
