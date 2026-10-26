package io.github.jpsutton.inkstash.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jpsutton.inkstash.data.Backup
import io.github.jpsutton.inkstash.data.CardRepository
import io.github.jpsutton.inkstash.data.CollectionRepository
import io.github.jpsutton.inkstash.data.SyncProgress
import io.github.jpsutton.inkstash.ui.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val cardCount: Int = 0,
    val setCount: Int = 0,
    val lastSync: Long = 0,
    val progress: SyncProgress? = null,
    val updating: Boolean = false,
    /** One-line result of the last update / backup action, shown under the buttons. */
    val message: String? = null,
)

class SettingsViewModel(
    private val cards: CardRepository,
    private val collection: CollectionRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsState())
    private var job: Job? = null

    val state: StateFlow<SettingsState> = combine(local, cards.cardCount, cards.setCount) { l, n, sets ->
        l.copy(cardCount = n, setCount = sets, lastSync = cards.lastSyncMillis)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun toggleUpdate() {
        if (job?.isActive == true) { job?.cancel(); return }
        local.value = local.value.copy(updating = true, progress = null, message = null)
        job = viewModelScope.launch {
            try {
                cards.sync { p -> local.value = local.value.copy(progress = p) }
                local.value = local.value.copy(updating = false, progress = null, message = "Card database is up to date.")
            } catch (e: CancellationException) {
                local.value = local.value.copy(updating = false, progress = null, message = "Update stopped.")
                throw e
            } catch (e: Exception) {
                local.value = local.value.copy(updating = false, progress = null, message = "Couldn't update: ${e.message ?: "no connection"}. Your cards are safe.")
            }
        }
    }

    fun saveBackup(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        local.value = local.value.copy(message = try {
            val data = collection.snapshot()
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")!!.use { it.write(Backup.encode(data, now()).toByteArray()) }
            }
            "Backup saved."
        } catch (e: Exception) { "Couldn't save the backup: ${e.message}" })
    }

    fun loadBackup(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        local.value = local.value.copy(message = try {
            val text = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
            val skipped = collection.restore(Backup.decode(text))
            if (skipped > 0) "Backup loaded. $skipped cards were skipped because the card database doesn't know them. Update it and load again."
            else "Backup loaded."
        } catch (e: Exception) { e.message ?: "Couldn't load the backup." })
    }
}

fun interface ThemeSetter { fun set(mode: ThemeMode) }
