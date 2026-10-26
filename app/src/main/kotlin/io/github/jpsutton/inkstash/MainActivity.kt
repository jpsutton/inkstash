package io.github.jpsutton.inkstash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.jpsutton.inkstash.ui.InkstashRoot
import io.github.jpsutton.inkstash.ui.InkstashTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as InkstashApp).container
        setContent {
            val mode by container.theme.collectAsState()
            InkstashTheme(mode) { InkstashRoot(container) }
        }
    }
}
