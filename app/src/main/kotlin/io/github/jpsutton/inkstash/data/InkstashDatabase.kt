package io.github.jpsutton.inkstash.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun kindToString(kind: LocationKind): String = kind.name

    @TypeConverter
    fun stringToKind(value: String): LocationKind = LocationKind.valueOf(value)
}

@Database(entities = [Card::class, Location::class, Holding::class], version = 2, exportSchema = true)
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

        /** v1 -> v2: estimated prices on cards. They stay null until the bundled snapshot or a sync fills them. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Card ADD COLUMN priceUsd REAL")
                db.execSQL("ALTER TABLE Card ADD COLUMN priceFoilUsd REAL")
            }
        }

        fun create(context: Context): InkstashDatabase =
            Room.databaseBuilder(context, InkstashDatabase::class.java, "inkstash.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(seedCallback)
                .build()
    }
}
