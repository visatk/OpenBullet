package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConfigEntity::class,
        WordlistEntity::class,
        ProxyEntity::class,
        HitDbEntity::class,
        SettingsEntity::class,
        RunnerSessionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OpenBulletDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun wordlistDao(): WordlistDao
    abstract fun proxyDao(): ProxyDao
    abstract fun hitDbDao(): HitDbDao
    abstract fun settingsDao(): SettingsDao
    abstract fun runnerSessionDao(): RunnerSessionDao

    companion object {
        @Volatile
        private var instance: OpenBulletDatabase? = null

        fun getInstance(context: Context): OpenBulletDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpenBulletDatabase::class.java,
                    "openbullet_workstation.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
