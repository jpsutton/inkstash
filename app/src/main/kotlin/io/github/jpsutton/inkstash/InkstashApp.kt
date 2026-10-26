package io.github.jpsutton.inkstash

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import io.github.jpsutton.inkstash.ui.AvifCoilDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InkstashApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // First launch: load the bundled card snapshot so the app works offline.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.cards.importBundled(this@InkstashApp) }
        }
    }

    /** Card art is AVIF; see [AvifCoilDecoder]. */
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context).components { add(AvifCoilDecoder.Factory()) }.build()
}
