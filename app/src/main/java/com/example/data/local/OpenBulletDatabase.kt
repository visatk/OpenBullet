package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConfigEntity::class,
        WordlistEntity::class,
        ProxyEntity::class,
        HitDbEntity::class,
        SettingsEntity::class,
        RunnerSessionEntity::class
    ],
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'DARK'"
                )
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN useDynamicColor INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_proxies_status ON proxies (status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_proxies_protocol ON proxies (protocol)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_proxies_createdAt ON proxies (createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hits_db_type ON hits_db (type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hits_db_createdAt ON hits_db (createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_configs_createdAt ON configs (createdAt)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE proxies ADD COLUMN username TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE proxies ADD COLUMN password TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE configs ADD COLUMN version TEXT NOT NULL DEFAULT '1.0.0'"
                )
                db.execSQL(
                    "ALTER TABLE configs ADD COLUMN description TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE configs ADD COLUMN suggestedBots INTEGER NOT NULL DEFAULT 8"
                )
                db.execSQL(
                    "ALTER TABLE configs ADD COLUMN maxRetries INTEGER NOT NULL DEFAULT 3"
                )
                db.execSQL(
                    "ALTER TABLE configs ADD COLUMN timeoutSec INTEGER NOT NULL DEFAULT 10"
                )
                db.execSQL(
                    "ALTER TABLE configs ADD COLUMN customUserAgent TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE configs ADD COLUMN maxProxyUses INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): OpenBulletDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpenBulletDatabase::class.java,
                    "openbullet_workstation.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
