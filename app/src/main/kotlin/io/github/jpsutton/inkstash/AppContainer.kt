package io.github.jpsutton.inkstash

import android.content.Context
import io.github.jpsutton.inkstash.data.CardRepository
import io.github.jpsutton.inkstash.data.CollectionRepository
import io.github.jpsutton.inkstash.data.SyncStore
import io.github.jpsutton.inkstash.data.InkstashDatabase
import io.github.jpsutton.inkstash.scan.CardMatcher
import io.github.jpsutton.inkstash.scan.NameAnchorLocator
import io.github.jpsutton.inkstash.scan.OcrEngine
import io.github.jpsutton.inkstash.scan.ScanPipeline
import io.github.jpsutton.inkstash.scan.ScanRow
import io.github.jpsutton.inkstash.scan.TiledTextReader
import io.github.jpsutton.inkstash.ui.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/** Manual DI: one instance per process, created by [InkstashApp]. */
class AppContainer(val context: Context) {
    private val prefs = context.getSharedPreferences("inkstash", Context.MODE_PRIVATE)
    private val http = OkHttpClient()

    val db: InkstashDatabase = InkstashDatabase.create(context)
    val cards = CardRepository(db, prefs, http)
    /** This install's id in the operation log (and in a mesh). Random, made once. */
    val nodeId: String = prefs.getString(KEY_NODE_ID, null) ?: java.util.UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_NODE_ID, it).apply()
    }
    val sync = SyncStore(db, nodeId)
    val collection = CollectionRepository(db, sync)
    val ocr by lazy { OcrEngine() }

    /** Photo scan result waiting for the review screen. */
    val scanSession = MutableStateFlow<ScanSession?>(null)

    private val _theme = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<ThemeMode> = _theme

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    suspend fun newScanPipeline(): ScanPipeline {
        val cards = db.cards().all()
        return ScanPipeline(TiledTextReader(ocr), CardMatcher(cards), NameAnchorLocator(cards.map { it.name }))
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_NODE_ID = "node_id"
    }
}

/** The photo that was scanned and the cards found in it. */
class ScanSession(val photo: android.graphics.Bitmap, val rows: List<ScanRow>)
