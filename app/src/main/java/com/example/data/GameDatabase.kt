package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "player_profile")
data class PlayerProfile(
    @PrimaryKey val id: Int = 1,
    val username: String,
    val rankPoints: Int = 1200, // Starts at 1200 LP (Silver IV)
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0
)

@Entity(tableName = "match_history")
data class MatchRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opponentName: String,
    val opponentRank: String,
    val result: String, // "WIN", "DRAW", "LOSS"
    val lpChange: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface GameDao {
    @Query("SELECT * FROM player_profile WHERE id = 1 LIMIT 1")
    fun getPlayerProfile(): Flow<PlayerProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlayerProfile(profile: PlayerProfile)

    @Query("SELECT * FROM match_history ORDER BY timestamp DESC LIMIT 30")
    fun getMatchHistory(): Flow<List<MatchRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchRecord(record: MatchRecord)

    @Query("DELETE FROM match_history")
    suspend fun clearMatchHistory()
}

@Database(entities = [PlayerProfile::class, MatchRecord::class], version = 1, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "tic_tac_toe_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
