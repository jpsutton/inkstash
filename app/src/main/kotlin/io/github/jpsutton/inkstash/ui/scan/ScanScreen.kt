package io.github.jpsutton.inkstash.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsutton.inkstash.scan.ScanMode
import io.github.jpsutton.inkstash.ui.Fredoka
import io.github.jpsutton.inkstash.ui.Muted
import io.github.jpsutton.inkstash.ui.isCompact
import java.io.File

private val Night = Color(0xFF0E0D1F)
private val Plum = Color(0xFF2E2A6B)
private val Amber = Color(0xFFF2B33D)
private val Scrim = Color(0xC70E0D1F)
private val Viewfinder = Color(0xFF35303F)

@Composable
fun ScanScreen(vm: ScanViewModel, onClose: () -> Unit, onScanned: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val s by vm.state.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var mode by remember { mutableStateOf(GuideMode.ONE) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> uri?.let { vm.scanUri(ctx.contentResolver, it, mode.scan, onScanned) } }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var flash by remember { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    DisposableEffect(granted) {
        var provider: ProcessCameraProvider? = null
        if (granted) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                p.unbindAll()
                camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { provider?.unbindAll(); camera = null }
    }
    LaunchedEffect(flash, camera) { camera?.cameraControl?.enableTorch(flash) }

    fun snap() {
        val file = File.createTempFile("scan", ".jpg", ctx.cacheDir)
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                vm.scanUri(ctx.contentResolver, Uri.fromFile(file), mode.scan, onScanned, deleteWhenDone = file)
            }
            override fun onError(e: ImageCaptureException) { vm.fail("Couldn't take the photo. Try again.") }
        })
    }

    if (isCompact()) {
        ScanPhone(
            granted = granted, previewView = previewView, busy = s.busy, error = s.error,
            mode = mode, onMode = { mode = it }, flash = flash, onFlash = { flash = !flash },
            onClose = onClose, onSnap = ::snap, onClearError = vm::clearError,
            onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        return
    }
    Row(Modifier.fillMaxSize().background(Night).safeDrawingPadding()) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Viewfinder), Alignment.Center) {
            if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The camera needs your OK", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Muted("Tap the camera permission button, or pick a photo instead.", size = 16)
            }
            GuideFrame(mode)
            RoundOverlay(Modifier.align(Alignment.TopStart).padding(24.dp).size(56.dp).clickable(onClick = onClose).semantics { contentDescription = "Close camera" }, CircleShape) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
            mode.hint?.let { Pill(it, 18, Modifier.align(Alignment.TopCenter).padding(top = 28.dp)) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Sit just left of the card guide, but never off the screen edge.
                val start = maxOf(8.dp, (maxWidth - guideWidth(mode)) / 2 - ModePillsWidth - 16.dp)
                Column(
                    Modifier.align(Alignment.CenterStart).padding(start = start).width(ModePillsWidth).clip(RoundedCornerShape(28.dp)).background(Scrim).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (m in GuideMode.entries) Text(
                        m.label, color = if (m == mode) Color(0xFF1B1A3A) else Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (m == mode) Amber else Color.Transparent)
                            .clickable { mode = m }.wrapContentCenter(),
                    )
                }
            }
            if (s.busy) Box(Modifier.fillMaxSize().background(Scrim), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(color = Amber)
                    Text("Reading your cards…", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            s.error?.let { msg ->
                Pill(msg, 16, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp).clickable { vm.clearError() })
            }
        }
        Column(Modifier.width(168.dp).fillMaxHeight().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Column(
                Modifier.size(64.dp).clip(CircleShape).background(if (flash) Amber else Plum).clickable { flash = !flash }.semantics { contentDescription = if (flash) "Light on" else "Light off" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(if (flash) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, null, tint = if (flash) Color(0xFF1B1A3A) else Color.White)
                Text(if (flash) "On" else "Off", color = if (flash) Color(0xFF1B1A3A) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(104.dp).border(6.dp, Color.White, CircleShape).clip(CircleShape)
                        .clickable(enabled = granted && !s.busy) { snap() }.semantics { contentDescription = "Take photo" },
                    Alignment.Center,
                ) { Box(Modifier.size(78.dp).background(Amber, CircleShape)) }
                Text("Snap", color = Color(0xFFC9C6F0), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.clickable(enabled = !s.busy) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Plum), Alignment.Center) { Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White) }
                Text("Pick photo", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun Modifier.wrapContentCenter(): Modifier = this.then(Modifier.wrapContentSize(Alignment.Center))

@Composable
private fun Pill(text: String, size: Int, modifier: Modifier = Modifier) {
    Text(text, color = Color.White, fontSize = size.sp, fontWeight = FontWeight.ExtraBold,
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(Scrim).padding(horizontal = 22.dp).wrapContentCenter())
}

@Composable
private fun RoundOverlay(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape, content: @Composable () -> Unit) {
    Box(modifier.clip(shape).background(Scrim), Alignment.Center) { content() }
}

/** What the guide shows: one big card, or a 3x3 / 4x3 grid of slots. */
private enum class GuideMode(val label: String, val cols: Int, val rows: Int, val scan: ScanMode, val hint: String?) {
    ONE("1 card", 1, 1, ScanMode.SINGLE, null),
    NINE("9 cards", 3, 3, ScanMode.GRID, "Lay up to 9 cards in a grid and fill the frame"),
    TWELVE("12 cards", 4, 3, ScanMode.GRID, "12 cards is tricky. Get close and use good light"),
}

private val ModePillsWidth = 120.dp
private val GuideGap = 16.dp

private fun guideSlotWidth(mode: GuideMode) = if (mode == GuideMode.ONE) 440.dp else 140.dp

/** Width of the card slots in the guide, so other overlays can sit beside it. */
private fun guideWidth(mode: GuideMode) = guideSlotWidth(mode) * mode.cols + GuideGap * (mode.cols - 1)

/** Card slots that show how to lay cards out. */
@Composable
private fun GuideFrame(mode: GuideMode) {
    val single = mode == GuideMode.ONE
    val slotW = guideSlotWidth(mode)
    val slotH = if (single) 616.dp else 196.dp
    Column(verticalArrangement = Arrangement.spacedBy(GuideGap)) {
        repeat(mode.rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(GuideGap)) {
                repeat(mode.cols) {
                    Box(Modifier.size(slotW, slotH).clip(RoundedCornerShape(12.dp)).border(3.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp)))
                }
            }
        }
    }
}

/**
 * The phone layout: close button and card-count switch on top, the viewfinder with the card guide
 * in the middle, and the camera controls underneath.
 */
@Composable
private fun ScanPhone(
    granted: Boolean, previewView: PreviewView, busy: Boolean, error: String?,
    mode: GuideMode, onMode: (GuideMode) -> Unit, flash: Boolean, onFlash: () -> Unit,
    onClose: () -> Unit, onSnap: () -> Unit, onPick: () -> Unit, onClearError: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Night).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Plum).clickable(onClick = onClose).semantics { contentDescription = "Close camera" },
                Alignment.Center,
            ) { Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            Row(Modifier.clip(RoundedCornerShape(26.dp)).background(Plum).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (m in GuideMode.entries) {
                    val on = m == mode
                    Text(
                        m.label, color = if (on) Color(0xFF1B1A3A) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        modifier = Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (on) Amber else Color.Transparent)
                            .clickable { onMode(m) }.padding(horizontal = 14.dp).wrapContentCenter(),
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Viewfinder), Alignment.Center) {
            if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
            else Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The camera needs your OK", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Muted("Allow the camera in your settings, or pick a photo instead.", size = 15)
            }
            GuideGridPhone(mode)
            mode.hint?.let { hint ->
                Text(
                    hint, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopCenter).padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Scrim).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            if (busy) Box(Modifier.fillMaxSize().background(Scrim), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(color = Amber, trackColor = Plum, strokeWidth = 6.dp, modifier = Modifier.size(56.dp))
                    Text("Reading your cards…", color = Color.White, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                }
            }
            if (error != null) Text(
                error, color = Color(0xFFF4A3A3), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF3D1D20)).clickable(onClick = onClearError).padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Row(Modifier.fillMaxWidth().height(168.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clickable(enabled = !busy, onClick = onPick),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Plum), Alignment.Center) { Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                Text("Pick photo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(92.dp).border(6.dp, Color.White, CircleShape).clip(CircleShape)
                        .clickable(enabled = granted && !busy, onClick = onSnap).semantics { contentDescription = "Take photo" },
                    Alignment.Center,
                ) { Box(Modifier.size(68.dp).background(Amber, CircleShape)) }
                Text("Snap", color = Color(0xFFC9C6F0), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(
                Modifier.weight(1f).clickable(onClick = onFlash).semantics { contentDescription = if (flash) "Flash on" else "Flash off" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(if (flash) Amber else Plum), Alignment.Center) {
                    Icon(if (flash) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, null, tint = if (flash) Color(0xFF1B1A3A) else Color.White, modifier = Modifier.size(24.dp))
                }
                Text(if (flash) "Flash on" else "Flash off", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/**
 * Card slots that show how to lay cards out: one big slot, or three columns of three or four rows, shrunk
 * to fit short screens. A card is 5:7.
 */
@Composable
private fun GuideGridPhone(mode: GuideMode) {
    val single = mode == GuideMode.ONE
    val cols = if (single) 1 else 3
    val rows = when (mode) { GuideMode.ONE -> 1; GuideMode.NINE -> 3; GuideMode.TWELVE -> 4 }
    val top = if (mode.hint != null) 62.dp else 14.dp // room for the hint, when there is one
    BoxWithConstraints(Modifier.fillMaxSize(), Alignment.Center) {
        val gap = 10.dp
        val availH = (maxHeight - top - 14.dp - gap * (rows - 1)) / rows
        val availW = (maxWidth - 28.dp - gap * (cols - 1)) / cols
        var width = minOf(availW, availH * 5f / 7f)
        if (!single) width = minOf(width, 129.dp * 5f / 7f)
        val slotHeight = width * 7f / 5f
        Column(Modifier.padding(top = top - 14.dp + if (single) 0.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(cols) {
                        Box(Modifier.size(width, slotHeight).clip(RoundedCornerShape(10.dp)).border(3.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)), Alignment.BottomStart) {
                            Box(Modifier.padding(7.dp).height(8.dp).fillMaxWidth(0.62f).clip(RoundedCornerShape(3.dp)).background(Amber.copy(alpha = 0.75f)))
                        }
                    }
                }
            }
        }
    }
}
