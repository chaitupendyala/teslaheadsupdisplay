package com.chaitalkstech.teslaheadsupdisplay

import android.content.Context
import androidx.room.*

@Entity(tableName = "road_data")
data class RoadData(
    @PrimaryKey val placeId: String,
    val speedLimit: String?,
    val roadName: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "api_logs")
data class ApiLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val responseCode: Int? = null,
    val error: String? = null
)

@Dao
interface RoadDataDao {
    @Query("SELECT * FROM road_data WHERE placeId = :placeId")
    suspend fun getRoadData(placeId: String): RoadData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoadData(roadData: RoadData)

    @Query("DELETE FROM road_data")
    suspend fun clearAll()
}

@Dao
interface ApiLogDao {
    @Insert
    suspend fun insertLog(log: ApiLog)

    @Query("SELECT * FROM api_logs ORDER BY timestamp DESC LIMIT 500")
    suspend fun getRecentLogs(): List<ApiLog>

    @Query("DELETE FROM api_logs")
    suspend fun clearLogs()
}

@Database(entities = [RoadData::class, ApiLog::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roadDataDao(): RoadDataDao
    abstract fun apiLogDao(): ApiLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tesla_hud_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
