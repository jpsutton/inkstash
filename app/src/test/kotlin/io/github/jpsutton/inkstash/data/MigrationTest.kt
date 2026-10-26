package io.github.jpsutton.inkstash.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), InkstashDatabase::class.java)

    @Test fun v1ToV2KeepsEverythingAndAddsEmptyPrices() {
        helper.createDatabase("mig", 1).apply {
            execSQL("INSERT INTO Location (id, name, kind, notes, createdAt) VALUES (1, 'General', 'GENERAL', '', 0)")
            execSQL(
                "INSERT INTO Card (id, setCode, setName, collectorNumber, name, version, ink, inkable, cost, type, rarity) " +
                    "VALUES ('a', '1', 'Set', '1', 'Anna', 'v', 'Amber', 1, 2, 'Character', 'Rare')",
            )
            execSQL("INSERT INTO Holding (cardId, locationId, foil, quantity, sourceLocationId) VALUES ('a', 1, 0, 3, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig", 2, true, InkstashDatabase.MIGRATION_1_2)
        db.query("SELECT name, priceUsd, priceFoilUsd FROM Card").use {
            assertTrue(it.moveToFirst())
            assertEquals("Anna", it.getString(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
        }
        db.query("SELECT quantity FROM Holding").use {
            assertTrue(it.moveToFirst())
            assertEquals(3, it.getInt(0))
        }
    }

    @Test fun v2ToV3KeepsCopiesGivesPlacesUidsAndDropsTheCardLink() {
        helper.createDatabase("mig3", 2).apply {
            execSQL("INSERT INTO Location (id, name, kind, notes, createdAt) VALUES (1, 'General', 'GENERAL', '', 0)")
            execSQL("INSERT INTO Location (id, name, kind, notes, createdAt) VALUES (2, 'Trade', 'POOL', '', 5)")
            execSQL("INSERT INTO Location (id, name, kind, notes, createdAt) VALUES (3, 'Deck', 'TEMP_SET', '', 6)")
            execSQL(
                "INSERT INTO Card (id, setCode, setName, collectorNumber, name, version, ink, inkable, cost, type, rarity) " +
                    "VALUES ('a', '1', 'Set', '1', 'Anna', 'v', 'Amber', 1, 2, 'Character', 'Rare')",
            )
            execSQL("INSERT INTO Holding (cardId, locationId, foil, quantity, sourceLocationId) VALUES ('a', 1, 0, 3, 0)")
            execSQL("INSERT INTO Holding (cardId, locationId, foil, quantity, sourceLocationId) VALUES ('a', 3, 1, 2, 2)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig3", 3, true, InkstashDatabase.MIGRATION_2_3)
        val uids = mutableMapOf<Long, String>()
        db.query("SELECT id, uid, deleted FROM Location").use {
            while (it.moveToNext()) {
                uids[it.getLong(0)] = it.getString(1)
                assertEquals(0, it.getInt(2))
            }
        }
        assertEquals("general", uids[1])
        assertTrue(uids.values.all { it.isNotEmpty() })
        assertEquals(3, uids.values.toSet().size) // all different
        db.query("SELECT cardId, locationId, foil, quantity, sourceLocationId FROM Holding ORDER BY locationId").use {
            assertTrue(it.moveToFirst()); assertEquals(3, it.getInt(3))
            assertTrue(it.moveToNext()); assertEquals(2, it.getInt(3)); assertEquals(2, it.getInt(4))
        }
        // A copy of a card the card table doesn't know is fine now (no foreign key).
        db.execSQL("INSERT INTO Holding (cardId, locationId, foil, quantity, sourceLocationId) VALUES ('unknown', 1, 0, 1, 0)")
        db.query("SELECT COUNT(*) FROM SyncOp").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM MeshMember").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
    }

    @Test fun v1ToV3InOneGo() {
        helper.createDatabase("mig13", 1).close()
        helper.runMigrationsAndValidate("mig13", 3, true, InkstashDatabase.MIGRATION_1_2, InkstashDatabase.MIGRATION_2_3).close()
    }
}
