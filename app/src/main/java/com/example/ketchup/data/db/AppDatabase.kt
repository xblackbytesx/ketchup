package com.example.ketchup.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ArticleEntity::class, FeedEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun feedDao(): FeedDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `feeds` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `feedUrl` TEXT NOT NULL, `siteUrl` TEXT NOT NULL, `faviconUrl` TEXT, `categoryLabel` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `articles` ADD COLUMN `isStarred` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceFaviconUrl` TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_feedId` ON `articles` (`feedId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_feedId_isRead` ON `articles` (`feedId`, `isRead`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_publishedMs` ON `articles` (`publishedMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_isStarred` ON `articles` (`isStarred`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `isTitleCustomized` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `etag` TEXT")
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `lastModified` TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ketchup.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { INSTANCE = it }
            }
        }
    }
}
