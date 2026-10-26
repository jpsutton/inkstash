package io.github.jpsutton.inkstash

import android.content.Context
import io.github.jpsutton.inkstash.data.CardRepository
import io.github.jpsutton.inkstash.data.CollectionRepository
import io.github.jpsutton.inkstash.data.InkstashDatabase
import io.github.jpsutton.inkstash.scan.CardMatcher
import io.github.jpsutton.inkstash.scan.OcrEngine
import io.github.jpsutton.inkstash.scan.ScanPipeline
import io.github.jpsutton.inkstash.scan.ScanRow
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
    val collection = CollectionRepository(db)
    val ocr by lazy { OcrEngine() }

    /** Photo scan result waiting for the review screen. */
    val scanSession = MutableStateFlow<ScanSession?>(null)

    private val _theme = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<ThemeMode> = _theme

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    suspend fun newScanPipeline(): ScanPipeline = ScanPipeline(ocr, CardMatcher(db.cards().all()))

    companion object {
        private const val KEY_THEME = "theme"
    }
}

/** The photo that was scanned and the cards found in it. */
class ScanSession(val photo: android.graphics.Bitmap, val rows: List<ScanRow>)
