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
}
