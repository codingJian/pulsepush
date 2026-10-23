package com.shing.pulsepush.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LocalMessage::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
