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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsutton.inkstash.ui.Muted
import kotlinx.coroutines.launch
import java.io.File

private val Night = Color(0xFF0E0D1F)
private val Plum = Color(0xFF2E2A6B)
private val Amber = Color(0xFFF2B33D)
private val Scrim = Color(0xC70E0D1F)

@Composable
fun ScanScreen(vm: ScanViewModel, onClose: () -> Unit, onScanned: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val s by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> uri?.let { vm.scanUri(ctx.contentResolver, it, onScanned) } }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var cols by remember { mutableStateOf(3) }
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
                scope.launch { vm.scanUri(ctx.contentResolver, Uri.fromFile(file), onScanned); file.delete() }
            }
            override fun onError(e: ImageCaptureException) { vm.fail("Couldn't take the photo. Try again.") }
        })
    }

    Row(Modifier.fillMaxSize().background(Night)) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF35303F)), Alignment.Center) {
            if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The camera needs your OK", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Muted("Tap the camera permission button, or pick a photo instead.", size = 16)
            }
            GuideFrame(cols)
            RoundOverlay(Modifier.align(Alignment.TopStart).padding(24.dp).size(56.dp).clickable(onClick = onClose).semantics { contentDescription = "Close camera" }, CircleShape) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
            Pill(if (cols == 3) "Lay up to 9 cards in a grid and fill the frame" else "12 cards is tricky. Get close and use good light", 18, Modifier.align(Alignment.TopCenter).padding(top = 28.dp))
            Row(Modifier.align(Alignment.TopEnd).padding(24.dp).clip(RoundedCornerShape(28.dp)).background(Scrim).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (n in listOf(3, 4)) Text(
                    "${n * 3} cards", color = if (n == cols) Color(0xFF1B1A3A) else Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (n == cols) Amber else Color.Transparent)
                        .clickable { cols = n }.padding(horizontal = 18.dp).wrapContentCenter(),
                )
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp).clip(RoundedCornerShape(24.dp)).background(Scrim).height(48.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(40.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Amber))
                Text("Keep the tiny numbers at the bottom of each card sharp and well lit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

/** Corner brackets and dashed card slots that show how to lay cards out. */
@Composable
private fun GuideFrame(cols: Int) {
    val frameW = (cols * 140 + (cols - 1) * 16 + 48).dp
    Box(Modifier.width(frameW).height(668.dp)) {
        val corner = Modifier.size(44.dp)
        for (a in listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)) {
            Box(Modifier.align(a).then(corner).border(
                width = 5.dp, color = Color.White,
                shape = when (a) {
                    Alignment.TopStart -> RoundedCornerShape(topStart = 14.dp)
                    Alignment.TopEnd -> RoundedCornerShape(topEnd = 14.dp)
                    Alignment.BottomStart -> RoundedCornerShape(bottomStart = 14.dp)
                    else -> RoundedCornerShape(bottomEnd = 14.dp)
                },
            ))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(cols) {
                    Box(Modifier.size(140.dp, 196.dp).clip(RoundedCornerShape(12.dp)).border(3.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp)), Alignment.BottomStart) {
                        Box(Modifier.padding(10.dp).height(12.dp).width(87.dp).clip(RoundedCornerShape(4.dp)).background(Amber.copy(alpha = 0.75f)))
                    }
                }
            }
        }
    }
}
