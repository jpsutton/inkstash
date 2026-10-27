package io.github.jpsutton.inkstash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.jpsutton.inkstash.mesh.LocalNetworkPermission
import io.github.jpsutton.inkstash.ui.InkstashRoot
import io.github.jpsutton.inkstash.ui.InkstashTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as InkstashApp).container
        // A device that is already in a mesh needs this to listen and dial again; the sync screen asks for everyone else.
        val localNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) container.appScope.launch { runCatching { container.mesh.start() } }
        }
        if (savedInstanceState == null && container.meshIdentity.inMesh && !LocalNetworkPermission.granted(this)) {
            localNetwork.launch(LocalNetworkPermission.NAME)
        }
        setContent {
            val mode by container.theme.collectAsState()
            InkstashTheme(mode) { InkstashRoot(container) }
        }
    }
}
