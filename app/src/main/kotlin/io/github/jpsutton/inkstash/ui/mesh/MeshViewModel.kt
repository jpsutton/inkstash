package io.github.jpsutton.inkstash.ui.mesh

import androidx.lifecycle.ViewModel
import io.github.jpsutton.inkstash.mesh.MeshInvite
import io.github.jpsutton.inkstash.mesh.MeshNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Talks to the [MeshNode]. Long actions (joining waits for a person to approve) run in the app's scope, so
 * leaving the screen doesn't cancel them.
 */
class MeshViewModel(private val mesh: MeshNode, private val scope: CoroutineScope) : ViewModel() {
    val state = mesh.state

    private val _invite = MutableStateFlow<MeshInvite?>(null)

    /** The QR invite on screen, if any. */
    val invite: StateFlow<MeshInvite?> = _invite

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun startMesh(passphrase: String) {
        scope.launch {
            try {
                mesh.startMesh(passphrase)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun join(address: String, passphrase: String) {
        scope.launch { mesh.join(address.trim(), passphrase) }
    }

    fun join(invite: MeshInvite) {
        scope.launch { mesh.join("", "", invite) }
    }

    fun approve(nodeId: String) = mesh.approve(nodeId)
    fun decline(nodeId: String) = mesh.decline(nodeId)
    fun remove(nodeId: String) { scope.launch { mesh.remove(nodeId) } }
    fun leave() { scope.launch { mesh.leave() } }
    fun rename(name: String) = mesh.rename(name)
    fun addAddress(a: String) = mesh.addAddress(a)
    fun removeAddress(a: String) = mesh.removeAddress(a)
    fun dismissNotice() = mesh.dismissNotice()
    fun clearError() { _error.value = null }

    fun showInvite() {
        _invite.value = mesh.createInvite()
        if (_invite.value == null) _error.value = "This device has no network address to share. Connect to Wi-Fi first."
    }

    fun hideInvite() { _invite.value = null }
}
