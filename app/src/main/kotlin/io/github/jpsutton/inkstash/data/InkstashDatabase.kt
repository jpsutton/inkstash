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

@Database(
    entities = [Card::class, Location::class, Holding::class, SyncOp::class, MeshMember::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class InkstashDatabase : RoomDatabase() {
    abstract fun cards(): CardDao
    abstract fun locations(): LocationDao
    abstract fun holdings(): HoldingDao
    abstract fun syncOps(): SyncOpDao
    abstract fun meshMembers(): MeshMemberDao

    companion object {
        /** Seeds the one GENERAL location. */
        val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT INTO Location (id, name, kind, notes, createdAt, uid, deleted, nameHlc, nameOrigin) " +
                        "VALUES ($GENERAL_ID, 'General', 'GENERAL', '', 0, '$GENERAL_UID', 0, 0, '')",
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

        /**
         * v2 -> v3: places get a uid that is the same on every device (General's is "general") and a deleted flag;
         * copies lose their link to the card table (a card the database doesn't know yet still counts); and
         * the operation log and mesh membership tables arrive.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Location ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Location ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Location ADD COLUMN nameHlc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Location ADD COLUMN nameOrigin TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE Location SET uid = CASE WHEN id = $GENERAL_ID THEN '$GENERAL_UID' ELSE lower(hex(randomblob(16))) END")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Location_uid` ON `Location` (`uid`)")

                db.execSQL(
                    "CREATE TABLE `Holding_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardId` TEXT NOT NULL, " +
                        "`locationId` INTEGER NOT NULL, `foil` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, " +
                        "`sourceLocationId` INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(`locationId`) REFERENCES `Location`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                )
                db.execSQL("INSERT INTO Holding_new SELECT id, cardId, locationId, foil, quantity, sourceLocationId FROM Holding")
                db.execSQL("DROP TABLE Holding")
                db.execSQL("ALTER TABLE Holding_new RENAME TO Holding")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Holding_cardId_locationId_foil_sourceLocationId` ON `Holding` (`cardId`, `locationId`, `foil`, `sourceLocationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Holding_locationId` ON `Holding` (`locationId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `SyncOp` (`origin` TEXT NOT NULL, `seq` INTEGER NOT NULL, `hlc` INTEGER NOT NULL, " +
                        "`payload` TEXT NOT NULL, `applied` INTEGER NOT NULL, PRIMARY KEY(`origin`, `seq`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_SyncOp_applied` ON `SyncOp` (`applied`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `MeshMember` (`nodeId` TEXT NOT NULL, `name` TEXT NOT NULL, `noiseKey` TEXT NOT NULL, " +
                        "`signKey` TEXT NOT NULL, `admittedBy` TEXT NOT NULL, `admittedAt` INTEGER NOT NULL, `signature` TEXT NOT NULL, " +
                        "`removedBy` TEXT, `removedAt` INTEGER, `removalSignature` TEXT, `addresses` TEXT NOT NULL, PRIMARY KEY(`nodeId`))",
                )
            }
        }

        fun create(context: Context): InkstashDatabase =
            Room.databaseBuilder(context, InkstashDatabase::class.java, "inkstash.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(seedCallback)
                .build()
    }
}
