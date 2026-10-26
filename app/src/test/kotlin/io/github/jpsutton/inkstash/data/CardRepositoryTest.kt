package io.github.jpsutton.inkstash.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRepositoryTest : DbTestBase() {
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("t", Context.MODE_PRIVATE)
    private fun cards() = CardRepository(db, prefs, OkHttpClient())

    // Shape of a real Lorcast /sets/:code/cards answer (trimmed), plus a dual-ink card without images.
    private val json = """
      [{"id":"crd_1","name":"Ariel","version":"On Human Legs","layout":"normal","image_uris":{"digital":{"small":"https://x/s.avif","normal":"https://x/n.avif","large":"https://x/l.avif"}},
        "cost":4,"inkwell":true,"ink":"Amber","inks":null,"type":["Character"],"rarity":"Uncommon","collector_number":"1","lang":"en",
        "set":{"id":"set_1","code":"1","name":"The First Chapter"},"prices":{"usd":"0.12"},"something_new":true},
       {"id":"crd_2","name":"Be Our Guest","version":null,"image_uris":null,"cost":2,"inkwell":false,"ink":null,"inks":["Ruby","Steel"],
        "type":["Action","Song"],"rarity":"Super_rare","collector_number":"205","set":{"id":"set_1","code":"1","name":"The First Chapter"}}]
    """.trimIndent()

    @Test fun importParsesLorcastJson() = runBlocking {
        assertEquals(2, cards().importJson(json))
        val ariel = db.cards().byId("crd_1")!!
        assertEquals("Amber", ariel.ink)
        assertEquals("Character", ariel.type)
        assertEquals("https://x/n.avif", ariel.imageNormal)
        assertTrue(ariel.inkable)
        val song = db.cards().byId("crd_2")!!
        assertEquals("Ruby", song.ink)
        assertEquals("Action Song", song.type)
        assertEquals("Super rare", song.rarity)
        assertEquals("", song.version)
        assertNull(song.imageSmall)
        // Prices: present as strings, or absent.
        assertEquals(0.12, ariel.priceUsd!!, 1e-9)
        assertNull(ariel.priceFoilUsd)
        assertNull(song.priceUsd)
    }

    @Test fun foilOnlyAndBadPricesParse() = runBlocking {
        val r = cards()
        r.importJson(
            """[{"id":"e","name":"Elsa","version":"Enchanted","cost":8,"inkwell":true,"ink":"Amethyst","type":["Character"],
              "rarity":"Enchanted","collector_number":"207","set":{"id":"s","code":"1","name":"S"},"prices":{"usd":null,"usd_foil":"115.39"}},
             {"id":"f","name":"Fish","cost":1,"inkwell":false,"ink":"Amber","type":["Character"],"rarity":"Common","collector_number":"1",
              "set":{"id":"s","code":"1","name":"S"},"prices":{"usd":"n/a","usd_foil":""}}]""".trimIndent(),
        )
        val e = db.cards().byId("e")!!
        assertNull(e.priceUsd)
        assertEquals(115.39, e.priceFoilUsd!!, 1e-9)
        val f = db.cards().byId("f")!!
        assertNull(f.priceUsd)
        assertNull(f.priceFoilUsd)
    }

    @Test fun importIsAnUpsertSoOwnedCardsSurvive() = runBlocking {
        val r = cards()
        r.importJson(json)
        repo.add("crd_1", false, 3, GENERAL_ID)
        r.importJson(json.replace("On Human Legs", "On Human Legs (updated)"))
        assertEquals("On Human Legs (updated)", db.cards().byId("crd_1")!!.version)
        assertEquals(3, qty("crd_1", GENERAL_ID))
    }

    @Test fun lookupBySetAndNumber() = runBlocking {
        cards().importJson(json)
        assertEquals("crd_2", db.cards().bySetAndNumber("1", "205")!!.id)
        assertNull(db.cards().bySetAndNumber("2", "205"))
    }

    @Test fun searchMatchesNameAndVersion() = runBlocking {
        val r = cards()
        r.importJson(json)
        assertEquals(listOf("crd_1"), r.search("ariel").map { it.id })
        assertEquals(listOf("crd_1"), r.search("human").map { it.id })
        assertTrue(r.search("   ").isEmpty())
    }

    @Test fun bundledImportRunsOnceAndThenLeavesTheDatabaseAlone() = runBlocking {
        val r = cards()
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Whether the snapshot asset exists depends on the checkout (it is gitignored). With it, the
        // table is not empty here (the base class seeds 3 cards) but has no prices flag yet: it imports
        // once. Without it, nothing is imported, ever.
        val first = r.importBundled(context)
        assertTrue(first == 0 || first > 3)
        assertEquals(0, r.importBundled(context).let { if (first == 0) 0 else it })
    }
}
