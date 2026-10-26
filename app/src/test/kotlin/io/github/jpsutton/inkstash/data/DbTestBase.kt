package io.github.jpsutton.inkstash.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class DbTestBase {
    protected lateinit var db: InkstashDatabase
    protected lateinit var repo: CollectionRepository

    protected fun card(id: String, name: String = id, set: String = "T", num: String = id.hashCode().toString()) = Card(
        id, set, "Set $set", num, name, "v", "Amber", true, 2, "Character", "Rare", null, null, null,
    )

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InkstashDatabase::class.java)
            .addCallback(InkstashDatabase.seedCallback).allowMainThreadQueries().build()
        repo = CollectionRepository(db, SyncStore(db, "test-node", clock = { 1_000 }), now = { 1_000 })
        kotlinx.coroutines.runBlocking {
            db.cards().upsertAll(listOf(card("a", "Anna", num = "1"), card("b", "Belle", num = "2"), card("c", "Cruella", num = "3")))
        }
    }

    @After fun tearDown() = db.close()

    protected fun total(): Int = kotlinx.coroutines.runBlocking { db.holdings().totalCopies() }
    protected fun qty(cardId: String, location: Long): Int = kotlinx.coroutines.runBlocking {
        db.holdings().inLocation(location).filter { it.cardId == cardId }.sumOf { it.quantity }
    }
}
