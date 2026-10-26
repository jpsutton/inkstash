package io.github.jpsutton.inkstash.ui.mesh

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.jpsutton.inkstash.mesh.MeshInvite
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val Night = Color(0xFF0E0D1F)
private val Scrim = Color(0xC70E0D1F)
private val Amber = Color(0xFFF2B33D)

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
/** Looks for the QR code another device shows ("Show a code to scan") and hands back the invite in it. */
@Composable
fun QrScanScreen(onInvite: (MeshInvite) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var message by remember { mutableStateOf("Point the camera at the code on the other device") }
    val done = remember { AtomicBoolean(false) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }

    DisposableEffect(granted) {
        var provider: ProcessCameraProvider? = null
        if (granted) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val image = proxy.image
                    if (image == null || done.get()) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes ->
                            for (code in codes) {
                                val text = code.rawValue ?: continue
                                val invite = MeshInvite.decode(text)
                                if (invite == null) {
                                    message = "That code isn't from inkstash"
                                } else if (invite.expiresAt < System.currentTimeMillis()) {
                                    message = "That code has run out. Show a new one."
                                } else if (done.compareAndSet(false, true)) {
                                    onInvite(invite)
                                }
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { provider?.unbindAll() }
    }
    DisposableEffect(Unit) { onDispose { scanner.close(); executor.shutdown() } }

    Box(Modifier.fillMaxSize().background(Night).safeDrawingPadding()) {
        if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
        else Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The camera needs your OK", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.align(Alignment.Center).size(320.dp).clip(RoundedCornerShape(24.dp)).border(5.dp, Amber, RoundedCornerShape(24.dp)))
        Box(
            Modifier.align(Alignment.TopStart).padding(24.dp).size(56.dp).clip(CircleShape).background(Scrim).clickable(onClick = onClose)
                .semantics { contentDescription = "Close camera" },
            Alignment.Center,
        ) { Icon(Icons.Outlined.Close, null, tint = Color.White) }
        Text(
            message, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).clip(RoundedCornerShape(24.dp)).background(Scrim).padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}
