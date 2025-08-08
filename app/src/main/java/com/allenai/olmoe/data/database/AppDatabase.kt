package com.allenai.olmoe.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.allenai.olmoe.data.dao.ChatDao
import com.allenai.olmoe.data.model.Chat

@Database(
    entities = [Chat::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun chatDao(): ChatDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "olmoe_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
} 