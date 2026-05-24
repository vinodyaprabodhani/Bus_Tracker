package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyDao {
    @Query("SELECT * FROM journeys ORDER BY timestamp DESC")
    fun getAllJourneys(): Flow<List<JourneyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJourney(journey: JourneyEntity)

    @Query("DELETE FROM journeys WHERE id = :id")
    suspend fun deleteJourneyById(id: Int)

    @Query("DELETE FROM journeys")
    suspend fun clearAll()
}
