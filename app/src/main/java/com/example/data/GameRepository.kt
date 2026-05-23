package com.example.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val gameDao: GameDao) {
    val playerProfile: Flow<PlayerProfile?> = gameDao.getPlayerProfile()
    val matchHistory: Flow<List<MatchRecord>> = gameDao.getMatchHistory()

    suspend fun saveProfile(profile: PlayerProfile) {
        gameDao.savePlayerProfile(profile)
    }

    suspend fun addMatchRecord(record: MatchRecord) {
        gameDao.insertMatchRecord(record)
    }

    suspend fun clearHistory() {
        gameDao.clearMatchHistory()
    }
}
