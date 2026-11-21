package com.angel.speednet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.angel.speednet.data.local.entity.MeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MeasurementEntity): Long

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MeasurementEntity?

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()
}
