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
    fun scanUri(resolver: ContentResolver, uri: Uri, mode: ScanMode, onDone: () -> Unit) {
        if (_state.value.busy) return
        _state.value = ScanUiState(busy = true)
        viewModelScope.launch {
            try {
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
            }
        }
    }

    fun fail(message: String) { _state.value = ScanUiState(error = message) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
