package io.github.jpsutton.inkstash.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest : DbTestBase() {
    @Test fun exportThenImportRoundTrips() = runBlocking {
        val pool = repo.createLocation("Trade \"binder\" ✨", LocationKind.POOL)
        val temp = repo.createLocation("Deck", LocationKind.TEMP_SET)
        repo.add("a", false, 2, GENERAL_ID)
        repo.add("b", true, 3, pool)
        repo.move(db.holdings().inLocation(pool).single().id, 1, temp)
        val before = repo.snapshot()
        val json = Backup.encode(before, 123)

        // Wipe, then restore into a fresh state.
        repo.restore(BackupData(emptyList(), emptyList()))
        assertEquals(0, total())

        val skipped = repo.restore(Backup.decode(json))
        assertEquals(0, skipped)
        val after = repo.snapshot()
        fun norm(d: BackupData) = d.locations.map { Triple(it.id, it.name, it.kind) }.sortedBy { it.first } to
            d.holdings.map { listOf(it.cardId, it.locationId, it.foil, it.quantity, it.sourceLocationId) }.sortedBy { it.toString() }
        assertEquals(norm(before), norm(after))
        assertEquals(5, total())
    }

    @Test fun restoreReplacesEverything() = runBlocking {
        repo.add("c", false, 9, GENERAL_ID)
        val other = BackupData(emptyList(), listOf(Holding(cardId = "a", locationId = GENERAL_ID, foil = false, quantity = 1)))
        repo.restore(other)
        assertEquals(1, total())
        assertEquals(0, qty("c", GENERAL_ID))
    }

    @Test fun unknownCardsAreSkippedAndCounted() = runBlocking {
        val data = BackupData(
            emptyList(),
            listOf(
                Holding(cardId = "a", locationId = GENERAL_ID, foil = false, quantity = 1),
                Holding(cardId = "nope", locationId = GENERAL_ID, foil = false, quantity = 4),
            ),
        )
        assertEquals(4, repo.restore(data))
        assertEquals(1, total())
    }

    @Test fun notABackupIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("{\"hello\": 1}") }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("not json") }
    }

    @Test fun newerVersionIsRejected() {
        val text = Backup.encode(BackupData(emptyList(), emptyList()), 0).replace("\"version\": 1", "\"version\": 2")
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(text) }
    }
}
