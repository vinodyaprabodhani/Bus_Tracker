package com.example.data

import kotlinx.coroutines.flow.Flow

class JourneyRepository(private val journeyDao: JourneyDao) {
    val allJourneys: Flow<List<JourneyEntity>> = journeyDao.getAllJourneys()

    suspend fun insert(journey: JourneyEntity) {
        journeyDao.insertJourney(journey)
    }

    suspend fun delete(id: Int) {
        journeyDao.deleteJourneyById(id)
    }

    suspend fun clearAll() {
        journeyDao.clearAll()
    }
}
