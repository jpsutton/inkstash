package io.github.jpsutton.inkstash

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InkstashApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // First launch: load the bundled card snapshot so the app works offline.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.cards.importBundledIfEmpty(this@InkstashApp) }
        }
    }
}
