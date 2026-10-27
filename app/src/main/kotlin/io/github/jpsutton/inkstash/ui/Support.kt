package io.github.jpsutton.inkstash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import io.github.jpsutton.inkstash.data.Card

/** `viewModel { ... }` that builds the ViewModel from a lambda. */
@Composable
inline fun <reified VM : ViewModel> vm(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    })

enum class ArtSize { Small, Normal, Large }

/**
 * Card art from the Lorcast CDN (Coil caches it), or an ink-tinted placeholder while it loads or when it can't.
 * The real image already prints the cost, so [costGem] (the gem's size) is drawn on the placeholder only.
 */
@Composable
fun CardArt(card: Card, modifier: Modifier = Modifier, size: ArtSize = ArtSize.Normal, costGem: Dp? = null) {
    val ink = InkColor.of(card.ink)
    val url = when (size) {
        ArtSize.Small -> card.imageSmall ?: card.imageNormal
        ArtSize.Normal -> card.imageNormal ?: card.imageSmall
        ArtSize.Large -> card.imageLarge ?: card.imageNormal
    }
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(ink.color.copy(alpha = 0.16f)), Alignment.Center) {
            Icon(Icons.Outlined.Image, null, tint = ink.color, modifier = Modifier.alpha(0.7f).size(52.dp))
            if (costGem != null) CostGem(card.cost, ink, costGem, Modifier.align(Alignment.TopStart).padding(costGem / 4))
        }
    }
    Box(modifier) {
        if (url == null) placeholder() else SubcomposeAsyncImage(
            model = url,
            contentDescription = card.fullName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { placeholder() },
            error = { placeholder() },
        )
    }
}
