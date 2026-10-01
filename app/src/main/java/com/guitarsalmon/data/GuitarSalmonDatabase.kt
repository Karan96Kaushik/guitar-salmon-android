package com.guitarsalmon.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Session::class, ChordAttempt::class],
    version = 1,
    exportSchema = true,
)
abstract class GuitarSalmonDatabase : RoomDatabase() {

    abstract fun practiceDao(): PracticeDao

    companion object {
        private const val NAME = "guitarsalmon.db"

        @Volatile
        private var instance: GuitarSalmonDatabase? = null

        fun get(context: Context): GuitarSalmonDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): GuitarSalmonDatabase =
            Room.databaseBuilder(context, GuitarSalmonDatabase::class.java, NAME).build()
    }
}
