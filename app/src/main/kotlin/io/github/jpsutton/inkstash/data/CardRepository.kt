package io.github.jpsutton.inkstash.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Download progress: `done` of `total` sets. */
data class SyncProgress(val done: Int, val total: Int)

/**
 * The known-card DB: imported from the bundled snapshot on first launch, re-synced from Lorcast
 * (https://api.lorcast.com/v0) from Settings.
 */
class CardRepository(
    private val db: InkstashDatabase,
    private val prefs: SharedPreferences,
    private val http: OkHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val baseUrl: String = "https://api.lorcast.com/v0",
    private val politeDelayMs: Long = 100,
) {
    private val cards = db.cards()

    val cardCount = cards.countFlow()
    val setCount = cards.setCountFlow()
    val sets = cards.sets()

    var lastSyncMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0)
        private set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    suspend fun byId(id: String) = cards.byId(id)
    fun observe(id: String) = cards.observe(id)
    suspend fun search(query: String): List<Card> = if (query.isBlank()) emptyList() else cards.search(query.trim())

    /**
     * Loads the bundled snapshot (`assets/cards.json`): into an empty card table, and once into a
     * table made before cards had prices, so installed copies get them without a manual update.
     * Returns the number of cards imported.
     */
    suspend fun importBundled(context: Context): Int = withContext(Dispatchers.IO) {
        val empty = cards.count() == 0
        if (!empty && prefs.getBoolean(KEY_PRICES_SEEDED, false)) return@withContext 0
        val text = try {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        } catch (_: java.io.FileNotFoundException) {
            return@withContext 0
        }
        val n = importJson(text)
        prefs.edit().putBoolean(KEY_PRICES_SEEDED, true).apply()
        n
    }

    suspend fun importJson(text: String): Int {
        val parsed = LorcastJson.parseCards(text).map { it.toCard() }
        upsertChunked(parsed)
        return parsed.size
    }

    /**
     * Re-syncs every set from the Lorcast API, upserting by card id. Rate limited to one request per
     * [politeDelayMs]. Throws on network failure; cards already stored stay.
     */
    suspend fun sync(onProgress: (SyncProgress) -> Unit = {}) = withContext(Dispatchers.IO) {
        val sets = LorcastJson.parseSets(get("$baseUrl/sets"))
            .filter { it.code.length <= 4 } // Skips odd one-off sets, e.g. "Coconut".
        onProgress(SyncProgress(0, sets.size))
        for ((i, set) in sets.withIndex()) {
            delay(politeDelayMs)
            val fetched = LorcastJson.parseCards(get("$baseUrl/sets/${set.code}/cards")).map { it.toCard() }
            upsertChunked(fetched)
            onProgress(SyncProgress(i + 1, sets.size))
        }
        lastSyncMillis = now()
    }

    private suspend fun upsertChunked(list: List<Card>) {
        for (chunk in list.chunked(300)) cards.upsertAll(chunk)
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Lorcast answered ${response.code}")
            return response.body.string()
        }
    }

    companion object {
        const val ASSET_NAME = "cards.json"
        private const val KEY_LAST_SYNC = "cards_last_sync"
        private const val KEY_PRICES_SEEDED = "bundled_prices_seeded"
    }
}

/** Helper so callers can tell a cancelled sync from a failed one. */
fun Throwable.isCancellation(): Boolean = this is CancellationException
