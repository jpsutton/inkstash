package io.github.jpsutton.inkstash.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Random

/** Several devices with their own databases, exchanging operations the way the mesh does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncStoreTest {
    private class Device(val name: String, val db: InkstashDatabase, val repo: CollectionRepository, val sync: SyncStore)

    private val devices = mutableListOf<Device>()
    private var tick = 1_000L

    private fun device(name: String): Device {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, InkstashDatabase::class.java)
            .addCallback(InkstashDatabase.seedCallback).allowMainThreadQueries().build()
        val sync = SyncStore(db, name, clock = { tick++ })
        val d = Device(name, db, CollectionRepository(db, sync, now = { tick }), sync)
        runBlocking {
            db.cards().upsertAll(listOf("a", "b", "c").map {
                Card(it, "T", "Set", it, it.uppercase(), "v", "Amber", true, 1, "Character", "Rare", null, null, null)
            })
        }
        devices += d
        return d
    }

    @Before fun noop() {}
    @After fun close() = devices.forEach { it.db.close() }

    /** One way: what [to] is missing from [from]. */
    private fun pull(from: Device, to: Device) = runBlocking {
        while (true) {
            val batch = from.sync.missingFor(to.sync.vector(), limit = 7)
            if (batch.isEmpty() || to.sync.receive(batch) == 0) break
        }
    }

    private fun syncAll() {
        repeat(2) { for (a in devices) for (b in devices) if (a !== b) pull(a, b) }
    }

    /** The collection as a device sees it, independent of local ids. */
    private fun view(d: Device): Pair<List<String>, List<String>> = runBlocking {
        val locs = d.db.locations().all()
        val names = locs.associate { it.id to it.uid }
        locs.map { "${it.uid}|${it.name}|${it.kind}" }.sorted() to
            d.db.holdings().all().map { "${it.cardId}|${names[it.locationId]}|${it.foil}|${it.quantity}|${names[it.sourceLocationId] ?: ""}" }.sorted()
    }

    private fun assertConverged() {
        syncAll()
        val first = view(devices.first())
        for (d in devices.drop(1)) assertEquals("${d.name} differs", first, view(d))
    }

    private fun holdingId(d: Device, card: String, loc: Long) =
        runBlocking { d.db.holdings().inLocation(loc).first { it.cardId == card }.id }

    @Test fun aSecondDeviceGetsTheWholeCollection() = runBlocking {
        val a = device("a")
        val b = device("b")
        val pool = a.repo.createLocation("Trade", LocationKind.POOL)
        a.repo.add("a", false, 3, GENERAL_ID)
        a.repo.add("b", true, 1, pool)
        assertConverged()
        assertEquals(4, b.db.holdings().totalCopies())
        assertEquals(listOf("General", "Trade"), b.db.locations().all().map { it.name }.sorted())
    }

    @Test fun twoDevicesAddingTheSameCardBothCount() = runBlocking {
        val a = device("a")
        val b = device("b")
        a.repo.add("a", false, 2, GENERAL_ID)
        b.repo.add("a", false, 3, GENERAL_ID)
        assertConverged()
        assertEquals(5, a.db.holdings().totalCopies())
        assertEquals(5, b.db.holdings().totalCopies())
    }

    @Test fun anAddAndARemoveMakeAtTheSameTimeMerge() = runBlocking {
        val a = device("a")
        val b = device("b")
        a.repo.add("a", false, 5, GENERAL_ID)
        assertConverged()
        a.repo.remove(holdingId(a, "a", GENERAL_ID), 2)
        b.repo.add("a", false, 1, GENERAL_ID)
        assertConverged()
        assertEquals(4, b.db.holdings().totalCopies())
    }

    @Test fun movesAndTempSetsSyncAndDissolveEverywhere() = runBlocking {
        val a = device("a")
        val b = device("b")
        val pool = a.repo.createLocation("Trade", LocationKind.POOL)
        val temp = a.repo.createLocation("Deck", LocationKind.TEMP_SET)
        a.repo.add("a", false, 2, pool)
        a.repo.move(holdingId(a, "a", pool), 2, temp)
        assertConverged()
        val bTemp = b.db.locations().all().first { it.name == "Deck" }
        assertEquals(2, b.db.holdings().inLocation(bTemp.id).single().quantity)

        // B dissolves it; A sees the copies back in the pool, the deck gone.
        b.repo.dissolveTempSet(bTemp.id)
        assertConverged()
        assertEquals(listOf("General", "Trade"), a.db.locations().all().map { it.name }.sorted())
        val aPool = a.db.locations().all().first { it.name == "Trade" }
        assertEquals(2, a.db.holdings().inLocation(aPool.id).single().quantity)
    }

    @Test fun newestRenameWins() = runBlocking {
        val a = device("a")
        val b = device("b")
        val pool = a.repo.createLocation("Trade", LocationKind.POOL)
        assertConverged()
        a.repo.rename(pool, "Alpha")
        b.repo.rename(b.db.locations().all().first { it.name == "Trade" }.id, "Beta") // later clock
        assertConverged()
        assertEquals("Beta", a.db.locations().all().first { it.kind == LocationKind.POOL }.name)
    }

    @Test fun copiesAddedToAPoolWhileAnotherDeviceDeletesItEndUpInGeneral() = runBlocking {
        val a = device("a")
        val b = device("b")
        val pool = a.repo.createLocation("Trade", LocationKind.POOL)
        assertConverged()
        val bPool = b.db.locations().all().first { it.name == "Trade" }.id
        a.repo.deleteLocation(pool)
        b.repo.add("c", false, 4, bPool) // at the same time, B doesn't know yet
        assertConverged()
        assertEquals(4, a.db.holdings().inLocation(GENERAL_ID).single().quantity)
        assertEquals(4, b.db.holdings().inLocation(GENERAL_ID).single().quantity)
        assertTrue(a.db.locations().all().none { it.kind == LocationKind.POOL })
    }

    @Test fun totalsSurviveEveryOrderOfArrival() = runBlocking {
        // A big mixed history on one device, replayed on others in shuffled batches.
        val src = device("src")
        val pools = (1..3).map { src.repo.createLocation("P$it", LocationKind.POOL) }
        val temp = src.repo.createLocation("Deck", LocationKind.TEMP_SET)
        val rnd = Random(7)
        repeat(60) {
            val card = listOf("a", "b", "c")[rnd.nextInt(3)]
            val loc = if (rnd.nextBoolean()) GENERAL_ID else pools[rnd.nextInt(3)]
            when (rnd.nextInt(4)) {
                0, 1 -> src.repo.add(card, rnd.nextBoolean(), 1 + rnd.nextInt(3), loc)
                2 -> src.db.holdings().inLocation(loc).randomOrNull(rnd.asKotlinRandom())?.let { src.repo.move(it.id, 1, temp) }
                else -> src.db.holdings().inLocation(loc).randomOrNull(rnd.asKotlinRandom())?.let { src.repo.remove(it.id, 1) }
            }
        }
        src.repo.deleteLocation(pools[1])
        val expected = view(src)
        val all = src.db.syncOps().all()
        for (seed in 1..5) {
            val d = device("replay$seed")
            val shuffled = all.groupBy { it.origin }.values.flatten().shuffled(Random(seed.toLong()))
            // Offer them in random batches, repeatedly: only in-order operations are taken each round.
            var guard = 0
            while (d.sync.vector()["src"] != all.maxOf { it.seq } && guard++ < 500) {
                d.sync.receive(shuffled.shuffled(Random(guard.toLong())).take(25))
            }
            assertEquals("replay $seed", expected, view(d))
        }
    }

    @Test fun anOperationForAPlaceNotHereYetWaitsAndThenApplies() = runBlocking {
        val a = device("a")
        val b = device("b")
        val c = device("c")
        val pool = a.repo.createLocation("Trade", LocationKind.POOL)
        pull(a, b)
        b.repo.add("a", false, 2, b.db.locations().all().first { it.name == "Trade" }.id)
        // C hears from B first, before it has A's create.
        val fromB = b.sync.missingFor(emptyMap()).filter { it.origin == "b" }
        c.sync.receive(fromB)
        assertEquals(0, c.db.holdings().totalCopies())
        assertEquals(1, c.db.syncOps().pending().size)
        pull(a, c)
        assertEquals(2, c.db.holdings().totalCopies())
        assertTrue(c.db.syncOps().pending().isEmpty())
        assertConverged()
    }

    @Test fun existingCollectionIsBaselinedOnceWithoutChangingIt() = runBlocking {
        val a = device("a")
        // Make state without ops, as an older version of the app would have.
        val pool = a.db.locations().insert(Location(name = "Old pool", kind = LocationKind.POOL, createdAt = 5))
        a.db.holdings().insert(Holding(cardId = "a", locationId = pool, foil = true, quantity = 3))
        a.sync.baseline()
        a.sync.baseline() // second call does nothing
        assertEquals(2, a.db.syncOps().count())
        assertEquals(3, a.db.holdings().totalCopies())
        val b = device("b")
        assertConverged()
        assertEquals(3, b.db.holdings().totalCopies())
        assertEquals(listOf("General", "Old pool"), b.db.locations().all().map { it.name }.sorted())
    }

    @Test fun opsFromTheSameDeviceMustArriveInOrder() = runBlocking {
        val a = device("a")
        val b = device("b")
        repeat(3) { a.repo.add("a", false, 1, GENERAL_ID) }
        val ops = a.db.syncOps().all()
        assertEquals(0, b.sync.receive(listOf(ops[1], ops[2]))) // seq 2 and 3 without 1
        assertEquals(3, b.sync.receive(ops))
        assertEquals(0, b.sync.receive(ops)) // again: nothing new
        assertEquals(3, b.db.holdings().totalCopies())
    }

    private fun Random.asKotlinRandom() = kotlin.random.Random(nextLong())
}
