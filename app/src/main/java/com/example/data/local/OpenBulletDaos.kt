package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class ProxyCheckUpdate(
    val id: String,
    val status: String,
    val pingMs: Int?,
    val country: String
)

@Dao
interface ConfigDao {
    @Query("SELECT * FROM configs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ConfigEntity>)

    @Query("DELETE FROM configs WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface WordlistDao {
    @Query("SELECT * FROM wordlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WordlistEntity>>

    @Query("SELECT * FROM wordlists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WordlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WordlistEntity)

    @Query("DELETE FROM wordlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM wordlists")
    suspend fun clearAll()
}

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies ORDER BY createdAt DESC")
    suspend fun getAllSnapshot(): List<ProxyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ProxyEntity>)

    @Query("UPDATE proxies SET status = :status, pingMs = :pingMs, country = :country WHERE id = :id")
    suspend fun updateCheckResult(id: String, status: String, pingMs: Int?, country: String)

    @Transaction
    suspend fun updateCheckResultsBatch(updates: List<ProxyCheckUpdate>) {
        for (update in updates) {
            updateCheckResult(
                id = update.id,
                status = update.status,
                pingMs = update.pingMs,
                country = update.country
            )
        }
    }

    @Query("DELETE FROM proxies WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM proxies WHERE status IN ('BAD', 'BANNED')")
    suspend fun deleteBadAndBanned()

    @Query("DELETE FROM proxies")
    suspend fun clearAll()
}

@Dao
interface HitDbDao {
    @Query("SELECT * FROM hits_db ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HitDbEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HitDbEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HitDbEntity>)

    @Query("DELETE FROM hits_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM hits_db")
    suspend fun clearAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 0 LIMIT 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 0 LIMIT 1")
    suspend fun getSnapshot(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SettingsEntity)
}

@Dao
interface RunnerSessionDao {
    @Query("SELECT * FROM runner_session WHERE id = 0 LIMIT 1")
    fun observe(): Flow<RunnerSessionEntity?>

    @Query("SELECT * FROM runner_session WHERE id = 0 LIMIT 1")
    suspend fun getSnapshot(): RunnerSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RunnerSessionEntity)

    @Query("DELETE FROM runner_session")
    suspend fun clearSession()
}
