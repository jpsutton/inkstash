package io.github.jpsutton.inkstash.ui.scan

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jpsutton.inkstash.AppContainer
import io.github.jpsutton.inkstash.ScanSession
import io.github.jpsutton.inkstash.scan.PhotoLoader
import io.github.jpsutton.inkstash.scan.ScanMode
import io.github.jpsutton.inkstash.scan.ScanLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanUiState(val busy: Boolean = false, val error: String? = null)

class ScanViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state

    /** Reads the photo at [uri], finds the cards in it and stores the result for the review screen. */
    fun scanUri(resolver: ContentResolver, uri: Uri, mode: ScanMode, onDone: () -> Unit, deleteWhenDone: java.io.File? = null) {
        if (_state.value.busy) { deleteWhenDone?.delete(); return }
        _state.value = ScanUiState(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { keepLastPhoto(resolver, uri) }
                val photo = withContext(Dispatchers.IO) { PhotoLoader.load(resolver, uri) }
                val rows = app.newScanPipeline().scan(photo, mode)
                if (rows.isEmpty()) {
                    _state.value = ScanUiState(error = "No cards found. Get closer, use more light, and keep the tiny numbers sharp. Or try the 1 card option.")
                } else {
                    app.scanSession.value = ScanSession(photo, rows)
                    _state.value = ScanUiState()
                    onDone()
                }
            } catch (e: Exception) {
                ScanLog.i("scan failed: $e")
                _state.value = ScanUiState(error = "Couldn't read that photo. ${e.message.orEmpty()}".trim())
            } finally {
                // The temp file of a camera shot: the scan runs on its own coroutine, so only now is it unused.
                deleteWhenDone?.delete()
            }
        }
    }

    /**
     * Copies the photo being scanned to `cache/last-scan.jpg`, overwriting the one before, so a scan that
     * went wrong can be looked at (debug builds: `adb exec-out run-as <package> cat cache/last-scan.jpg`).
     */
    private fun keepLastPhoto(resolver: ContentResolver, uri: Uri) {
        runCatching {
            resolver.openInputStream(uri)!!.use { input ->
                java.io.File(app.context.cacheDir, LAST_PHOTO).outputStream().use { input.copyTo(it) }
            }
        }
    }

    fun fail(message: String) { _state.value = ScanUiState(error = message) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    companion object {
        const val LAST_PHOTO = "last-scan.jpg"
    }
}
