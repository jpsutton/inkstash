package io.github.jpsutton.inkstash.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionRepositoryTest : DbTestBase() {
    @Test fun generalIsSeeded() = runBlocking {
        val general = db.locations().byId(GENERAL_ID)!!
        assertEquals(LocationKind.GENERAL, general.kind)
    }

    @Test fun addMergesIdenticalRows() = runBlocking {
        repo.add("a", false, 2, GENERAL_ID)
        repo.add("a", false, 1, GENERAL_ID)
        repo.add("a", true, 1, GENERAL_ID)
        assertEquals(3, qty("a", GENERAL_ID) - 1)
        assertEquals(2, db.holdings().inLocation(GENERAL_ID).size) // normal + foil
        assertEquals(4, total())
    }

    @Test fun removeDeletesEmptyRows() = runBlocking {
        repo.add("a", false, 2, GENERAL_ID)
        val id = db.holdings().inLocation(GENERAL_ID).single().id
        repo.remove(id, 1)
        assertEquals(1, total())
        repo.remove(id, 5)
        assertEquals(0, total())
        assertTrue(db.holdings().all().isEmpty())
    }

    @Test fun moveConservesCopiesAndSplitsRows() = runBlocking {
        val pool = repo.createLocation("Trade", LocationKind.POOL)
        repo.add("a", false, 3, GENERAL_ID)
        repo.move(db.holdings().inLocation(GENERAL_ID).single().id, 2, pool)
        assertEquals(1, qty("a", GENERAL_ID))
        assertEquals(2, qty("a", pool))
        assertEquals(3, total())
        assertEquals(Holding.NO_SOURCE, db.holdings().inLocation(pool).single().sourceLocationId)
    }

    @Test fun movingIntoTempSetRecordsTheSource() = runBlocking {
        val pool = repo.createLocation("Trade", LocationKind.POOL)
        val temp = repo.createLocation("Deck", LocationKind.TEMP_SET)
        repo.add("a", false, 1, pool)
        repo.add("a", false, 1, GENERAL_ID)
        repo.move(db.holdings().inLocation(pool).single().id, 1, temp)
        repo.move(db.holdings().inLocation(GENERAL_ID).single().id, 1, temp)
        val rows = db.holdings().inLocation(temp).sortedBy { it.sourceLocationId }
        assertEquals(listOf(GENERAL_ID, pool), rows.map { it.sourceLocationId })
        assertEquals(2, total())
    }

    @Test fun movingBetweenTempSetsKeepsTheOriginalSource() = runBlocking {
        val t1 = repo.createLocation("Deck 1", LocationKind.TEMP_SET)
        val t2 = repo.createLocation("Deck 2", LocationKind.TEMP_SET)
        repo.add("a", false, 1, GENERAL_ID)
        repo.move(db.holdings().inLocation(GENERAL_ID).single().id, 1, t1)
        repo.move(db.holdings().inLocation(t1).single().id, 1, t2)
        assertEquals(GENERAL_ID, db.holdings().inLocation(t2).single().sourceLocationId)
    }

    @Test fun moveToSameLocationIsANoOp() = runBlocking {
        repo.add("a", false, 2, GENERAL_ID)
        repo.move(db.holdings().inLocation(GENERAL_ID).single().id, 1, GENERAL_ID)
        assertEquals(2, qty("a", GENERAL_ID))
    }

    @Test fun dissolveReturnsEveryCopyHome() = runBlocking {
        val pool = repo.createLocation("Trade", LocationKind.POOL)
        val temp = repo.createLocation("Deck", LocationKind.TEMP_SET)
        repo.add("a", false, 2, GENERAL_ID)
        repo.add("b", true, 1, pool)
        repo.move(db.holdings().inLocation(GENERAL_ID).single().id, 2, temp)
        repo.move(db.holdings().inLocation(pool).single().id, 1, temp)
        val before = total()
        repo.dissolveTempSet(temp)
        assertEquals(before, total())
        assertEquals(2, qty("a", GENERAL_ID))
        assertEquals(1, qty("b", pool))
        assertNull(db.locations().byId(temp))
        assertTrue(db.holdings().all().none { it.sourceLocationId != Holding.NO_SOURCE })
    }

    @Test fun dissolveFallsBackToGeneralWhenSourceIsGone() = runBlocking {
        val pool = repo.createLocation("Trade", LocationKind.POOL)
        val temp = repo.createLocation("Deck", LocationKind.TEMP_SET)
        repo.add("b", false, 1, pool)
        repo.move(db.holdings().inLocation(pool).single().id, 1, temp)
        repo.deleteLocation(pool)
        repo.dissolveTempSet(temp)
        assertEquals(1, qty("b", GENERAL_ID))
        assertEquals(1, total())
    }

    @Test fun deletingAPoolMovesItsCardsToGeneral() = runBlocking {
        val pool = repo.createLocation("Trade", LocationKind.POOL)
        repo.add("a", false, 2, pool)
        repo.add("a", false, 1, GENERAL_ID)
        repo.deleteLocation(pool)
        assertEquals(3, qty("a", GENERAL_ID))
        assertEquals(1, db.holdings().inLocation(GENERAL_ID).size)
        assertNull(db.locations().byId(pool))
        assertEquals(3, total())
    }

    @Test fun deletingAPoolRepointsTempSetsThatBorrowedFromIt() = runBlocking {
        val pool = repo.createLocation("Trade", LocationKind.POOL)
        val temp = repo.createLocation("Deck", LocationKind.TEMP_SET)
        repo.add("a", false, 1, pool)
        repo.move(db.holdings().inLocation(pool).single().id, 1, temp)
        repo.deleteLocation(pool)
        assertEquals(GENERAL_ID, db.holdings().inLocation(temp).single().sourceLocationId)
        assertEquals(1, total())
    }

    @Test fun deletingATempSetDissolvesIt() = runBlocking {
        val temp = repo.createLocation("Deck", LocationKind.TEMP_SET)
        repo.add("a", false, 1, GENERAL_ID)
        repo.move(db.holdings().inLocation(GENERAL_ID).single().id, 1, temp)
        repo.deleteLocation(temp)
        assertEquals(1, qty("a", GENERAL_ID))
        assertNull(db.locations().byId(temp))
    }

    @Test fun generalCannotBeDeletedOrRenamed() = runBlocking {
        repo.deleteLocation(GENERAL_ID)
        repo.rename(GENERAL_ID, "Mine")
        assertEquals("General", db.locations().byId(GENERAL_ID)!!.name)
    }

    @Test fun cannotCreateASecondGeneral() {
        val failed = runCatching { runBlocking { repo.createLocation("Another", LocationKind.GENERAL) } }.isFailure
        assertTrue(failed)
    }

    @Test fun borrowOnePrefersGeneralAndLeavesTempSetsAlone() = runBlocking {
        val pool = repo.createLocation("Trade", LocationKind.POOL)
        val t1 = repo.createLocation("Deck 1", LocationKind.TEMP_SET)
        val t2 = repo.createLocation("Deck 2", LocationKind.TEMP_SET)
        repo.add("a", false, 1, GENERAL_ID)
        repo.add("a", false, 1, pool)
        assertTrue(repo.borrowOne("a", t1))
        assertEquals(GENERAL_ID, db.holdings().inLocation(t1).single().sourceLocationId)
        assertTrue(repo.borrowOne("a", t2))
        assertEquals(pool, db.holdings().inLocation(t2).single().sourceLocationId)
        assertFalse("both copies are in decks now", repo.borrowOne("a", t1))
        assertEquals(2, total())
    }

    @Test fun returnOneSendsACopyHome() = runBlocking {
        val temp = repo.createLocation("Deck", LocationKind.TEMP_SET)
        repo.add("a", false, 2, GENERAL_ID)
        repo.move(db.holdings().inLocation(GENERAL_ID).single().id, 2, temp)
        repo.returnOne(db.holdings().inLocation(temp).single().id)
        assertEquals(1, qty("a", temp))
        assertEquals(1, qty("a", GENERAL_ID))
    }

    @Test fun ownedCardsGroupAcrossLocations() = runBlocking {
        val pool = repo.createLocation("Trade", LocationKind.POOL)
        repo.add("a", false, 2, GENERAL_ID)
        repo.add("a", true, 1, pool)
        repo.add("b", false, 1, GENERAL_ID)
        val all = db.holdings().observeOwned(-1).first()
        assertEquals(listOf("Anna" to 3, "Belle" to 1), all.map { it.card.name to it.qty })
        assertEquals(1, all.first().foilQty)
        val poolOnly = db.holdings().observeOwned(pool).first()
        assertEquals(listOf("a"), poolOnly.map { it.card.id })
        assertNotNull(poolOnly.single())
    }
}
