package io.github.jpsutton.inkstash.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun kindToString(kind: LocationKind): String = kind.name

    @TypeConverter
    fun stringToKind(value: String): LocationKind = LocationKind.valueOf(value)
}

@Database(entities = [Card::class, Location::class, Holding::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class InkstashDatabase : RoomDatabase() {
    abstract fun cards(): CardDao
    abstract fun locations(): LocationDao
    abstract fun holdings(): HoldingDao

    companion object {
        /** Seeds the one GENERAL location. */
        val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT INTO Location (id, name, kind, notes, createdAt) " +
                        "VALUES ($GENERAL_ID, 'General', 'GENERAL', '', 0)",
                )
            }
        }

        fun create(context: Context): InkstashDatabase =
            Room.databaseBuilder(context, InkstashDatabase::class.java, "inkstash.db")
                .addCallback(seedCallback)
                .build()
    }
}
